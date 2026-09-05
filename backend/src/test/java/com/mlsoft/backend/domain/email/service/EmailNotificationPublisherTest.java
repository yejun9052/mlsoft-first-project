package com.mlsoft.backend.domain.email.service;

import com.mlsoft.backend.config.AppProperties;
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
import static org.junit.jupiter.api.Assertions.assertFalse;
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

    // 실제 팩토리를 쓴다 — 본문이 무엇으로 채워지는지가 이 테스트의 관심사이기도 하다
    private final EmailTemplateFactory emailTemplateFactory = new EmailTemplateFactory(
            new AppProperties("mlsoft.com", java.util.List.of(), "http://localhost:5173", false));

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

    // 2026-08-16에 수신자가 "구분: ANNUAL"을 받았다. LeaveType에만 라벨이 없어
    // publishLeave가 name()을 그대로 넘겼기 때문이다.
    @Test
    @DisplayName("연차 종류는 enum 이름이 아니라 한글 라벨로 나간다")
    void publish_연차종류_한글라벨() {
        User applicant = user(1L, "신청자", Role.EMPLOYEE, true);
        User approver = user(2L, "팀장", Role.TEAM_LEADER, true);
        LeaveRequest leave = LeaveRequest.create(
                applicant,
                LeaveType.HALF_AM,
                List.of(LocalDate.of(2026, 8, 20)),
                "개인 사유",
                approver,
                null);
        givenSavedHistoriesGetIds();

        publisher().publishLeaveApplied(leave);

        assertTrue(captureSaved().stream().allMatch(h -> h.getContent().contains("오전 반차")),
                "한글 라벨이 본문에 없다");
        assertTrue(captureSaved().stream().noneMatch(h -> h.getContent().contains("HALF_AM")),
                "enum 이름이 그대로 새어 나갔다");
    }

    @Test
    @DisplayName("온보딩 승인 대기는 재직 SYSTEM_ADMIN에게 NOTICE로 보낸다")
    void publishOnboardingPending_관리자수신_NOTICE() {
        User applicant = user(1L, "신청자", Role.EMPLOYEE, true);
        applicant.requestOnboardingApproval(LocalDate.of(1990, 1, 1), LocalDate.of(1995, 4, 1));
        User admin = user(2L, "관리자", Role.SYSTEM_ADMIN, true);
        given(userRepository.findByRoleAndIsActiveTrue(Role.SYSTEM_ADMIN)).willReturn(List.of(admin));
        givenSavedHistoriesGetIds();

        publisher().publishOnboardingPending(applicant);

        EmailHistory history = captureSaved().getFirst();
        assertEquals(admin, history.getUser());
        assertEquals(com.mlsoft.backend.domain.email.entity.EmailType.NOTICE, history.getEmailType());
        assertTrue(history.getTitle().contains("온보딩 승인 대기"));
    }

    @Test
    @DisplayName("온보딩 반려는 사원 본인에게 캡처한 입사일을 포함해 보낸다")
    void publishOnboardingRejected_본인수신_입사일포함() {
        User applicant = user(1L, "신청자", Role.EMPLOYEE, true);
        givenSavedHistoriesGetIds();

        publisher().publishOnboardingRejected(applicant, LocalDate.of(1990, 1, 1));

        EmailHistory history = captureSaved().getFirst();
        assertEquals(applicant, history.getUser());
        assertTrue(history.getContent().contains("1990-01-01"));
        assertTrue(history.getTitle().contains("온보딩 반려"));
    }

    @Test
    @DisplayName("온보딩 수정 결과는 확정·승인 대기 문구를 나눈다")
    void publishOnboardingRevised_결과문구분기() {
        User applicant = user(1L, "신청자", Role.EMPLOYEE, true);
        applicant.requestOnboardingApproval(LocalDate.of(1990, 1, 1), LocalDate.of(1995, 4, 1));
        User admin = user(2L, "관리자", Role.SYSTEM_ADMIN, true);
        given(userRepository.findByRoleAndIsActiveTrue(Role.SYSTEM_ADMIN)).willReturn(List.of(admin));
        givenSavedHistoriesGetIds();

        publisher().publishOnboardingRevised(applicant);

        assertTrue(captureSaved().getFirst().getContent().contains("승인 대기 상태로 다시"));
    }

    // 2026-08-17: 신청자에게도 "결재하러 가기" 버튼이 갔다. 본문은 원래 수신자별로 만들고
    // 있었는데(사유 마스킹) 버튼만 그 갈래를 안 타서, 신청자가 자기 신청을 결재하러 가는
    // 링크를 받았다. 팩토리 단위 테스트와 별개로 **발행부가 신청자를 실제로 구분해 넘기는지**를
    // 여기서 본다 — 팩토리만 고치고 이 배선을 빠뜨리면 증상이 그대로 남는다.
    @Test
    @DisplayName("같은 신청의 메일이라도 신청자에게는 결재 링크가 가지 않는다")
    void publishLeaveApplied_신청자에게는결재링크없음() {
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

        List<EmailHistory> saved = captureSaved();
        String toApplicant = contentFor(saved, applicant);
        String toApprover = contentFor(saved, approver);

        assertFalse(toApplicant.contains("결재하러 가기"), "신청자에게 결재 버튼이 갔다");
        assertFalse(toApplicant.contains("/approvals"), "신청자를 결재 화면으로 보냈다");
        // 결재자 쪽은 그대로여야 한다 — 신청자를 고치면서 결재자 링크까지 없애면 알림이 무용해진다
        assertTrue(toApprover.contains("결재하러 가기"), "결재자에게 결재 버튼이 사라졌다");
        assertTrue(toApprover.contains("/approvals"));
    }

    private String contentFor(List<EmailHistory> saved, User recipient) {
        return saved.stream()
                .filter(h -> h.getUser().equals(recipient))
                .findFirst()
                .orElseThrow(() -> new AssertionError(recipient.getName() + "에게 갈 이력이 없다"))
                .getContent();
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
