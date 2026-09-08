package com.mlsoft.backend.domain.user.service;

import com.mlsoft.backend.domain.audit.entity.AdminAction;
import com.mlsoft.backend.domain.audit.entity.AdminAuditLog;
import com.mlsoft.backend.domain.audit.repository.AdminAuditLogRepository;
import com.mlsoft.backend.domain.common.RequestAction;
import com.mlsoft.backend.domain.email.entity.EmailHistory;
import com.mlsoft.backend.domain.email.entity.EmailType;
import com.mlsoft.backend.domain.email.repository.EmailHistoryRepository;
import com.mlsoft.backend.domain.leave.entity.LeaveActionHistory;
import com.mlsoft.backend.domain.leave.entity.LeaveRequest;
import com.mlsoft.backend.domain.leave.entity.LeaveType;
import com.mlsoft.backend.domain.leave.repository.LeaveActionHistoryRepository;
import com.mlsoft.backend.domain.leave.repository.LeaveRequestRepository;
import com.mlsoft.backend.domain.user.entity.Role;
import com.mlsoft.backend.domain.user.entity.User;
import com.mlsoft.backend.domain.user.repository.UserRepository;
import com.mlsoft.backend.domain.welfare.entity.WelfareActionHistory;
import com.mlsoft.backend.domain.welfare.entity.WelfarePolicy;
import com.mlsoft.backend.domain.welfare.entity.WelfareRequest;
import com.mlsoft.backend.domain.welfare.entity.WelfareTarget;
import com.mlsoft.backend.domain.welfare.repository.WelfareActionHistoryRepository;
import com.mlsoft.backend.domain.welfare.repository.WelfarePolicyRepository;
import com.mlsoft.backend.domain.welfare.repository.WelfareRequestRepository;
import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** 실제 DB에서 익명화 벌크 UPDATE와 감사 로그 보존을 검증한다. */
@SpringBootTest
@ActiveProfiles("test")
class UserPurgeIntegrationTest {

    @Autowired
    private UserService userService;
    @Autowired
    private UserRepository userRepository;
    @Autowired
    private LeaveRequestRepository leaveRequestRepository;
    @Autowired
    private LeaveActionHistoryRepository leaveActionHistoryRepository;
    @Autowired
    private WelfarePolicyRepository welfarePolicyRepository;
    @Autowired
    private WelfareRequestRepository welfareRequestRepository;
    @Autowired
    private WelfareActionHistoryRepository welfareActionHistoryRepository;
    @Autowired
    private EmailHistoryRepository emailHistoryRepository;
    @Autowired
    private AdminAuditLogRepository adminAuditLogRepository;
    @Autowired
    private EntityManager entityManager;

    @Test
    @DisplayName("파기 트랜잭션 — users·신청 사유·이력 코멘트·수신 메일은 익명화되고 감사 로그는 남는다")
    void purge_anonymizesFreeTextAndKeepsAudit() {
        User actor = saveUser("파기 관리자", Role.SYSTEM_ADMIN);
        User target = saveRetiredUser();

        LeaveRequest leave = leaveRequestRepository.save(LeaveRequest.create(
                target, LeaveType.ANNUAL, List.of(LocalDate.now().plusDays(1)),
                "개인적인 연차 사유", actor, null));
        LeaveActionHistory leaveHistory = leaveActionHistoryRepository.save(LeaveActionHistory.create(
                leave, actor, RequestAction.REJECTED, "개인 사정이라 반려"));

        WelfarePolicy policy = welfarePolicyRepository.save(WelfarePolicy.create(
                "경조", WelfareTarget.PARENT, new BigDecimal("1.0"), "증빙", "설명"));
        WelfareRequest welfare = welfareRequestRepository.save(WelfareRequest.create(
                policy, target, "가족 관련 복리후생 사유", actor, null));
        WelfareActionHistory welfareHistory = welfareActionHistoryRepository.save(WelfareActionHistory.create(
                welfare, actor, RequestAction.REJECTED, "민감한 처리 코멘트"));

        EmailHistory email = emailHistoryRepository.save(EmailHistory.create(
                target, actor, EmailType.LEAVE, "메일 제목 속 이름", "메일 본문 속 사유"));
        Long targetId = target.getId();

        userService.purge(targetId, actor.getId());
        entityManager.clear();

        User reloaded = userRepository.findById(targetId).orElseThrow();
        assertEquals("퇴직사원#" + targetId, reloaded.getName());
        assertEquals("deleted-" + targetId + "@invalid", reloaded.getEmail());
        assertNull(reloaded.getBirthDay());
        assertNull(reloaded.getHireDate());
        assertNull(reloaded.getPosition());
        assertTrue(reloaded.getPurgedAt() != null);
        assertEquals(Role.EMPLOYEE, reloaded.getRole());
        assertEquals(new BigDecimal("10.0"), reloaded.getBaseDays());

        assertNull(leaveRequestRepository.findById(leave.getId()).orElseThrow().getRequestReason());
        assertNull(leaveActionHistoryRepository.findById(leaveHistory.getId()).orElseThrow().getComment());
        assertNull(welfareRequestRepository.findById(welfare.getId()).orElseThrow().getReason());
        assertNull(welfareActionHistoryRepository.findById(welfareHistory.getId()).orElseThrow().getComment());
        assertNull(emailHistoryRepository.findById(email.getId()).orElseThrow().getTitle());
        assertNull(emailHistoryRepository.findById(email.getId()).orElseThrow().getContent());

        List<AdminAuditLog> audits = adminAuditLogRepository
                .search(AdminAction.USER_PURGED, targetId, org.springframework.data.domain.PageRequest.of(0, 20))
                .getContent();
        assertEquals(1, audits.size());
        assertEquals("user_id=" + targetId, audits.get(0).getTargetLabel());
        assertNull(audits.get(0).getBeforeValue());
        assertTrue(!audits.get(0).getTargetLabel().contains("파기 전 이름"));
    }

    private User saveRetiredUser() {
        User target = User.builder()
                .name("파기 전 이름")
                .email("purge-target-" + System.nanoTime() + "@integration.test")
                .birthDay(LocalDate.of(1990, 1, 1))
                .hireDate(LocalDate.of(2018, 1, 1))
                .position("선임")
                .role(Role.EMPLOYEE)
                .baseDays(new BigDecimal("10.0"))
                .useDays(new BigDecimal("2.0"))
                .bonusDays(new BigDecimal("1.0"))
                .advanceDays(new BigDecimal("0.0"))
                .isActive(false)
                .retiredAt(LocalDate.now().minusYears(4))
                .build();
        return userRepository.saveAndFlush(target);
    }

    private User saveUser(String name, Role role) {
        return userRepository.saveAndFlush(User.builder()
                .name(name)
                .email("purge-actor-" + System.nanoTime() + "@integration.test")
                .role(role)
                .baseDays(new BigDecimal("15.0"))
                .useDays(BigDecimal.ZERO)
                .bonusDays(BigDecimal.ZERO)
                .advanceDays(BigDecimal.ZERO)
                .isActive(true)
                .build());
    }
}
