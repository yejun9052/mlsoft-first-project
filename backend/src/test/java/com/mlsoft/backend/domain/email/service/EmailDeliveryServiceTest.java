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
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
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

    private EmailDeliveryService service(String username) {
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
