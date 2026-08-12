package com.mlsoft.backend.domain.email.service;

import com.mlsoft.backend.config.MailAppProperties;
import com.mlsoft.backend.domain.email.entity.EmailHistory;
import com.mlsoft.backend.domain.email.entity.EmailStatus;
import com.mlsoft.backend.domain.email.entity.EmailType;
import com.mlsoft.backend.domain.email.repository.EmailHistoryRepository;
import com.mlsoft.backend.domain.user.entity.User;
import com.mlsoft.backend.domain.user.repository.UserRepository;
import jakarta.mail.internet.MimeMessage;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mail.javamail.MimeMessageHelper;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Optional;

/**
 * SMTP 발송과 email_history 상태 전이를 담당한다.
 *
 * <p>모든 예외를 FAILED로 변환하고 밖으로 전파하지 않는다. 메일 장애가 업무 처리를 실패시키면
 * 안 되기 때문이다(검증 R-4).
 */
@Slf4j
@Service
public class EmailDeliveryService {

    public static final int MAX_ATTEMPTS = 3;

    private static final String FROM_NAME = "MLsoft 연차관리";
    private static final String ERROR_MAIL_ACCOUNT_MISSING = "메일 발송 계정이 설정되지 않았습니다.";
    private static final String ERROR_RECIPIENT_EMAIL_MISSING = "수신자 이메일이 설정되지 않았습니다.";
    private static final String ERROR_RECIPIENT_INACTIVE = "퇴직 처리된 수신자라 재발송하지 않았습니다.";

    private final JavaMailSender mailSender;
    private final EmailHistoryRepository emailHistoryRepository;
    private final UserRepository userRepository;
    private final MailAppProperties mailAppProperties;
    private final String username;

    public EmailDeliveryService(
            JavaMailSender mailSender,
            EmailHistoryRepository emailHistoryRepository,
            UserRepository userRepository,
            MailAppProperties mailAppProperties,
            @Value("${spring.mail.username:}") String username
    ) {
        this.mailSender = mailSender;
        this.emailHistoryRepository = emailHistoryRepository;
        this.userRepository = userRepository;
        this.mailAppProperties = mailAppProperties;
        this.username = username == null ? "" : username.trim();
    }

    /**
     * 새 발송 이력을 만들고 즉시 한 번 발송한다.
     *
     * <p>호출자는 반드시 커밋 후 시작한 새 트랜잭션이어야 한다.
     */
    public void createAndSend(
            User recipient,
            EmailType emailType,
            EmailMessage message
    ) {
        EmailHistory history = emailHistoryRepository.save(
                EmailHistory.create(recipient, null, emailType, message.title(), message.content()));
        attempt(history);
    }

    /**
     * FAILED 한 건을 별도 트랜잭션으로 재시도한다.
     *
     * <p>한 건의 DB·SMTP 오류가 다음 실패 건 처리를 막지 않게 건마다 트랜잭션을 끊는다.
     */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void retry(Long historyId) {
        Optional<EmailHistory> optionalHistory = emailHistoryRepository.findById(historyId);
        if (optionalHistory.isEmpty()) {
            return;
        }

        EmailHistory history = optionalHistory.get();
        if (history.getStatus() != EmailStatus.FAILED
                || history.getRetryCount() >= MAX_ATTEMPTS) {
            return;
        }

        if (!history.getUser().isActive()) {
            history.markFailed(ERROR_RECIPIENT_INACTIVE);
            return;
        }

        attempt(history);
    }

    @Transactional(readOnly = true)
    public List<Long> findRetryTargetIds() {
        return emailHistoryRepository
                .findTop100ByStatusAndRetryCountLessThanOrderByIdAsc(
                        EmailStatus.FAILED,
                        MAX_ATTEMPTS)
                .stream()
                .map(EmailHistory::getId)
                .toList();
    }

    private void attempt(EmailHistory history) {
        User recipient = history.getUser();

        if (recipient.getEmail() == null || recipient.getEmail().isBlank()) {
            log.warn("[이메일] 수신자 이메일 없음 — userId={}, historyId={}",
                    recipient.getId(), history.getId());
            history.markFailed(ERROR_RECIPIENT_EMAIL_MISSING);
            return;
        }

        if (username.isBlank()) {
            log.warn("[이메일] MAIL_USERNAME 미설정 — 발송 건너뜀, historyId={}", history.getId());
            history.markFailed(ERROR_MAIL_ACCOUNT_MISSING);
            return;
        }

        try {
            MimeMessage mimeMessage = mailSender.createMimeMessage();
            MimeMessageHelper helper = new MimeMessageHelper(
                    mimeMessage,
                    false,
                    StandardCharsets.UTF_8.name());

            // 개인 Gmail은 From 주소를 계정 주소로 강제하므로 주소는 username 그대로 쓰고 표시 이름만 지정한다.
            helper.setFrom(username, FROM_NAME);
            helper.setTo(recipient.getEmail());
            if (mailAppProperties.hasReplyTo()) {
                helper.setReplyTo(mailAppProperties.replyTo());
            }
            helper.setSubject(history.getTitle());
            helper.setText(history.getContent(), false);

            mailSender.send(mimeMessage);
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
