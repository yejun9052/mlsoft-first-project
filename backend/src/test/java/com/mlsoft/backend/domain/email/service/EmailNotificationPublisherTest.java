package com.mlsoft.backend.domain.email.service;

import com.mlsoft.backend.domain.email.entity.EmailHistory;
import com.mlsoft.backend.domain.email.event.EmailDispatchEvent;
import com.mlsoft.backend.domain.email.repository.EmailHistoryRepository;
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
import java.util.concurrent.atomic.AtomicLong;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
class EmailNotificationPublisherTest {

    @Mock
    private ApplicationEventPublisher applicationEventPublisher;
    @Mock
    private UserRepository userRepository;
    @Mock
    private EmailHistoryRepository emailHistoryRepository;

    private final EmailTemplateFactory emailTemplateFactory = new EmailTemplateFactory();

    @Test
    @DisplayName("퇴직한 승인자는 수신자에서 빠진다 — 이력도 만들지 않는다")
    void publishLeaveApplied_퇴직자제외() {
        User applicant = user(1L, "신청자", Role.EMPLOYEE, true);
        User retiredApprover = user(2L, "퇴직팀장", Role.TEAM_LEADER, false);
        LeaveRequest leave = LeaveRequest.create(
                applicant,
                LeaveType.ANNUAL,
                List.of(LocalDate.of(2026, 8, 20)),
                "개인 사유",
                retiredApprover,
                null);
        givenSavedHistoriesGetIds();

        publisher().publishLeaveApplied(leave);

        List<EmailHistory> saved = captureSaved();
        assertEquals(1, saved.size());
        assertEquals(applicant, saved.getFirst().getUser());
    }

    @Test
    @DisplayName("신청자와 승인자와 관리자가 같아도 이력 한 건으로 합친다")
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
        givenSavedHistoriesGetIds();

        publisher().publishLeaveProcessed(leave, systemAdmin, true);

        assertEquals(1, captureSaved().size());
    }

    /**
     * 1차 테스트 B — 이력은 업무 트랜잭션 안에서 만들어져야 한다.
     *
     * <p>이 테스트가 지키는 것은 "이벤트를 발행하기 전에 저장이 끝나 있다"는 순서다.
     * 순서가 뒤집히면(= 리스너가 이력을 만들면) 리스너가 실행되지 못했을 때 알림이 흔적 없이 사라진다.
     */
    @Test
    @DisplayName("이벤트는 이미 저장된 이력 id만 나른다 — 저장이 발행보다 먼저다")
    void publish_이력저장후_id만발행() {
        User applicant = user(1L, "신청자", Role.EMPLOYEE, true);
        User approver = user(2L, "팀장", Role.TEAM_LEADER, true);
        LeaveRequest leave = LeaveRequest.create(
                applicant,
                LeaveType.ANNUAL,
                List.of(LocalDate.of(2026, 8, 20)),
                "개인 사유",
                approver,
                null);
        givenSavedHistoriesGetIds();

        publisher().publishLeaveApplied(leave);

        ArgumentCaptor<EmailDispatchEvent> captor = ArgumentCaptor.forClass(EmailDispatchEvent.class);
        verify(applicationEventPublisher).publishEvent(captor.capture());

        // 저장된 두 건의 id가 그대로 이벤트에 실린다 — null이면 저장 전에 발행한 것이다
        assertEquals(List.of(1L, 2L), captor.getValue().historyIds());
        assertTrue(captureSaved().stream().allMatch(h -> h.getContent() != null && !h.getContent().isBlank()),
                "본문은 발행 전에 확정돼 있어야 한다 (수신자별 마스킹이 다르므로)");
    }

    private EmailNotificationPublisher publisher() {
        return new EmailNotificationPublisher(
                applicationEventPublisher, userRepository, emailTemplateFactory, emailHistoryRepository);
    }

    /** save()가 id를 채워 돌려주도록 — 실제 JPA와 같은 동작 */
    private void givenSavedHistoriesGetIds() {
        AtomicLong sequence = new AtomicLong();
        given(emailHistoryRepository.save(any(EmailHistory.class))).willAnswer(invocation -> {
            EmailHistory history = invocation.getArgument(0);
            java.lang.reflect.Field idField = EmailHistory.class.getDeclaredField("id");
            idField.setAccessible(true);
            idField.set(history, sequence.incrementAndGet());
            return history;
        });
    }

    private List<EmailHistory> captureSaved() {
        ArgumentCaptor<EmailHistory> captor = ArgumentCaptor.forClass(EmailHistory.class);
        verify(emailHistoryRepository, org.mockito.Mockito.atLeastOnce()).save(captor.capture());
        return captor.getAllValues();
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
