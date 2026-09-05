package com.mlsoft.backend.domain.email.service;

import com.mlsoft.backend.domain.audit.service.AdminAuditService;
import com.mlsoft.backend.domain.email.dto.EmailBulkRequest;
import com.mlsoft.backend.domain.email.entity.EmailHistory;
import com.mlsoft.backend.domain.email.entity.EmailStatus;
import com.mlsoft.backend.domain.email.entity.EmailType;
import com.mlsoft.backend.domain.email.event.EmailDispatchEvent;
import com.mlsoft.backend.domain.email.repository.EmailHistoryRepository;
import com.mlsoft.backend.domain.user.entity.Role;
import com.mlsoft.backend.domain.user.entity.User;
import com.mlsoft.backend.domain.user.repository.UserRepository;
import com.mlsoft.backend.global.exception.BusinessException;
import com.mlsoft.backend.global.exception.ErrorCode;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.context.ApplicationEventPublisher;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneId;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
class EmailAdminServiceTest {

    @Mock
    private EmailHistoryRepository emailHistoryRepository;
    @Mock
    private UserRepository userRepository;
    @Mock
    private LeaveReminderService leaveReminderService;
    @Mock
    private ApplicationEventPublisher eventPublisher;
    @Mock
    private AdminAuditService adminAuditService;

    private EmailAdminService service;

    @BeforeEach
    void setUp() {
        service = new EmailAdminService(
                emailHistoryRepository,
                userRepository,
                leaveReminderService,
                eventPublisher,
                adminAuditService,
                Clock.fixed(Instant.parse("2026-09-05T00:00:00Z"), ZoneId.of("Asia/Seoul")));
    }

    @Test
    @DisplayName("일괄 발송은 이메일이 있는 사원만 NOTICE 아웃박스에 넣는다")
    void bulk_이메일없는사원_skip() {
        User withEmail = user(1L, "with@mlsoft.com");
        User withoutEmail = user(2L, "");
        given(emailHistoryRepository.countByCreatedAtGreaterThanEqualAndCreatedAtLessThan(any(), any()))
                .willReturn(0L);
        given(userRepository.findAllById(List.of(1L, 2L))).willReturn(List.of(withEmail, withoutEmail));
        EmailHistory saved = EmailHistory.builder()
                .id(11L).user(withEmail).emailType(EmailType.NOTICE)
                .title("공지").content("본문").status(EmailStatus.PENDING).build();
        given(emailHistoryRepository.save(any(EmailHistory.class))).willReturn(saved);

        var result = service.bulk(new EmailBulkRequest(List.of(1L, 2L), "공지", "본문"), 99L);

        assertEquals(2, result.requested());
        assertEquals(1, result.queued());
        assertEquals(1, result.skipped());
        ArgumentCaptor<EmailHistory> captor = ArgumentCaptor.forClass(EmailHistory.class);
        verify(emailHistoryRepository).save(captor.capture());
        assertEquals(EmailType.NOTICE, captor.getValue().getEmailType());
        verify(eventPublisher).publishEvent(any(EmailDispatchEvent.class));
    }

    @Test
    @DisplayName("일일 400건을 넘기는 일괄 발송은 저장하지 않는다")
    void bulk_일일상한초과() {
        given(emailHistoryRepository.countByCreatedAtGreaterThanEqualAndCreatedAtLessThan(any(), any()))
                .willReturn(400L);

        BusinessException exception = assertThrows(BusinessException.class, () -> service.bulk(
                new EmailBulkRequest(List.of(1L), "공지", "본문"), 99L));

        assertEquals(ErrorCode.EMAIL_BULK_LIMIT_EXCEEDED, exception.getErrorCode());
    }

    @Test
    @DisplayName("FAILED 재발송은 retry_count를 0으로 초기화하고 PENDING으로 되돌린다")
    void resend_failed_재설정() {
        User user = user(1L, "failed@mlsoft.com");
        EmailHistory history = EmailHistory.builder()
                .id(12L).user(user).emailType(EmailType.NOTICE)
                .title("공지").content("본문").status(EmailStatus.PENDING).build();
        history.markFailed("SMTP");
        given(emailHistoryRepository.findByIdWithUser(12L)).willReturn(Optional.of(history));

        var result = service.resend(12L, 99L);

        assertEquals(EmailStatus.PENDING, history.getStatus());
        assertEquals(0, history.getRetryCount());
        assertEquals(EmailStatus.PENDING, result.status());
        verify(eventPublisher).publishEvent(any(EmailDispatchEvent.class));
    }

    @Test
    @DisplayName("FAILED가 아닌 이력은 재발송할 수 없다")
    void resend_sent_거부() {
        User user = user(1L, "sent@mlsoft.com");
        EmailHistory history = EmailHistory.create(user, null, EmailType.NOTICE, "공지", "본문");
        history.markSent();
        given(emailHistoryRepository.findByIdWithUser(13L)).willReturn(Optional.of(history));

        BusinessException exception = assertThrows(BusinessException.class,
                () -> service.resend(13L, 99L));

        assertEquals(ErrorCode.EMAIL_RESEND_NOT_ALLOWED, exception.getErrorCode());
    }

    private User user(Long id, String email) {
        return User.builder()
                .id(id)
                .name("수신자")
                .email(email)
                .role(Role.EMPLOYEE)
                .baseDays(new BigDecimal("15.0"))
                .useDays(BigDecimal.ZERO)
                .bonusDays(BigDecimal.ZERO)
                .advanceDays(BigDecimal.ZERO)
                .isActive(true)
                .build();
    }
}
