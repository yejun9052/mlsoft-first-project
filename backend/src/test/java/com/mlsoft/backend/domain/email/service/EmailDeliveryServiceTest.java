package com.mlsoft.backend.domain.email.service;

import com.mlsoft.backend.config.MailAppProperties;
import com.mlsoft.backend.domain.email.entity.EmailHistory;
import com.mlsoft.backend.domain.email.entity.EmailStatus;
import com.mlsoft.backend.domain.email.entity.EmailType;
import com.mlsoft.backend.domain.email.repository.EmailHistoryRepository;
import com.mlsoft.backend.domain.user.entity.Role;
import com.mlsoft.backend.domain.user.entity.User;
import jakarta.mail.internet.MimeMessage;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.mail.javamail.JavaMailSender;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.Optional;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.CyclicBarrier;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicBoolean;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
class EmailDeliveryServiceTest {

    @Mock
    private JavaMailSender mailSender;
    @Mock
    private EmailHistoryRepository emailHistoryRepository;

    @Test
    @DisplayName("메일 계정이 없어도 예외 없이 FAILED와 시도 횟수를 기록한다")
    void send_계정미설정_failed기록() {
        EmailHistory history = pendingHistory(user(true));
        given(emailHistoryRepository.findById(1L)).willReturn(Optional.of(history));

        service("").send(1L);

        verify(mailSender, never()).send(any(MimeMessage.class));
        assertEquals(EmailStatus.FAILED, history.getStatus());
        assertEquals(1, history.getRetryCount());
        assertNull(history.getSendingAt());
    }

    @Test
    @DisplayName("발송 성공 후 SENDING 선점 시각을 비운다")
    void send_성공_sending시각초기화() {
        EmailHistory history = pendingHistory(user(true));
        MimeMessage mimeMessage = org.mockito.Mockito.mock(MimeMessage.class);
        given(emailHistoryRepository.findById(1L)).willReturn(Optional.of(history));
        given(mailSender.createMimeMessage()).willReturn(mimeMessage);

        service("sender@gmail.com").send(1L);

        assertEquals(EmailStatus.SENT, history.getStatus());
        assertNotNull(history.getSentAt());
        assertNull(history.getSendingAt());
    }

    @Test
    @DisplayName("발송 실패 후 SENDING 선점 시각을 비운다")
    void send_실패_sending시각초기화() {
        EmailHistory history = pendingHistory(user(true));
        given(emailHistoryRepository.findById(1L)).willReturn(Optional.of(history));
        given(mailSender.createMimeMessage()).willThrow(new IllegalStateException("SMTP 실패"));

        service("sender@gmail.com").send(1L);

        assertEquals(EmailStatus.FAILED, history.getStatus());
        assertEquals(1, history.getRetryCount());
        assertNull(history.getSendingAt());
    }

    @Test
    @DisplayName("발송 직전 퇴직한 수신자에게는 보내지 않는다")
    void send_퇴직자_발송안함() {
        EmailHistory history = pendingHistory(user(false));
        given(emailHistoryRepository.findById(1L)).willReturn(Optional.of(history));

        service("sender@gmail.com").send(1L);

        verify(mailSender, never()).send(any(MimeMessage.class));
        assertEquals(EmailStatus.FAILED, history.getStatus());
    }

    @Test
    @DisplayName("이미 보낸 건은 다시 보내지 않는다 — 스케줄러와 비동기 리스너가 겹쳐도 중복 발송이 없다")
    void send_이미SENT면_건너뛴다() {
        EmailHistory history = pendingHistory(user(true));
        history.markSent();
        given(emailHistoryRepository.findById(1L)).willReturn(Optional.of(history));

        service("sender@gmail.com").send(1L);

        verify(mailSender, never()).createMimeMessage();
        assertEquals(EmailStatus.SENT, history.getStatus());
    }

    @Test
    @DisplayName("재시도 상한에 도달한 건은 다시 시도하지 않는다 — 영구 실패 건 무한 재시도 방지 (D-4)")
    void send_상한도달이면_건너뛴다() {
        EmailHistory history = pendingHistory(user(true));
        for (int i = 0; i < EmailDeliveryService.MAX_ATTEMPTS; i++) {
            history.markFailed("실패 " + (i + 1));
        }
        given(emailHistoryRepository.findById(1L)).willReturn(Optional.of(history));

        service("sender@gmail.com").send(1L);

        verify(mailSender, never()).createMimeMessage();
        assertEquals(EmailDeliveryService.MAX_ATTEMPTS, history.getRetryCount(),
                "상한 도달 후에는 시도 횟수가 더 늘지 않아야 한다");
    }

    @Test
    @DisplayName("같은 이력을 동시에 발송해도 조건부 선점은 한 번만 성공한다")
    void send_동시호출_한건만선점() throws Exception {
        EmailHistory history = pendingHistory(user(true));
        given(emailHistoryRepository.findById(1L)).willReturn(Optional.of(history));
        AtomicInteger claimCalls = new AtomicInteger();
        AtomicInteger successfulClaims = new AtomicInteger();
        AtomicBoolean winner = new AtomicBoolean();
        CyclicBarrier claimBarrier = new CyclicBarrier(2);
        org.mockito.Mockito.lenient().when(emailHistoryRepository.claimForSending(
                        any(),
                        any(LocalDateTime.class),
                        org.mockito.ArgumentMatchers.eq(EmailStatus.SENDING),
                        org.mockito.ArgumentMatchers.eq(EmailStatus.PENDING),
                        org.mockito.ArgumentMatchers.eq(EmailStatus.FAILED),
                        org.mockito.ArgumentMatchers.eq(EmailDeliveryService.MAX_ATTEMPTS)))
                .thenAnswer(invocation -> {
                    claimCalls.incrementAndGet();
                    try {
                        claimBarrier.await(5, TimeUnit.SECONDS);
                    } catch (Exception e) {
                        throw new IllegalStateException(e);
                    }
                    if (winner.compareAndSet(false, true)) {
                        successfulClaims.incrementAndGet();
                        return 1;
                    }
                    return 0;
                });

        EmailDeliveryService concurrentService = new EmailDeliveryService(
                mailSender, emailHistoryRepository, new MailAppProperties(""), "");
        ExecutorService executor = Executors.newFixedThreadPool(2);
        try {
            executor.submit(() -> concurrentService.send(1L));
            executor.submit(() -> concurrentService.send(1L));
        } finally {
            executor.shutdown();
            executor.awaitTermination(5, TimeUnit.SECONDS);
        }

        assertEquals(2, claimCalls.get());
        assertEquals(1, successfulClaims.get());
    }

    private EmailDeliveryService service(String username) {
        // 실제 DB에서는 조건부 UPDATE가 1을 반환한 경우에만 발송한다. 단위 테스트 목도 같은
        // 선점 결과를 명시해 발송 경계를 검증한다.
        org.mockito.Mockito.lenient().when(emailHistoryRepository.claimForSending(
                any(),
                any(LocalDateTime.class),
                org.mockito.ArgumentMatchers.eq(EmailStatus.SENDING),
                org.mockito.ArgumentMatchers.eq(EmailStatus.PENDING),
                org.mockito.ArgumentMatchers.eq(EmailStatus.FAILED),
                org.mockito.ArgumentMatchers.eq(EmailDeliveryService.MAX_ATTEMPTS)))
                .thenReturn(1);
        return new EmailDeliveryService(
                mailSender, emailHistoryRepository, new MailAppProperties(""), username);
    }

    private EmailHistory pendingHistory(User recipient) {
        return EmailHistory.create(recipient, null, EmailType.LEAVE, "제목", "본문");
    }

    private User user(boolean active) {
        return User.builder()
                .id(1L)
                .name("테스트 사원")
                .email("user@mlsoft.com")
                .role(Role.EMPLOYEE)
                .baseDays(new BigDecimal("15.0"))
                .useDays(BigDecimal.ZERO)
                .bonusDays(BigDecimal.ZERO)
                .advanceDays(BigDecimal.ZERO)
                .isActive(active)
                .build();
    }
}
