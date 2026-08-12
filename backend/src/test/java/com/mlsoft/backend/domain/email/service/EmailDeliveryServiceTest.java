package com.mlsoft.backend.domain.email.service;

import com.mlsoft.backend.config.MailAppProperties;
import com.mlsoft.backend.domain.email.entity.EmailHistory;
import com.mlsoft.backend.domain.email.entity.EmailStatus;
import com.mlsoft.backend.domain.email.entity.EmailType;
import com.mlsoft.backend.domain.email.repository.EmailHistoryRepository;
import com.mlsoft.backend.domain.user.entity.Role;
import com.mlsoft.backend.domain.user.entity.User;
import com.mlsoft.backend.domain.user.repository.UserRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.mail.javamail.JavaMailSender;

import java.math.BigDecimal;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
class EmailDeliveryServiceTest {

    @Mock
    private JavaMailSender mailSender;

    @Mock
    private EmailHistoryRepository emailHistoryRepository;

    @Mock
    private UserRepository userRepository;

    @Test
    @DisplayName("메일 계정이 없어도 예외 없이 FAILED 이력과 시도 횟수를 기록한다")
    void createAndSend_계정미설정_failed기록() {
        User user = user();
        given(emailHistoryRepository.save(org.mockito.ArgumentMatchers.any(EmailHistory.class)))
                .willAnswer(invocation -> invocation.getArgument(0));

        EmailDeliveryService service = new EmailDeliveryService(
                mailSender,
                emailHistoryRepository,
                userRepository,
                new MailAppProperties(""),
                "");

        service.createAndSend(
                user,
                EmailType.LEAVE,
                new EmailMessage("제목", "본문"));

        ArgumentCaptor<EmailHistory> captor = ArgumentCaptor.forClass(EmailHistory.class);
        verify(emailHistoryRepository).save(captor.capture());
        verify(mailSender, never()).send(org.mockito.ArgumentMatchers.any(jakarta.mail.internet.MimeMessage.class));

        assertEquals(EmailStatus.FAILED, captor.getValue().getStatus());
        assertEquals(1, captor.getValue().getRetryCount());
    }

    @Test
    @DisplayName("retry_count가 3 이상인 FAILED는 재시도 조회 대상에서 빠진다")
    void findRetryTargetIds_상한은저장소조건으로제외() {
        given(emailHistoryRepository.findTop100ByStatusAndRetryCountLessThanOrderByIdAsc(
                EmailStatus.FAILED,
                EmailDeliveryService.MAX_ATTEMPTS))
                .willReturn(List.of());

        EmailDeliveryService service = new EmailDeliveryService(
                mailSender,
                emailHistoryRepository,
                userRepository,
                new MailAppProperties(""),
                "sender@gmail.com");

        assertEquals(List.of(), service.findRetryTargetIds());
        verify(emailHistoryRepository)
                .findTop100ByStatusAndRetryCountLessThanOrderByIdAsc(
                        EmailStatus.FAILED,
                        3);
    }

    private User user() {
        return User.builder()
                .id(1L)
                .name("테스트 사원")
                .email("user@mlsoft.com")
                .role(Role.EMPLOYEE)
                .baseDays(new BigDecimal("15.0"))
                .useDays(BigDecimal.ZERO)
                .bonusDays(BigDecimal.ZERO)
                .advanceDays(BigDecimal.ZERO)
                .isActive(true)
                .build();
    }
}
