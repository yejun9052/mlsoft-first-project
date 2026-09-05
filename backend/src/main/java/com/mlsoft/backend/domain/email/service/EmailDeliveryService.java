package com.mlsoft.backend.domain.email.service;

import com.mlsoft.backend.config.MailAppProperties;
import com.mlsoft.backend.domain.email.entity.EmailHistory;
import com.mlsoft.backend.domain.email.entity.EmailStatus;
import com.mlsoft.backend.domain.email.repository.EmailHistoryRepository;
import com.mlsoft.backend.domain.user.entity.User;
import org.springframework.beans.factory.annotation.Autowired;
import jakarta.mail.internet.MimeMessage;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.PageRequest;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mail.javamail.MimeMessageHelper;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

/**
 * SMTP 발송과 {@code email_history} 상태 전이를 담당한다.
 *
 * <p>모든 예외를 FAILED로 변환하고 밖으로 전파하지 않는다. 메일 장애가 업무 처리를 실패시키면
 * 안 되기 때문이다(검증 R-4).
 */
@Slf4j
@Service
public class EmailDeliveryService {

    public static final int MAX_ATTEMPTS = 3;

    /**
     * 이 시간이 지나도 {@code PENDING}이면 비동기 발송이 실행되지 못한 것으로 보고 스케줄러가 줍는다.
     *
     * <p>너무 짧으면 정상 발송 중인 건을 스케줄러가 중복으로 집는다. 리스너는 보통 수 초 안에
     * 끝나므로 10분이면 안전하다.
     */
    private static final Duration PENDING_STALE_AFTER = Duration.ofMinutes(10);
    /** SENDING은 프로세스 중단으로 남을 수 있으므로 PENDING과 같은 10분 뒤 복구한다. */
    private static final Duration SENDING_STALE_AFTER = Duration.ofMinutes(10);

    private static final int BATCH_SIZE = 100;
    private static final String FROM_NAME = "MLsoft 연차관리";
    private static final String ERROR_MAIL_ACCOUNT_MISSING = "메일 발송 계정이 설정되지 않았습니다.";
    private static final String ERROR_RECIPIENT_EMAIL_MISSING = "수신자 이메일이 설정되지 않았습니다.";
    private static final String ERROR_RECIPIENT_INACTIVE = "퇴직 처리된 수신자라 발송하지 않았습니다.";

    private final EmailHistoryRepository emailHistoryRepository;
    private final MailAppProperties mailAppProperties;
    private final MailSenderResolver mailSenderResolver;

    /** Spring용 생성자 — DB 계정이 있으면 계정별 sender를 선택하고 없으면 env 빈을 사용한다. */
    @Autowired
    public EmailDeliveryService(
            JavaMailSender environmentMailSender,
            EmailHistoryRepository emailHistoryRepository,
            MailAppProperties mailAppProperties,
            MailCredentialService mailCredentialService,
            @org.springframework.beans.factory.annotation.Value("${spring.mail.username:}") String environmentUsername
    ) {
        this.emailHistoryRepository = emailHistoryRepository;
        this.mailAppProperties = mailAppProperties;
        this.mailSenderResolver = new MailSenderResolver(
                environmentMailSender, mailCredentialService, environmentUsername);
    }

    /** 기존 단위 테스트와 환경변수 전용 사용처를 위한 생성자. */
    public EmailDeliveryService(
            JavaMailSender environmentMailSender,
            EmailHistoryRepository emailHistoryRepository,
            MailAppProperties mailAppProperties,
            String environmentUsername
    ) {
        this.emailHistoryRepository = emailHistoryRepository;
        this.mailAppProperties = mailAppProperties;
        this.mailSenderResolver = new MailSenderResolver(
                environmentMailSender, null, environmentUsername);
    }

    /**
     * 이력 한 건을 발송한다 — <b>건마다 독립 트랜잭션</b>이다 (1차 테스트 A).
     *
     * <p>이미 보낸 건(SENT)이나 재시도 상한에 도달한 건은 건너뛴다. 최초 발송과 재시도가
     * 같은 경로를 쓰므로 상태 판정이 한 곳에만 있다.
     */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void send(Long historyId) {
        Optional<EmailHistory> found = emailHistoryRepository.findById(historyId);
        if (found.isEmpty()) {
            return;
        }

        EmailHistory history = found.get();
        if (history.getStatus() == EmailStatus.SENT
                || history.getStatus() == EmailStatus.SENDING
                || history.getRetryCount() >= MAX_ATTEMPTS) {
            return;
        }

        // 조회와 실제 발송 사이에 다른 인스턴스가 먼저 선점할 수 있다. 갱신 건수 1일 때만 진행한다.
        int claimed = emailHistoryRepository.claimForSending(
                historyId,
                EmailStatus.SENDING,
                EmailStatus.PENDING,
                EmailStatus.FAILED,
                MAX_ATTEMPTS);
        if (claimed != 1) {
            return;
        }

        // 조건부 bulk UPDATE가 영속성 컨텍스트를 비웠을 수 있으므로 선점 후 다시 읽는다.
        history = emailHistoryRepository.findById(historyId).orElse(null);
        if (history == null) {
            return;
        }
        // Mockito 기반 단위 테스트처럼 bulk UPDATE가 엔티티 객체에 반영되지 않는 경우에도
        // 갱신 건수 1이라는 선점 결과를 도메인 상태에 반영한다. 실 DB에서는 이미 SENDING이다.
        if (history.getStatus() != EmailStatus.SENDING) {
            if (history.getStatus() == EmailStatus.PENDING
                    || history.getStatus() == EmailStatus.FAILED) {
                history.markSending();
            } else {
                return;
            }
        }

        User recipient = history.getUser();
        // 발행 시점에는 재직이었어도 발송 직전에 퇴직했을 수 있다.
        if (!recipient.isActive()) {
            log.info("[이메일] 퇴직자 수신 제외 — userId={}, historyId={}", recipient.getId(), historyId);
            history.markFailed(ERROR_RECIPIENT_INACTIVE);
            return;
        }

        attempt(history, recipient);
    }

    /**
     * 재시도 대상 id — FAILED(상한 미만) + <b>오래 묶여 있는 PENDING</b>.
     *
     * <p>PENDING을 포함하는 것이 1차 테스트 B의 안전망이다. 큐 포화나 강제 종료로 비동기 발송이
     * 아예 시작되지 못한 건이 여기로 회수된다. 프로세스가 발송 도중 중단된 SENDING도
     * 같은 10분 기준으로 먼저 FAILED 복구한다.
     */
    @Transactional
    public List<Long> findDispatchTargetIds() {
        // 프로세스 중단으로 SENDING에 남은 건을 FAILED로 되돌린 뒤 같은 주기의 재시도 대상에 넣는다.
        emailHistoryRepository.recoverStaleSending(
                EmailStatus.SENDING,
                EmailStatus.FAILED,
                LocalDateTime.now().minus(SENDING_STALE_AFTER),
                "발송 프로세스 중단으로 SENDING 상태가 만료되어 재시도합니다.");
        return emailHistoryRepository.findDispatchTargetIds(
                MAX_ATTEMPTS,
                EmailStatus.FAILED,
                EmailStatus.PENDING,
                LocalDateTime.now().minus(PENDING_STALE_AFTER),
                PageRequest.of(0, BATCH_SIZE));
    }

    private void attempt(EmailHistory history, User recipient) {
        if (recipient.getEmail() == null || recipient.getEmail().isBlank()) {
            log.warn("[이메일] 수신자 이메일 없음 — userId={}, historyId={}",
                    recipient.getId(), history.getId());
            history.markFailed(ERROR_RECIPIENT_EMAIL_MISSING);
            return;
        }

        MailSenderResolver.SenderSelection senderSelection = mailSenderResolver.resolve();
        if (senderSelection.username().isBlank()) {
            log.warn("[이메일] MAIL_USERNAME 미설정 — 발송 건너뜀, historyId={}", history.getId());
            history.markFailed(ERROR_MAIL_ACCOUNT_MISSING);
            return;
        }

        try {
            MimeMessage mimeMessage = senderSelection.sender().createMimeMessage();
            MimeMessageHelper helper = new MimeMessageHelper(
                    mimeMessage,
                    false,
                    StandardCharsets.UTF_8.name());

            // 개인 Gmail은 From 주소를 계정 주소로 강제하므로 주소는 username 그대로 쓰고 표시 이름만 지정한다.
            helper.setFrom(senderSelection.username(), FROM_NAME);
            helper.setTo(recipient.getEmail());
            if (mailAppProperties.hasReplyTo()) {
                helper.setReplyTo(mailAppProperties.replyTo());
            }
            helper.setSubject(history.getTitle());
            // 본문은 HTML이다 (EmailTemplateFactory) — false로 보내면 태그가 글자 그대로 노출된다.
            // email_history.content에 HTML이 그대로 들어 있어, 그 값을 브라우저에 붙이면
            // 수신자가 받은 화면을 그대로 재현할 수 있다(감사용).
            helper.setText(history.getContent(), true);

            senderSelection.sender().send(mimeMessage);
            history.markSent();
            log.info("[이메일] 발송 성공 — historyId={}, userId={}",
                    history.getId(), recipient.getId());
        } catch (Exception e) {
            history.markFailed(rootMessage(e));
            log.warn("[이메일] 발송 실패 — historyId={}, userId={}, retryCount={}",
                    history.getId(), recipient.getId(), history.getRetryCount(), e);
        }
    }

    private String rootMessage(Exception exception) {
        Throwable cause = exception;
        while (cause.getCause() != null) {
            cause = cause.getCause();
        }
        String message = cause.getMessage();
        return message == null || message.isBlank()
                ? cause.getClass().getSimpleName()
                : message;
    }
}
