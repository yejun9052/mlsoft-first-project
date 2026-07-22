package com.mlsoft.backend.domain.user.service;

import com.mlsoft.backend.domain.common.RequestStatus;
import com.mlsoft.backend.domain.department.entity.Department;
import com.mlsoft.backend.domain.department.repository.DepartmentRepository;
import com.mlsoft.backend.domain.leave.entity.LeaveRequest;
import com.mlsoft.backend.domain.leave.entity.LeaveType;
import com.mlsoft.backend.domain.leave.repository.LeaveRequestRepository;
import com.mlsoft.backend.domain.user.dto.BaseDaysUpdateRequest;
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

    @Mock
    private UserRepository userRepository;
    @Mock
    private DepartmentRepository departmentRepository;
    @Mock
    private LeaveRequestRepository leaveRequestRepository;
    @Mock
    private WelfareRequestRepository welfareRequestRepository;

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

        userService.retire(1L);

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
        given(welfareRequestRepository.findByPrimaryApproverIdAndStatus(1L, RequestStatus.PENDING))
                .willReturn(List.of());
        given(welfareRequestRepository.findBySubApproverIdAndStatus(1L, RequestStatus.PENDING))
                .willReturn(List.of());
        given(userRepository.findFirstByRoleAndIsActiveTrueOrderByIdAsc(Role.SYSTEM_ADMIN))
                .willReturn(Optional.of(fallback));

        userService.retire(1L);

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
        given(welfareRequestRepository.findByPrimaryApproverIdAndStatus(1L, RequestStatus.PENDING))
                .willReturn(List.of());
        given(welfareRequestRepository.findBySubApproverIdAndStatus(1L, RequestStatus.PENDING))
                .willReturn(List.of());
        given(userRepository.findFirstByRoleAndIsActiveTrueOrderByIdAsc(Role.SYSTEM_ADMIN))
                .willReturn(Optional.of(fallback));

        userService.retire(1L);

        assertEquals(fallback, leave.getSubApprover());
    }

    @Test
    @DisplayName("퇴직 — primary 승인자 id로 걸린 PENDING WelfareRequest: fallback SA id로 재배정")
    void retire_reassignsWelfarePrimaryApprover() {
        User target = activeUser(1L, Role.TEAM_LEADER);
        User applicant = activeUser(2L, Role.EMPLOYEE);
        User fallback = activeUser(9L, Role.SYSTEM_ADMIN);
        WelfarePolicy policy = WelfarePolicy.create("결혼", WelfareTarget.SELF, new BigDecimal("7.0"), "증빙", "설명");
        WelfareRequest welfare = WelfareRequest.create(policy, applicant, "사유", target.getId(), null);

        given(userRepository.findById(1L)).willReturn(Optional.of(target));
        given(departmentRepository.findByLeader(target)).willReturn(List.of());
        given(leaveRequestRepository.findByPrimaryApproverAndStatusIn(target, LEAVE_REASSIGN_STATUSES))
                .willReturn(List.of());
        given(leaveRequestRepository.findBySubApproverAndStatusIn(target, LEAVE_REASSIGN_STATUSES))
                .willReturn(List.of());
        given(welfareRequestRepository.findByPrimaryApproverIdAndStatus(1L, RequestStatus.PENDING))
                .willReturn(List.of(welfare));
        given(welfareRequestRepository.findBySubApproverIdAndStatus(1L, RequestStatus.PENDING))
                .willReturn(List.of());
        given(userRepository.findFirstByRoleAndIsActiveTrueOrderByIdAsc(Role.SYSTEM_ADMIN))
                .willReturn(Optional.of(fallback));

        userService.retire(1L);

        assertEquals(fallback.getId(), welfare.getPrimaryApproverId());
    }

    @Test
    @DisplayName("퇴직 — 이관 대상 조회는 PENDING·CANCEL_PENDING 상태 목록으로만 호출된다 (APPROVED 등은 자동 제외)")
    void retire_queriesOnlyReassignableStatuses() {
        User target = activeUser(1L, Role.TEAM_LEADER);
        given(userRepository.findById(1L)).willReturn(Optional.of(target));
        given(departmentRepository.findByLeader(target)).willReturn(List.of());
        givenNoReassignTargets(target);

        userService.retire(1L);

        verify(leaveRequestRepository).findByPrimaryApproverAndStatusIn(eq(target), eq(LEAVE_REASSIGN_STATUSES));
        verify(leaveRequestRepository).findBySubApproverAndStatusIn(eq(target), eq(LEAVE_REASSIGN_STATUSES));
        verify(welfareRequestRepository).findByPrimaryApproverIdAndStatus(1L, RequestStatus.PENDING);
        verify(welfareRequestRepository).findBySubApproverIdAndStatus(1L, RequestStatus.PENDING);
    }

    @Test
    @DisplayName("퇴직 — 이미 퇴직 처리된 사용자: ALREADY_RETIRED, 이관 로직은 전혀 호출되지 않음")
    void retire_alreadyRetired_throwsAndSkipsReassignment() {
        User target = retiredUser(1L);
        given(userRepository.findById(1L)).willReturn(Optional.of(target));

        BusinessException ex = assertThrows(BusinessException.class, () -> userService.retire(1L));

        assertEquals(ErrorCode.ALREADY_RETIRED, ex.getErrorCode());
        verify(departmentRepository, never()).findByLeader(any());
        verify(leaveRequestRepository, never()).findByPrimaryApproverAndStatusIn(any(), any());
        verify(leaveRequestRepository, never()).findBySubApproverAndStatusIn(any(), any());
        verify(welfareRequestRepository, never()).findByPrimaryApproverIdAndStatus(any(), any());
        verify(welfareRequestRepository, never()).findBySubApproverIdAndStatus(any(), any());
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
        given(welfareRequestRepository.findByPrimaryApproverIdAndStatus(1L, RequestStatus.PENDING))
                .willReturn(List.of());
        given(welfareRequestRepository.findBySubApproverIdAndStatus(1L, RequestStatus.PENDING))
                .willReturn(List.of());
        given(userRepository.findFirstByRoleAndIsActiveTrueOrderByIdAsc(Role.SYSTEM_ADMIN))
                .willReturn(Optional.empty());

        BusinessException ex = assertThrows(BusinessException.class, () -> userService.retire(1L));

        assertEquals(ErrorCode.INVALID_APPROVER, ex.getErrorCode());
    }

    @Test
    @DisplayName("퇴직 — 이관 대상이 전혀 없으면 fallback SYSTEM_ADMIN 조회 자체를 하지 않는다")
    void retire_noReassignTargets_skipsFallbackLookup() {
        User target = activeUser(1L, Role.TEAM_LEADER);
        given(userRepository.findById(1L)).willReturn(Optional.of(target));
        given(departmentRepository.findByLeader(target)).willReturn(List.of());
        givenNoReassignTargets(target);

        userService.retire(1L);

        verify(userRepository, never()).findFirstByRoleAndIsActiveTrueOrderByIdAsc(any());
    }

    // ============================ 권한/부서 변경 — 퇴직자 가드 ============================

    @Test
    @DisplayName("권한 변경 — 대상이 퇴직자면 ALREADY_RETIRED")
    void changeRole_retiredTarget_throws() {
        User target = retiredUser(1L);
        given(userRepository.findById(1L)).willReturn(Optional.of(target));

        BusinessException ex = assertThrows(BusinessException.class,
                () -> userService.changeRole(1L, Role.TEAM_LEADER));

        assertEquals(ErrorCode.ALREADY_RETIRED, ex.getErrorCode());
    }

    @Test
    @DisplayName("부서 변경 — 대상이 퇴직자면 ALREADY_RETIRED")
    void changeDepartment_retiredTarget_throws() {
        User target = retiredUser(1L);
        given(userRepository.findById(1L)).willReturn(Optional.of(target));

        BusinessException ex = assertThrows(BusinessException.class,
                () -> userService.changeDepartment(1L, 10L));

        assertEquals(ErrorCode.ALREADY_RETIRED, ex.getErrorCode());
        verify(departmentRepository, never()).findByIdAndActiveTrue(any());
    }

    // ============================ 연차 직접 설정 ============================

    @Test
    @DisplayName("연차 직접 설정 — 음수 입력은 INVALID_INPUT_VALUE")
    void updateBaseDays_negative_throws() {
        User target = activeUser(1L, Role.EMPLOYEE);
        given(userRepository.findById(1L)).willReturn(Optional.of(target));

        BusinessException ex = assertThrows(BusinessException.class,
                () -> userService.updateBaseDays(1L, new BaseDaysUpdateRequest(new BigDecimal("-1.0"))));

        assertEquals(ErrorCode.INVALID_INPUT_VALUE, ex.getErrorCode());
    }

    @Test
    @DisplayName("연차 직접 설정 — 0 이상이면 정상 반영 (퇴직자도 허용, 과거 데이터 정정 목적)")
    void updateBaseDays_success_evenForRetiredUser() {
        User target = retiredUser(1L);
        given(userRepository.findById(1L)).willReturn(Optional.of(target));

        userService.updateBaseDays(1L, new BaseDaysUpdateRequest(new BigDecimal("20.0")));

        assertEquals(0, new BigDecimal("20.0").compareTo(target.getBaseDays()));
    }

    // ============================ 헬퍼 ============================

    private void givenNoReassignTargets(User target) {
        given(leaveRequestRepository.findByPrimaryApproverAndStatusIn(target, LEAVE_REASSIGN_STATUSES))
                .willReturn(List.of());
        given(leaveRequestRepository.findBySubApproverAndStatusIn(target, LEAVE_REASSIGN_STATUSES))
                .willReturn(List.of());
        given(welfareRequestRepository.findByPrimaryApproverIdAndStatus(target.getId(), RequestStatus.PENDING))
                .willReturn(List.of());
        given(welfareRequestRepository.findBySubApproverIdAndStatus(target.getId(), RequestStatus.PENDING))
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
