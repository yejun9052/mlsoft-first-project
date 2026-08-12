package com.mlsoft.backend.domain.email.service;

import com.mlsoft.backend.domain.email.event.EmailNotificationEvent;
import com.mlsoft.backend.domain.leave.entity.LeaveRequest;
import com.mlsoft.backend.domain.leave.entity.LeaveType;
import com.mlsoft.backend.domain.user.entity.Role;
import com.mlsoft.backend.domain.user.entity.User;
import com.mlsoft.backend.domain.user.repository.UserRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.context.ApplicationEventPublisher;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
class EmailNotificationPublisherTest {

    @Mock
    private ApplicationEventPublisher applicationEventPublisher;

    @Mock
    private UserRepository userRepository;

    @Test
    @DisplayName("퇴직자는 신청 알림 수신자에서 제외한다")
    void publishLeaveApplied_퇴직자제외() {
        User applicant = user(1L, "신청자", Role.EMPLOYEE, true);
        User retiredApprover = user(2L, "퇴직 팀장", Role.TEAM_LEADER, false);
        LeaveRequest leave = LeaveRequest.create(
                applicant,
                LeaveType.ANNUAL,
                List.of(LocalDate.of(2026, 8, 20)),
                "개인 사유",
                retiredApprover,
                null);

        EmailNotificationPublisher publisher =
                new EmailNotificationPublisher(applicationEventPublisher, userRepository);

        publisher.publishLeaveApplied(leave);

        ArgumentCaptor<EmailNotificationEvent> captor =
                ArgumentCaptor.forClass(EmailNotificationEvent.class);
        verify(applicationEventPublisher).publishEvent(captor.capture());

        assertEquals(List.of(1L), captor.getValue().recipients().stream()
                .map(recipient -> recipient.userId())
                .toList());
    }

    @Test
    @DisplayName("신청자와 승인자와 관리자가 같아도 수신자 한 건으로 합친다")
    void publishLeaveProcessed_중복수신자제거() {
        User systemAdmin = user(1L, "관리자", Role.SYSTEM_ADMIN, true);
        LeaveRequest leave = LeaveRequest.create(
                systemAdmin,
                LeaveType.HALF_AM,
                List.of(LocalDate.of(2026, 8, 20)),
                "병원 방문",
                systemAdmin,
                systemAdmin);
        given(userRepository.findByRoleAndIsActiveTrue(Role.SYSTEM_ADMIN))
                .willReturn(List.of(systemAdmin));

        EmailNotificationPublisher publisher =
                new EmailNotificationPublisher(applicationEventPublisher, userRepository);

        publisher.publishLeaveProcessed(leave, systemAdmin, true);

        ArgumentCaptor<EmailNotificationEvent> captor =
                ArgumentCaptor.forClass(EmailNotificationEvent.class);
        verify(applicationEventPublisher).publishEvent(captor.capture());

        assertEquals(1, captor.getValue().recipients().size());
        assertEquals(1L, captor.getValue().recipients().getFirst().userId());
    }

    private User user(Long id, String name, Role role, boolean active) {
        return User.builder()
                .id(id)
                .name(name)
                .email(name + "@mlsoft.com")
                .role(role)
                .baseDays(new BigDecimal("15.0"))
                .useDays(BigDecimal.ZERO)
                .bonusDays(BigDecimal.ZERO)
                .advanceDays(BigDecimal.ZERO)
                .isActive(active)
                .build();
    }
}
