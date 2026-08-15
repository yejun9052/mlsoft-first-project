package com.mlsoft.backend.domain.user.repository;

import com.mlsoft.backend.domain.user.entity.OnboardingStatus;
import com.mlsoft.backend.domain.user.entity.Role;
import com.mlsoft.backend.domain.user.entity.User;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 팀장 후보 조회를 DB로 고정한다 (2026-08-15 부서 관리 400).
 *
 * <p><b>왜 통합 테스트인가</b> — 후보를 고르는 주체가 서비스 코드가 아니라 파생 쿼리 이름이다.
 * 리포지토리를 목으로 두면 조건이 어긋나도 테스트가 통과한다.
 *
 * <p><b>무엇을 지키는가</b> — 이 목록은 서버가 팀장 지정을 검증하는 기준
 * ({@code ApproverResolver.canApprove})과 <b>같은 집합</b>이어야 한다. 넓으면 화면에서 고른
 * 사람이 저장 단계에서 거부되고, 좁으면 지정할 수 있는 사람이 화면에 안 보인다.
 *
 * <p>승인자 후보({@code findBy...AndIdNot})와 갈라지는 지점은 <b>본인 포함 여부</b> 하나뿐이라
 * 둘을 나란히 확인한다 — 나중에 누가 "중복이네" 하고 합치면 관리자가 자기 부서의 팀장이 될 수 없다.
 */
@SpringBootTest
@ActiveProfiles("test")
@Transactional
class LeaderCandidateQueryIntegrationTest {

    private static final List<Role> APPROVER_ROLES = List.of(Role.TEAM_LEADER, Role.SYSTEM_ADMIN);

    @Autowired
    private UserRepository userRepository;

    @Test
    @DisplayName("결재 자격이 있는 재직자만 후보다 — EMPLOYEE·퇴직자·온보딩 미확정은 빠진다")
    void 후보는_결재자격자만() {
        User 팀장 = save("팀장", Role.TEAM_LEADER, true, OnboardingStatus.COMPLETED);
        User 관리자 = save("관리자", Role.SYSTEM_ADMIN, true, OnboardingStatus.COMPLETED);
        User 사원 = save("사원", Role.EMPLOYEE, true, OnboardingStatus.COMPLETED);
        User 퇴직팀장 = save("퇴직팀장", Role.TEAM_LEADER, false, OnboardingStatus.COMPLETED);
        User 승인대기팀장 = save("승인대기팀장", Role.TEAM_LEADER, true, OnboardingStatus.PENDING_APPROVAL);
        User 온보딩전관리자 = save("온보딩전관리자", Role.SYSTEM_ADMIN, true, OnboardingStatus.NOT_STARTED);

        List<Long> 후보 = ids(findCandidates());

        assertTrue(후보.contains(팀장.getId()));
        assertTrue(후보.contains(관리자.getId()));

        assertFalse(후보.contains(사원.getId()), "EMPLOYEE는 결재할 수 없으므로 팀장이 될 수 없다");
        assertFalse(후보.contains(퇴직팀장.getId()), "퇴직자를 팀장으로 두면 그 부서 결재가 멈춘다");
        assertFalse(후보.contains(승인대기팀장.getId()),
                "온보딩 미확정자는 인터셉터가 막아 결재를 못 한다 (리뷰 S-1)");
        assertFalse(후보.contains(온보딩전관리자.getId()));
    }

    /**
     * 이 테스트가 이 파일의 존재 이유다.
     *
     * <p>승인자 후보는 셀프 결재를 막으려고 본인을 뺀다. 팀장 지정은 신청자가 정해지지 않은
     * 시점의 조작이라 그 규칙이 적용되지 않는다 — 관리자가 자기 부서의 팀장을 겸하는 것은 정상이다.
     */
    @Test
    @DisplayName("팀장 후보에는 본인이 포함된다 — 승인자 후보와 갈라지는 유일한 지점")
    void 팀장후보는_본인을_제외하지_않는다() {
        User 나 = save("나", Role.SYSTEM_ADMIN, true, OnboardingStatus.COMPLETED);
        User 동료팀장 = save("동료팀장", Role.TEAM_LEADER, true, OnboardingStatus.COMPLETED);

        List<Long> 팀장후보 = ids(findCandidates());
        List<Long> 승인자후보 = ids(userRepository.findByRoleInAndIsActiveTrueAndOnboardingStatusAndIdNot(
                APPROVER_ROLES, OnboardingStatus.COMPLETED, 나.getId()));

        assertTrue(팀장후보.contains(나.getId()),
                "본인을 빼면 관리자가 자기 부서의 팀장이 될 수 없다");
        assertFalse(승인자후보.contains(나.getId()),
                "승인자 후보는 반대로 본인을 빼야 한다 (셀프 결재 방지)");

        assertTrue(팀장후보.contains(동료팀장.getId()));
        assertTrue(승인자후보.contains(동료팀장.getId()));

        // 두 목록의 차이는 정확히 본인 한 명이어야 한다 — 조건이 하나라도 더 갈라지면 여기서 깨진다
        assertEquals(
                Stream.concat(승인자후보.stream(), Stream.of(나.getId())).sorted().toList(),
                팀장후보.stream().sorted().toList());
    }

    // ---------------------------------------------------------------------

    private List<User> findCandidates() {
        return userRepository.findByRoleInAndIsActiveTrueAndOnboardingStatus(
                APPROVER_ROLES, OnboardingStatus.COMPLETED);
    }

    private List<Long> ids(List<User> users) {
        return users.stream().map(User::getId).toList();
    }

    private User save(String name, Role role, boolean active, OnboardingStatus status) {
        return userRepository.saveAndFlush(User.builder()
                .name(name)
                .email(name + "@mlsoft.com")
                .role(role)
                .hireDate(status == OnboardingStatus.NOT_STARTED ? null : LocalDate.of(2024, 1, 2))
                .onboardingStatus(status)
                .baseDays(new BigDecimal("15.0"))
                .bonusDays(BigDecimal.ZERO)
                .useDays(BigDecimal.ZERO)
                .advanceDays(BigDecimal.ZERO)
                .isActive(active)
                .build());
    }
}
