package com.mlsoft.backend.domain.user.service;

import com.mlsoft.backend.domain.audit.entity.AdminAction;
import com.mlsoft.backend.domain.audit.service.AdminAuditService;
import com.mlsoft.backend.domain.common.RequestStatus;
import com.mlsoft.backend.domain.department.entity.Department;
import com.mlsoft.backend.domain.department.repository.DepartmentRepository;
import com.mlsoft.backend.domain.leave.entity.LeaveRequest;
import com.mlsoft.backend.domain.leave.entity.LeaveType;
import com.mlsoft.backend.domain.leave.repository.LeaveRequestRepository;
import com.mlsoft.backend.domain.user.dto.BaseDaysUpdateRequest;
import com.mlsoft.backend.domain.user.entity.OnboardingStatus;
import com.mlsoft.backend.domain.user.entity.Role;
import com.mlsoft.backend.domain.user.entity.User;
import com.mlsoft.backend.domain.user.repository.UserRepository;
import com.mlsoft.backend.domain.welfare.entity.WelfarePolicy;
import com.mlsoft.backend.domain.welfare.entity.WelfareRequest;
import com.mlsoft.backend.domain.welfare.entity.WelfareTarget;
import com.mlsoft.backend.domain.welfare.repository.WelfareRequestRepository;
import com.mlsoft.backend.global.exception.BusinessException;
import com.mlsoft.backend.global.exception.ErrorCode;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

/**
 * 사용자 서비스 단위 테스트 — 조회·프로필/관리자 수정, 특히 퇴직 처리의 결재 이관 로직 (docs/01 2-9).
 * 퇴직 처리는 이 도메인에서 가장 버그 나기 쉬운 지점이라 이관 대상 유무·상태 필터·fallback 부재를 전부 커버한다.
 */
@ExtendWith(MockitoExtension.class)
class UserServiceTest {

    private static final List<RequestStatus> LEAVE_REASSIGN_STATUSES =
            List.of(RequestStatus.PENDING, RequestStatus.CANCEL_PENDING);

    /** 조작한 관리자 id — 감사 기록의 actor (리뷰 S-3) */
    private static final Long ACTOR_ID = 99L;

    @Mock
    private UserRepository userRepository;
    @Mock
    private DepartmentRepository departmentRepository;
    @Mock
    private LeaveRequestRepository leaveRequestRepository;
    @Mock
    private WelfareRequestRepository welfareRequestRepository;
    @Mock
    private AdminAuditService adminAuditService;

    @InjectMocks
    private UserService userService;

    // ============================ 퇴직 처리 — 결재 이관 ============================

    @Test
    @DisplayName("퇴직 — 대상이 부서 팀장: 해당 부서 leader 해제")
    void retire_clearsLeaderOfOwnedDepartments() {
        User target = activeUser(1L, Role.TEAM_LEADER);
        Department department = Department.create("개발팀", "설명", null);
        department.assignLeader(target);
        given(userRepository.findById(1L)).willReturn(Optional.of(target));
        given(departmentRepository.findByLeader(target)).willReturn(List.of(department));
        givenNoReassignTargets(target);

        userService.retire(1L, ACTOR_ID);

        assertNull(department.getLeader());
        assertFalse(target.isActive());
    }

    @Test
    @DisplayName("퇴직 — primary 승인자로 걸린 PENDING LeaveRequest: fallback SA로 재배정")
    void retire_reassignsLeavePrimaryApprover() {
        User target = activeUser(1L, Role.TEAM_LEADER);
        User applicant = activeUser(2L, Role.EMPLOYEE);
        User fallback = activeUser(9L, Role.SYSTEM_ADMIN);
        LeaveRequest leave = pendingLeave(applicant, target, null);
        given(userRepository.findById(1L)).willReturn(Optional.of(target));
        given(departmentRepository.findByLeader(target)).willReturn(List.of());
        given(leaveRequestRepository.findByPrimaryApproverAndStatusIn(target, LEAVE_REASSIGN_STATUSES))
                .willReturn(List.of(leave));
        given(leaveRequestRepository.findBySubApproverAndStatusIn(target, LEAVE_REASSIGN_STATUSES))
                .willReturn(List.of());
        given(welfareRequestRepository.findByPrimaryApproverAndStatus(target, RequestStatus.PENDING))
                .willReturn(List.of());
        given(welfareRequestRepository.findBySubApproverAndStatus(target, RequestStatus.PENDING))
                .willReturn(List.of());
        given(userRepository.findFirstByRoleAndIsActiveTrueOrderByIdAsc(Role.SYSTEM_ADMIN))
                .willReturn(Optional.of(fallback));

        userService.retire(1L, ACTOR_ID);

        assertEquals(fallback, leave.getPrimaryApprover());
    }

    @Test
    @DisplayName("퇴직 — sub 승인자로 걸린 CANCEL_PENDING LeaveRequest: fallback SA로 재배정")
    void retire_reassignsLeaveSubApprover() {
        User target = activeUser(1L, Role.TEAM_LEADER);
        User applicant = activeUser(2L, Role.EMPLOYEE);
        User otherPrimary = activeUser(3L, Role.TEAM_LEADER);
        User fallback = activeUser(9L, Role.SYSTEM_ADMIN);
        LeaveRequest leave = pendingLeave(applicant, otherPrimary, target);
        leave.approve();
        leave.requestCancel("소급 취소");
        assertEquals(RequestStatus.CANCEL_PENDING, leave.getStatus());

        given(userRepository.findById(1L)).willReturn(Optional.of(target));
        given(departmentRepository.findByLeader(target)).willReturn(List.of());
        given(leaveRequestRepository.findByPrimaryApproverAndStatusIn(target, LEAVE_REASSIGN_STATUSES))
                .willReturn(List.of());
        given(leaveRequestRepository.findBySubApproverAndStatusIn(target, LEAVE_REASSIGN_STATUSES))
                .willReturn(List.of(leave));
        given(welfareRequestRepository.findByPrimaryApproverAndStatus(target, RequestStatus.PENDING))
                .willReturn(List.of());
        given(welfareRequestRepository.findBySubApproverAndStatus(target, RequestStatus.PENDING))
                .willReturn(List.of());
        given(userRepository.findFirstByRoleAndIsActiveTrueOrderByIdAsc(Role.SYSTEM_ADMIN))
                .willReturn(Optional.of(fallback));

        userService.retire(1L, ACTOR_ID);

        assertEquals(fallback, leave.getSubApprover());
    }

    @Test
    @DisplayName("퇴직 — primary 승인자로 걸린 PENDING WelfareRequest: fallback SA로 재배정")
    void retire_reassignsWelfarePrimaryApprover() {
        User target = activeUser(1L, Role.TEAM_LEADER);
        User applicant = activeUser(2L, Role.EMPLOYEE);
        User fallback = activeUser(9L, Role.SYSTEM_ADMIN);
        WelfarePolicy policy = WelfarePolicy.create("결혼", WelfareTarget.SELF, new BigDecimal("7.0"), "증빙", "설명");
        WelfareRequest welfare = WelfareRequest.create(policy, applicant, "사유", target, null);

        given(userRepository.findById(1L)).willReturn(Optional.of(target));
        given(departmentRepository.findByLeader(target)).willReturn(List.of());
        given(leaveRequestRepository.findByPrimaryApproverAndStatusIn(target, LEAVE_REASSIGN_STATUSES))
                .willReturn(List.of());
        given(leaveRequestRepository.findBySubApproverAndStatusIn(target, LEAVE_REASSIGN_STATUSES))
                .willReturn(List.of());
        given(welfareRequestRepository.findByPrimaryApproverAndStatus(target, RequestStatus.PENDING))
                .willReturn(List.of(welfare));
        given(welfareRequestRepository.findBySubApproverAndStatus(target, RequestStatus.PENDING))
                .willReturn(List.of());
        given(userRepository.findFirstByRoleAndIsActiveTrueOrderByIdAsc(Role.SYSTEM_ADMIN))
                .willReturn(Optional.of(fallback));

        userService.retire(1L, ACTOR_ID);

        // 연차와 같은 방식으로 엔티티가 들어간다 (리뷰 D-5 — 예전에는 id만 보관했다)
        assertEquals(fallback, welfare.getPrimaryApprover());
    }

    @Test
    @DisplayName("퇴직 — 이관 대상 조회는 PENDING·CANCEL_PENDING 상태 목록으로만 호출된다 (APPROVED 등은 자동 제외)")
    void retire_queriesOnlyReassignableStatuses() {
        User target = activeUser(1L, Role.TEAM_LEADER);
        given(userRepository.findById(1L)).willReturn(Optional.of(target));
        given(departmentRepository.findByLeader(target)).willReturn(List.of());
        givenNoReassignTargets(target);

        userService.retire(1L, ACTOR_ID);

        verify(leaveRequestRepository).findByPrimaryApproverAndStatusIn(eq(target), eq(LEAVE_REASSIGN_STATUSES));
        verify(leaveRequestRepository).findBySubApproverAndStatusIn(eq(target), eq(LEAVE_REASSIGN_STATUSES));
        verify(welfareRequestRepository).findByPrimaryApproverAndStatus(target, RequestStatus.PENDING);
        verify(welfareRequestRepository).findBySubApproverAndStatus(target, RequestStatus.PENDING);
    }

    @Test
    @DisplayName("퇴직 — 이미 퇴직 처리된 사용자: ALREADY_RETIRED, 이관 로직은 전혀 호출되지 않음")
    void retire_alreadyRetired_throwsAndSkipsReassignment() {
        User target = retiredUser(1L);
        given(userRepository.findById(1L)).willReturn(Optional.of(target));

        BusinessException ex = assertThrows(BusinessException.class, () -> userService.retire(1L, ACTOR_ID));

        assertEquals(ErrorCode.ALREADY_RETIRED, ex.getErrorCode());
        verify(departmentRepository, never()).findByLeader(any());
        verify(leaveRequestRepository, never()).findByPrimaryApproverAndStatusIn(any(), any());
        verify(leaveRequestRepository, never()).findBySubApproverAndStatusIn(any(), any());
        verify(welfareRequestRepository, never()).findByPrimaryApproverAndStatus(any(), any());
        verify(welfareRequestRepository, never()).findBySubApproverAndStatus(any(), any());
    }

    @Test
    @DisplayName("퇴직 — 이관 대상은 있는데 fallback SYSTEM_ADMIN이 없으면 INVALID_APPROVER (전체 롤백 유도)")
    void retire_noFallbackAdmin_throwsInvalidApprover() {
        User target = activeUser(1L, Role.TEAM_LEADER);
        User applicant = activeUser(2L, Role.EMPLOYEE);
        LeaveRequest leave = pendingLeave(applicant, target, null);
        given(userRepository.findById(1L)).willReturn(Optional.of(target));
        given(departmentRepository.findByLeader(target)).willReturn(List.of());
        given(leaveRequestRepository.findByPrimaryApproverAndStatusIn(target, LEAVE_REASSIGN_STATUSES))
                .willReturn(List.of(leave));
        given(leaveRequestRepository.findBySubApproverAndStatusIn(target, LEAVE_REASSIGN_STATUSES))
                .willReturn(List.of());
        given(welfareRequestRepository.findByPrimaryApproverAndStatus(target, RequestStatus.PENDING))
                .willReturn(List.of());
        given(welfareRequestRepository.findBySubApproverAndStatus(target, RequestStatus.PENDING))
                .willReturn(List.of());
        given(userRepository.findFirstByRoleAndIsActiveTrueOrderByIdAsc(Role.SYSTEM_ADMIN))
                .willReturn(Optional.empty());

        BusinessException ex = assertThrows(BusinessException.class, () -> userService.retire(1L, ACTOR_ID));

        assertEquals(ErrorCode.INVALID_APPROVER, ex.getErrorCode());
    }

    @Test
    @DisplayName("퇴직 — 이관 대상이 전혀 없으면 fallback SYSTEM_ADMIN 조회 자체를 하지 않는다")
    void retire_noReassignTargets_skipsFallbackLookup() {
        User target = activeUser(1L, Role.TEAM_LEADER);
        given(userRepository.findById(1L)).willReturn(Optional.of(target));
        given(departmentRepository.findByLeader(target)).willReturn(List.of());
        givenNoReassignTargets(target);

        userService.retire(1L, ACTOR_ID);

        verify(userRepository, never()).findFirstByRoleAndIsActiveTrueOrderByIdAsc(any());
    }

    // ============================ 권한/부서 변경 — 퇴직자 가드 ============================

    @Test
    @DisplayName("권한 변경 — 대상이 퇴직자면 ALREADY_RETIRED")
    void changeRole_retiredTarget_throws() {
        User target = retiredUser(1L);
        given(userRepository.findById(1L)).willReturn(Optional.of(target));

        BusinessException ex = assertThrows(BusinessException.class,
                () -> userService.changeRole(1L, Role.TEAM_LEADER, ACTOR_ID));

        assertEquals(ErrorCode.ALREADY_RETIRED, ex.getErrorCode());
    }

    @Test
    @DisplayName("부서 변경 — 대상이 퇴직자면 ALREADY_RETIRED")
    void changeDepartment_retiredTarget_throws() {
        User target = retiredUser(1L);
        given(userRepository.findById(1L)).willReturn(Optional.of(target));

        BusinessException ex = assertThrows(BusinessException.class,
                () -> userService.changeDepartment(1L, 10L, ACTOR_ID));

        assertEquals(ErrorCode.ALREADY_RETIRED, ex.getErrorCode());
        verify(departmentRepository, never()).findByIdAndActiveTrue(any());
    }

    // ============================ 마지막 SYSTEM_ADMIN 보호 (리뷰 S-2) ============================

    @Test
    @DisplayName("권한 변경 — 마지막 SYSTEM_ADMIN 강등: LAST_SYSTEM_ADMIN, 강등 자체가 일어나지 않는다")
    void changeRole_lastSystemAdmin_throws() {
        User target = activeUser(1L, Role.SYSTEM_ADMIN);
        given(userRepository.findById(1L)).willReturn(Optional.of(target));
        givenOnlyAdminIsTarget(target);

        BusinessException ex = assertThrows(BusinessException.class,
                () -> userService.changeRole(1L, Role.EMPLOYEE, ACTOR_ID));

        assertEquals(ErrorCode.LAST_SYSTEM_ADMIN, ex.getErrorCode());
        assertEquals(Role.SYSTEM_ADMIN, target.getRole());
        verify(departmentRepository, never()).findByLeader(any());
    }

    @Test
    @DisplayName("권한 변경 — 다른 관리자가 남아 있으면 강등된다")
    void changeRole_otherAdminRemains_succeeds() {
        User target = activeUser(1L, Role.SYSTEM_ADMIN);
        given(userRepository.findById(1L)).willReturn(Optional.of(target));
        given(userRepository.findActiveByRoleForUpdate(Role.SYSTEM_ADMIN))
                .willReturn(List.of(target, onboardedAdmin(9L)));
        given(departmentRepository.findByLeader(target)).willReturn(List.of());
        givenNoReassignTargets(target);

        userService.changeRole(1L, Role.EMPLOYEE, ACTOR_ID);

        assertEquals(Role.EMPLOYEE, target.getRole());
    }

    @Test
    @DisplayName("권한 변경 — SYSTEM_ADMIN을 SYSTEM_ADMIN으로 재지정하면 잠금 조회를 하지 않는다")
    void changeRole_toSameAdminRole_skipsLastAdminCheck() {
        User target = activeUser(1L, Role.SYSTEM_ADMIN);
        given(userRepository.findById(1L)).willReturn(Optional.of(target));

        userService.changeRole(1L, Role.SYSTEM_ADMIN, ACTOR_ID);

        assertEquals(Role.SYSTEM_ADMIN, target.getRole());
        verify(userRepository, never()).findActiveByRoleForUpdate(any());
    }

    @Test
    @DisplayName("권한 변경 — 대상이 관리자가 아니면 잠금 조회를 하지 않는다")
    void changeRole_nonAdminTarget_skipsLastAdminCheck() {
        User target = activeUser(1L, Role.TEAM_LEADER);
        given(userRepository.findById(1L)).willReturn(Optional.of(target));
        given(departmentRepository.findByLeader(target)).willReturn(List.of());
        givenNoReassignTargets(target);

        userService.changeRole(1L, Role.EMPLOYEE, ACTOR_ID);

        verify(userRepository, never()).findActiveByRoleForUpdate(any());
    }

    @Test
    @DisplayName("퇴직 — 마지막 SYSTEM_ADMIN: LAST_SYSTEM_ADMIN, 퇴직도 이관도 일어나지 않는다")
    void retire_lastSystemAdmin_throwsAndSkipsReassignment() {
        User target = activeUser(1L, Role.SYSTEM_ADMIN);
        given(userRepository.findById(1L)).willReturn(Optional.of(target));
        givenOnlyAdminIsTarget(target);

        BusinessException ex = assertThrows(BusinessException.class, () -> userService.retire(1L, ACTOR_ID));

        assertEquals(ErrorCode.LAST_SYSTEM_ADMIN, ex.getErrorCode());
        assertTrue(target.isActive());
        verify(departmentRepository, never()).findByLeader(any());
        verify(leaveRequestRepository, never()).findByPrimaryApproverAndStatusIn(any(), any());
    }

    @Test
    @DisplayName("퇴직 — 대기 결재가 0건이어도 마지막 관리자는 막힌다 (fallback 조회를 건너뛰는 경로)")
    void retire_lastSystemAdminWithNoPendingApprovals_stillBlocked() {
        User target = activeUser(1L, Role.SYSTEM_ADMIN);
        given(userRepository.findById(1L)).willReturn(Optional.of(target));
        givenOnlyAdminIsTarget(target);

        assertThrows(BusinessException.class, () -> userService.retire(1L, ACTOR_ID));

        // 기존 fallback 가드는 이관 대상이 있을 때만 돌기 때문에 이 경로를 못 막았다
        verify(userRepository, never()).findFirstByRoleAndIsActiveTrueOrderByIdAsc(any());
    }

    @Test
    @DisplayName("잔여 관리자 계산은 온보딩 완료자만 센다 — 미완료 관리자만 남으면 락아웃으로 본다")
    void lastAdminCheck_countsOnlyOnboardedAdmins() {
        User target = activeUser(1L, Role.SYSTEM_ADMIN);
        given(userRepository.findById(1L)).willReturn(Optional.of(target));
        // givenOnlyAdminIsTarget이 온보딩 미완료 관리자 1명을 함께 돌려준다.
        // 그 계정은 인터셉터가 /api/auth/* 밖을 막아 실제로 아무 조작을 못 하므로 세지 않는다.
        givenOnlyAdminIsTarget(target);

        assertThrows(BusinessException.class, () -> userService.changeRole(1L, Role.EMPLOYEE, ACTOR_ID));

        verify(userRepository).findActiveByRoleForUpdate(Role.SYSTEM_ADMIN);
    }

    // ============================ 연차 직접 설정 ============================

    @Test
    @DisplayName("연차 직접 설정 — 음수 입력은 INVALID_INPUT_VALUE")
    void updateBaseDays_negative_throws() {
        User target = activeUser(1L, Role.EMPLOYEE);
        given(userRepository.findById(1L)).willReturn(Optional.of(target));

        BusinessException ex = assertThrows(BusinessException.class,
                () -> userService.updateBaseDays(1L, new BaseDaysUpdateRequest(new BigDecimal("-1.0")), ACTOR_ID));

        assertEquals(ErrorCode.INVALID_INPUT_VALUE, ex.getErrorCode());
    }

    @Test
    @DisplayName("연차 직접 설정 — 0 이상이면 정상 반영 (퇴직자도 허용, 과거 데이터 정정 목적)")
    void updateBaseDays_success_evenForRetiredUser() {
        User target = retiredUser(1L);
        given(userRepository.findById(1L)).willReturn(Optional.of(target));

        userService.updateBaseDays(1L, new BaseDaysUpdateRequest(new BigDecimal("20.0")), ACTOR_ID);

        assertEquals(0, new BigDecimal("20.0").compareTo(target.getBaseDays()));
    }

    @Test
    @DisplayName("연차 직접 설정 — 당겨쓴 사원의 연차를 늘리면 당겨쓰기가 정산된다 (리뷰 I-8)")
    void updateBaseDays_existingAdvance_settlesAdvanceDays() {
        User target = userWithAdvance(1L, "10.0", "13.0", "3.0");
        given(userRepository.findById(1L)).willReturn(Optional.of(target));

        userService.updateBaseDays(1L, new BaseDaysUpdateRequest(new BigDecimal("20.0")), ACTOR_ID);

        assertEquals(0, new BigDecimal("20.0").compareTo(target.getBaseDays()));
        assertEquals(0, BigDecimal.ZERO.compareTo(target.getAdvanceDays()));
    }

    // ============================ 감사 기록 (리뷰 S-3) ============================

    @Test
    @DisplayName("감사 — 권한 변경은 전/후 권한 라벨과 함께 남는다")
    void changeRole_recordsAudit() {
        User target = activeUser(1L, Role.TEAM_LEADER);
        given(userRepository.findById(1L)).willReturn(Optional.of(target));
        given(departmentRepository.findByLeader(target)).willReturn(List.of());
        givenNoReassignTargets(target);

        userService.changeRole(1L, Role.EMPLOYEE, ACTOR_ID);

        verify(adminAuditService).recordUserChange(ACTOR_ID, AdminAction.ROLE_CHANGED, target, "팀장", "사원");
    }

    @Test
    @DisplayName("감사 — 부서 미배정에서의 이동도 '미배정'으로 남는다 (빈칸이면 무엇에서 바뀌었는지 알 수 없다)")
    void changeDepartment_recordsAuditWithUnassignedLabel() {
        User target = activeUser(1L, Role.EMPLOYEE);
        Department department = Department.create("개발팀", "설명", null);
        given(userRepository.findById(1L)).willReturn(Optional.of(target));
        given(departmentRepository.findByIdAndActiveTrue(10L)).willReturn(Optional.of(department));

        userService.changeDepartment(1L, 10L, ACTOR_ID);

        verify(adminAuditService).recordUserChange(
                ACTOR_ID, AdminAction.DEPARTMENT_CHANGED, target, "미배정", "개발팀");
    }

    @Test
    @DisplayName("감사 — 연차 직접 설정은 전/후 일수로 남는다")
    void updateBaseDays_recordsAudit() {
        User target = activeUser(1L, Role.EMPLOYEE); // baseDays = 10
        given(userRepository.findById(1L)).willReturn(Optional.of(target));

        userService.updateBaseDays(1L, new BaseDaysUpdateRequest(new BigDecimal("20.0")), ACTOR_ID);

        verify(adminAuditService).recordUserChange(
                ACTOR_ID, AdminAction.BASE_DAYS_CHANGED, target, "10일", "20.0일");
    }

    @Test
    @DisplayName("감사 — 퇴직은 퇴직일과 함께 남는다")
    void retire_recordsAudit() {
        User target = activeUser(1L, Role.TEAM_LEADER);
        given(userRepository.findById(1L)).willReturn(Optional.of(target));
        given(departmentRepository.findByLeader(target)).willReturn(List.of());
        givenNoReassignTargets(target);

        userService.retire(1L, ACTOR_ID);

        verify(adminAuditService).recordUserChange(ACTOR_ID, AdminAction.USER_RETIRED, target,
                "재직", "퇴직 (" + target.getRetiredAt() + ")");
    }

    @Test
    @DisplayName("감사 — 조작이 막히면 기록도 남지 않는다 (기록은 조작과 같은 트랜잭션)")
    void blockedOperation_recordsNothing() {
        User target = activeUser(1L, Role.SYSTEM_ADMIN);
        given(userRepository.findById(1L)).willReturn(Optional.of(target));
        givenOnlyAdminIsTarget(target);

        assertThrows(BusinessException.class, () -> userService.changeRole(1L, Role.EMPLOYEE, ACTOR_ID));

        verify(adminAuditService, never()).recordUserChange(any(), any(), any(), any(), any());
    }

    // ============================ 헬퍼 ============================

    /**
     * 대상이 유일한 "쓸 수 있는" 관리자인 상황 — 잠금 조회가 대상 본인만 돌려준다 (리뷰 S-2).
     * 온보딩 미완료 관리자가 함께 있어도 결과가 같아야 하므로 하나 끼워 둔다.
     */
    private void givenOnlyAdminIsTarget(User target) {
        given(userRepository.findActiveByRoleForUpdate(Role.SYSTEM_ADMIN))
                .willReturn(List.of(target, activeUser(77L, Role.SYSTEM_ADMIN)));
    }

    /** 온보딩까지 마쳐 실제로 관리 화면을 쓸 수 있는 관리자 */
    private User onboardedAdmin(Long id) {
        User admin = activeUser(id, Role.SYSTEM_ADMIN);
        admin.completeOnboarding(LocalDate.now().minusYears(1), LocalDate.of(1990, 1, 1));
        return admin;
    }

    private void givenNoReassignTargets(User target) {
        given(leaveRequestRepository.findByPrimaryApproverAndStatusIn(target, LEAVE_REASSIGN_STATUSES))
                .willReturn(List.of());
        given(leaveRequestRepository.findBySubApproverAndStatusIn(target, LEAVE_REASSIGN_STATUSES))
                .willReturn(List.of());
        given(welfareRequestRepository.findByPrimaryApproverAndStatus(target, RequestStatus.PENDING))
                .willReturn(List.of());
        given(welfareRequestRepository.findBySubApproverAndStatus(target, RequestStatus.PENDING))
                .willReturn(List.of());
    }

    private User activeUser(Long id, Role role) {
        return User.builder()
                .id(id)
                .name("user" + id)
                .email("user" + id + "@mlsoft.com")
                .role(role)
                .baseDays(BigDecimal.TEN)
                .useDays(BigDecimal.ZERO)
                .bonusDays(BigDecimal.ZERO)
                .advanceDays(BigDecimal.ZERO)
                .isActive(true)
                .build();
    }

    /** 당겨쓰기가 걸린 사원 — 잔액 불변식 검증용 */
    private User userWithAdvance(Long id, String baseDays, String useDays, String advanceDays) {
        return User.builder()
                .id(id)
                .name("user" + id)
                .email("user" + id + "@mlsoft.com")
                .role(Role.EMPLOYEE)
                .baseDays(new BigDecimal(baseDays))
                .useDays(new BigDecimal(useDays))
                .bonusDays(BigDecimal.ZERO)
                .advanceDays(new BigDecimal(advanceDays))
                .isActive(true)
                .build();
    }

    private User retiredUser(Long id) {
        return User.builder()
                .id(id)
                .name("user" + id)
                .email("user" + id + "@mlsoft.com")
                .role(Role.EMPLOYEE)
                .baseDays(BigDecimal.TEN)
                .useDays(BigDecimal.ZERO)
                .bonusDays(BigDecimal.ZERO)
                .advanceDays(BigDecimal.ZERO)
                .isActive(false)
                .retiredAt(LocalDate.now().minusDays(1))
                .build();
    }

    private LeaveRequest pendingLeave(User applicant, User primaryApprover, User subApprover) {
        return LeaveRequest.create(applicant, LeaveType.ANNUAL,
                List.of(LocalDate.now().plusDays(1)), "사유", primaryApprover, subApprover);
    }
}
