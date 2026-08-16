package com.mlsoft.backend.domain.user.service;

import com.mlsoft.backend.domain.department.entity.Department;
import com.mlsoft.backend.domain.department.repository.DepartmentRepository;
import com.mlsoft.backend.domain.user.entity.OnboardingStatus;
import com.mlsoft.backend.domain.user.entity.Role;
import com.mlsoft.backend.domain.user.entity.User;
import com.mlsoft.backend.domain.user.repository.UserRepository;
import com.mlsoft.backend.global.exception.BusinessException;
import com.mlsoft.backend.global.exception.ErrorCode;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

/**
 * 승인자 결정 규칙 단위 테스트 (리뷰 I-5).
 * <p>
 * 예전에는 이 규칙이 LeaveService·WelfareService에 복사돼 있어 한쪽만 고쳐지기 쉬웠다.
 * 규칙이 한 곳에 모인 지금은 이 테스트가 그 규칙의 유일한 명세다.
 */
@ExtendWith(MockitoExtension.class)
class ApproverResolverTest {

    /** 기본 승인자 — 중복 검사와 무관한 테스트에서 서브와 다른 사람임을 명시한다 (리뷰 I-7) */
    private static final User OTHER_PRIMARY = user(9L, Role.TEAM_LEADER, true, true);

    @Mock
    private UserRepository userRepository;
    // 상위 부서로 올라갈 때만 쓰인다 (2026-08-16)
    @Mock
    private DepartmentRepository departmentRepository;

    @InjectMocks
    private ApproverResolver approverResolver;

    // ==== primary ====

    @Test
    @DisplayName("기본 승인자 — 부서 팀장이 자격을 갖추면 팀장이 지정된다")
    void resolvePrimary_eligibleLeader() {
        User leader = user(2L, Role.TEAM_LEADER, true, true);
        User applicant = userInDepartment(1L, leader);

        assertEquals(leader, approverResolver.resolvePrimary(applicant));
    }

    // ── 상위 부서 승격 (2026-08-16) ──────────────────────────────────────────
    // 예전에는 자기 부서 팀장이 없으면 곧바로 SYSTEM_ADMIN으로 갔다. 조직도상 바로 위에
    // 본부장이 있는데 시스템만 모르는 상태였다.

    @Test
    @DisplayName("기본 승인자 — 하위 부서가 공석이면 상위 부서 팀장이 지정된다")
    void resolvePrimary_공석이면_상위부서팀장() {
        User parentLeader = user(3L, Role.TEAM_LEADER, true, true);
        User applicant = userInNestedDepartment(1L, null, parentLeader);

        assertEquals(parentLeader, approverResolver.resolvePrimary(applicant));
    }

    @Test
    @DisplayName("기본 승인자 — 하위 부서에 팀장이 있으면 상위로 올라가지 않는다")
    void resolvePrimary_하위팀장있으면_그대로() {
        User leader = user(2L, Role.TEAM_LEADER, true, true);
        User parentLeader = user(3L, Role.TEAM_LEADER, true, true);
        User applicant = userInNestedDepartment(1L, leader, parentLeader);

        assertEquals(leader, approverResolver.resolvePrimary(applicant));
        verify(departmentRepository, never()).findById(any());
    }

    @Test
    @DisplayName("기본 승인자 — 하위 팀장이 자격을 잃었으면 상위 부서 팀장으로 올라간다")
    void resolvePrimary_하위팀장_자격미달_상위로() {
        // 팀장으로 지정돼 있지만 강등된 사람 — 결재할 수 없다 (리뷰 I-5a)
        User demotedLeader = user(2L, Role.EMPLOYEE, true, true);
        User parentLeader = user(3L, Role.TEAM_LEADER, true, true);
        User applicant = userInNestedDepartment(1L, demotedLeader, parentLeader);

        assertEquals(parentLeader, approverResolver.resolvePrimary(applicant));
    }

    @Test
    @DisplayName("기본 승인자 — 상위 부서 팀장이 신청자 본인이면 건너뛰고 SYSTEM_ADMIN으로 간다")
    void resolvePrimary_상위팀장이본인_fallback() {
        User applicant = user(3L, Role.TEAM_LEADER, true, true);
        User fallbackAdmin = user(9L, Role.SYSTEM_ADMIN, true, true);
        Department parent = Department.builder()
                .id(100L).name("개발본부").description("본부").active(true).build();
        parent.assignLeader(applicant); // 본부장이 자기 신청을 올린 경우
        Department child = Department.builder()
                .id(101L).name("개발 1팀").description("팀").active(true).parentId(100L).build();
        applicant.assignDepartment(child);
        given(departmentRepository.findById(100L)).willReturn(Optional.of(parent));
        given(userRepository.findFirstByRoleAndIsActiveTrueAndOnboardingStatusAndIdNotOrderByIdAsc(
                Role.SYSTEM_ADMIN, OnboardingStatus.COMPLETED, applicant.getId()))
                .willReturn(Optional.of(fallbackAdmin));

        // 셀프 결재는 어느 단계에서도 허용하지 않는다
        assertEquals(fallbackAdmin, approverResolver.resolvePrimary(applicant));
    }

    // @Timeout이 이 테스트의 핵심이다. 순환 가드를 지우면 이 메서드는 실패가 아니라
    // **영원히 매달린다** — 시간 제한이 없으면 빌드가 멈춘 것처럼 보이고 원인도 안 보인다.
    // (2026-08-16 뮤테이션 검증 중 실제로 6분 40초를 태우고서야 알았다)
    //
    // SEPARATE_THREAD여야 한다. 기본값(SAME_THREAD)은 테스트가 **끝난 뒤에** 경과 시간을 볼 뿐이라
    // 끼어들 수 없는 CPU 루프는 못 끊는다 — 그 모드로 붙였다가 또 타임아웃을 태웠다.
    @Test
    @Timeout(value = 5, threadMode = Timeout.ThreadMode.SEPARATE_THREAD)
    @DisplayName("기본 승인자 — 부서 계층이 순환이어도 무한 루프에 빠지지 않는다")
    void resolvePrimary_순환계층_중단() {
        User fallbackAdmin = user(9L, Role.SYSTEM_ADMIN, true, true);
        // DB를 직접 고쳐야 만들 수 있는 상태다. 그래도 신청 한 건이 스레드를 물면 안 된다
        Department a = Department.builder()
                .id(200L).name("A").description("A").active(true).parentId(201L).build();
        Department b = Department.builder()
                .id(201L).name("B").description("B").active(true).parentId(200L).build();
        given(departmentRepository.findById(201L)).willReturn(Optional.of(b));
        given(departmentRepository.findById(200L)).willReturn(Optional.of(a));

        User applicant = user(1L, Role.EMPLOYEE, true, true);
        applicant.assignDepartment(a);
        given(userRepository.findFirstByRoleAndIsActiveTrueAndOnboardingStatusAndIdNotOrderByIdAsc(
                Role.SYSTEM_ADMIN, OnboardingStatus.COMPLETED, applicant.getId()))
                .willReturn(Optional.of(fallbackAdmin));

        assertEquals(fallbackAdmin, approverResolver.resolvePrimary(applicant));
    }

    @Test
    @DisplayName("기본 승인자 — 팀장이 EMPLOYEE로 강등됐으면 fallback (I-5a: 영구 PENDING 방지)")
    void resolvePrimary_demotedLeader_fallsBack() {
        User demoted = user(2L, Role.EMPLOYEE, true, true); // 강등됐지만 leader_id는 그대로
        User applicant = userInDepartment(1L, demoted);
        User admin = user(9L, Role.SYSTEM_ADMIN, true, true);
        givenFallback(1L, admin);

        assertEquals(admin, approverResolver.resolvePrimary(applicant));
    }

    @Test
    @DisplayName("기본 승인자 — 팀장이 퇴직했으면 fallback")
    void resolvePrimary_retiredLeader_fallsBack() {
        User retired = user(2L, Role.TEAM_LEADER, false, true);
        User applicant = userInDepartment(1L, retired);
        User admin = user(9L, Role.SYSTEM_ADMIN, true, true);
        givenFallback(1L, admin);

        assertEquals(admin, approverResolver.resolvePrimary(applicant));
    }

    @Test
    @DisplayName("기본 승인자 — 팀장이 온보딩 미완료면 fallback (I-5b: 인터셉터가 막아 결재 불가)")
    void resolvePrimary_leaderNotOnboarded_fallsBack() {
        User notOnboarded = user(2L, Role.TEAM_LEADER, true, false);
        User applicant = userInDepartment(1L, notOnboarded);
        User admin = user(9L, Role.SYSTEM_ADMIN, true, true);
        givenFallback(1L, admin);

        assertEquals(admin, approverResolver.resolvePrimary(applicant));
    }

    @Test
    @DisplayName("기본 승인자 — 신청자가 그 부서 팀장 본인이면 fallback (셀프 결재 방지)")
    void resolvePrimary_applicantIsLeader_fallsBack() {
        User leader = user(1L, Role.TEAM_LEADER, true, true);
        User applicant = userInDepartment(1L, leader); // 팀장 == 신청자
        User admin = user(9L, Role.SYSTEM_ADMIN, true, true);
        givenFallback(1L, admin);

        assertEquals(admin, approverResolver.resolvePrimary(applicant));
    }

    @Test
    @DisplayName("기본 승인자 — fallback 조회는 신청자 본인을 제외한다 (I-5c: SA 셀프 결재 방지)")
    void resolvePrimary_fallbackExcludesApplicant() {
        User applicant = user(9L, Role.SYSTEM_ADMIN, true, true); // 본인이 첫 SA
        User otherAdmin = user(10L, Role.SYSTEM_ADMIN, true, true);
        givenFallback(9L, otherAdmin);

        assertEquals(otherAdmin, approverResolver.resolvePrimary(applicant));
    }

    @Test
    @DisplayName("기본 승인자 — 팀장도 fallback도 없으면 INVALID_APPROVER")
    void resolvePrimary_noApprover_throws() {
        User applicant = user(1L, Role.EMPLOYEE, true, true);
        given(userRepository.findFirstByRoleAndIsActiveTrueAndOnboardingStatusAndIdNotOrderByIdAsc(
                eq(Role.SYSTEM_ADMIN), eq(OnboardingStatus.COMPLETED), any())).willReturn(Optional.empty());

        BusinessException e = assertThrows(BusinessException.class,
                () -> approverResolver.resolvePrimary(applicant));
        assertEquals(ErrorCode.INVALID_APPROVER, e.getErrorCode());
    }

    // ==== sub ====

    @Test
    @DisplayName("서브 승인자 — 미지정이면 null (선택 항목)")
    void resolveSub_null_returnsNull() {
        assertNull(approverResolver.resolveSub(null, user(1L, Role.EMPLOYEE, true, true), OTHER_PRIMARY));
    }

    @Test
    @DisplayName("서브 승인자 — EMPLOYEE는 지정할 수 없다")
    void resolveSub_employee_throws() {
        User applicant = user(1L, Role.EMPLOYEE, true, true);
        given(userRepository.findById(5L)).willReturn(Optional.of(user(5L, Role.EMPLOYEE, true, true)));

        BusinessException e = assertThrows(BusinessException.class,
                () -> approverResolver.resolveSub(5L, applicant, OTHER_PRIMARY));
        assertEquals(ErrorCode.INVALID_APPROVER, e.getErrorCode());
    }

    @Test
    @DisplayName("서브 승인자 — 온보딩 미완료자는 지정할 수 없다 (I-5b)")
    void resolveSub_notOnboarded_throws() {
        User applicant = user(1L, Role.EMPLOYEE, true, true);
        given(userRepository.findById(5L)).willReturn(Optional.of(user(5L, Role.TEAM_LEADER, true, false)));

        assertThrows(BusinessException.class, () -> approverResolver.resolveSub(5L, applicant, OTHER_PRIMARY));
    }

    @Test
    @DisplayName("서브 승인자 — 본인은 지정할 수 없다")
    void resolveSub_self_throws() {
        User applicant = user(1L, Role.TEAM_LEADER, true, true);
        given(userRepository.findById(1L)).willReturn(Optional.of(applicant));

        assertThrows(BusinessException.class, () -> approverResolver.resolveSub(1L, applicant, OTHER_PRIMARY));
    }

    @Test
    @DisplayName("서브 승인자 — 기본 승인자와 같은 사람이면 DUPLICATE_APPROVER (리뷰 I-7)")
    void resolveSub_sameAsPrimary_throws() {
        // 서브 승인자의 목적은 병렬 선착순이다. 같은 사람이 양쪽에 들어가면 1인 결재로 축퇴하는데,
        // 화면에는 승인자가 둘로 보여 그 사람이 부재할 때 대안이 없다는 것을 알 수 없다.
        User applicant = user(1L, Role.EMPLOYEE, true, true);
        User primary = user(5L, Role.TEAM_LEADER, true, true);

        BusinessException e = assertThrows(BusinessException.class,
                () -> approverResolver.resolveSub(5L, applicant, primary));

        assertEquals(ErrorCode.DUPLICATE_APPROVER, e.getErrorCode());
        // 중복은 DB를 보기 전에 걸러진다 — 어차피 거부될 요청으로 조회를 태우지 않는다
        verify(userRepository, never()).findById(any());
    }

    @Test
    @DisplayName("서브 승인자 — 기본 승인자가 아직 없으면(null) 중복 검사를 건너뛴다")
    void resolveSub_nullPrimary_skipsDuplicateCheck() {
        User applicant = user(1L, Role.EMPLOYEE, true, true);
        User sub = user(5L, Role.TEAM_LEADER, true, true);
        given(userRepository.findById(5L)).willReturn(Optional.of(sub));

        assertEquals(sub, approverResolver.resolveSub(5L, applicant, null));
    }

    @Test
    @DisplayName("서브 승인자 — 자격을 갖춘 팀장은 그대로 지정된다")
    void resolveSub_eligible_returns() {
        User applicant = user(1L, Role.EMPLOYEE, true, true);
        User sub = user(5L, Role.TEAM_LEADER, true, true);
        given(userRepository.findById(5L)).willReturn(Optional.of(sub));

        assertEquals(sub, approverResolver.resolveSub(5L, applicant, OTHER_PRIMARY));
    }

    // ==== canApprove (팀장 지정 검증에서 쓴다) ====

    @Test
    @DisplayName("자격 판정 — 셀프 배제 없이 재직·역할·온보딩만 본다")
    void canApprove_checksOnlyEligibility() {
        assertTrue(approverResolver.canApprove(user(1L, Role.TEAM_LEADER, true, true)));
        assertTrue(approverResolver.canApprove(user(1L, Role.SYSTEM_ADMIN, true, true)));
        assertFalse(approverResolver.canApprove(user(1L, Role.EMPLOYEE, true, true)));
        assertFalse(approverResolver.canApprove(user(1L, Role.TEAM_LEADER, false, true)));
        assertFalse(approverResolver.canApprove(user(1L, Role.TEAM_LEADER, true, false)));
        assertFalse(approverResolver.canApprove(null));
    }

    // ==== 헬퍼 ====

    private void givenFallback(Long applicantId, User admin) {
        given(userRepository.findFirstByRoleAndIsActiveTrueAndOnboardingStatusAndIdNotOrderByIdAsc(
                Role.SYSTEM_ADMIN, OnboardingStatus.COMPLETED, applicantId)).willReturn(Optional.of(admin));
    }

    private static User user(Long id, Role role, boolean active, boolean onboarded) {
        return User.builder()
                .id(id)
                .name("사용자" + id)
                .email("user" + id + "@mlsoft.com")
                .role(role)
                .isActive(active)
                // 온보딩 판별은 hire_date가 아니라 상태다 (리뷰 S-1) — 둘을 함께 세워 실제 데이터와 같은 조합을 만든다
                .onboardingStatus(onboarded ? OnboardingStatus.COMPLETED : OnboardingStatus.NOT_STARTED)
                .hireDate(onboarded ? LocalDate.of(2020, 1, 1) : null)
                .baseDays(new BigDecimal("15.0"))
                .useDays(BigDecimal.ZERO)
                .bonusDays(BigDecimal.ZERO)
                .advanceDays(BigDecimal.ZERO)
                .build();
    }

    /** 지정한 팀장을 가진 부서에 속한 신청자 */
    /**
     * 하위 부서(팀장 leader) 소속 사원. 상위 부서는 parentLeader가 팀장이다.
     * leader에 null을 주면 하위 부서는 공석이 된다.
     */
    private User userInNestedDepartment(Long applicantId, User leader, User parentLeader) {
        Department parent = Department.builder()
                .id(100L).name("개발본부").description("본부").active(true).build();
        if (parentLeader != null) {
            parent.assignLeader(parentLeader);
        }
        Department child = Department.builder()
                .id(101L).name("개발 1팀").description("팀").active(true).parentId(100L).build();
        if (leader != null) {
            child.assignLeader(leader);
        }
        // lenient — 하위 팀장이 있는 경우엔 이 조회가 일어나지 않는 것이 정상이고,
        // 그 "일어나지 않음"은 해당 테스트의 verify(never())가 직접 단정한다
        lenient().when(departmentRepository.findById(100L)).thenReturn(Optional.of(parent));

        User applicant = user(applicantId, Role.EMPLOYEE, true, true);
        applicant.assignDepartment(child);
        return applicant;
    }

    private static User userInDepartment(Long applicantId, User leader) {
        Department department = Department.builder()
                .id(10L).name("개발팀").description("설명").active(true).build();
        department.assignLeader(leader);
        User applicant = user(applicantId, Role.EMPLOYEE, true, true);
        applicant.assignDepartment(department);
        return applicant;
    }
}
