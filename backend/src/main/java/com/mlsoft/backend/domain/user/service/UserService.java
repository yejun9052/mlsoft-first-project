package com.mlsoft.backend.domain.user.service;

import com.mlsoft.backend.domain.common.RequestStatus;
import com.mlsoft.backend.domain.department.entity.Department;
import com.mlsoft.backend.domain.department.repository.DepartmentRepository;
import com.mlsoft.backend.domain.leave.entity.LeaveRequest;
import com.mlsoft.backend.domain.leave.repository.LeaveRequestRepository;
import com.mlsoft.backend.domain.user.dto.BaseDaysUpdateRequest;
import com.mlsoft.backend.domain.user.dto.UserProfileUpdateRequest;
import com.mlsoft.backend.domain.user.dto.UserResponse;
import com.mlsoft.backend.domain.user.dto.UserSummaryResponse;
import com.mlsoft.backend.domain.user.entity.Role;
import com.mlsoft.backend.domain.user.entity.User;
import com.mlsoft.backend.domain.user.repository.UserRepository;
import com.mlsoft.backend.domain.welfare.entity.WelfareRequest;
import com.mlsoft.backend.domain.welfare.repository.WelfareRequestRepository;
import com.mlsoft.backend.global.exception.BusinessException;
import com.mlsoft.backend.global.exception.ErrorCode;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.List;

/**
 * 사용자 도메인 서비스 — 조회·프로필 수정·관리자 조작·퇴직 처리 (docs/01 2-2·2-9, docs/03 사용자).
 *
 * <p>퇴직 처리(retire)는 이 도메인에서 가장 민감한 트랜잭션이다:
 * <ul>
 *   <li>대상이 팀장인 부서는 전부 leader 해제 (갭분석 B-4)</li>
 *   <li>대상이 primary/sub 승인자로 걸린 대기 중 연차·복리후생 신청은 SYSTEM_ADMIN fallback으로 재배정</li>
 *   <li>fallback SA가 없는데 이관 대상이 있으면 전체 롤백 (퇴직자가 승인자로 남는 상태를 절대 허용하지 않음)</li>
 * </ul>
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class UserService {

    // 퇴직 처리 기준일은 한국 시간 고정 — LeaveService·AuthService와 동일 정책
    private static final ZoneId KST = ZoneId.of("Asia/Seoul");

    // 연차 이관 대상 상태 — 선차감이 걸려 있어 승인자가 반드시 존재해야 하는 상태
    private static final List<RequestStatus> LEAVE_REASSIGN_STATUSES =
            List.of(RequestStatus.PENDING, RequestStatus.CANCEL_PENDING);

    private final UserRepository userRepository;
    private final DepartmentRepository departmentRepository;
    private final LeaveRequestRepository leaveRequestRepository;
    private final WelfareRequestRepository welfareRequestRepository;

    // ---------------------------------------------------------------------
    // 조회
    // ---------------------------------------------------------------------

    /** 전체 목록 (GET /api/users, SA) — keyword·role 필터, 퇴직자 제외 */
    @Transactional(readOnly = true)
    public Page<UserResponse> getUsers(String keyword, Role role, Pageable pageable) {
        String normalizedKeyword = (keyword == null || keyword.isBlank()) ? null : keyword.trim();
        return userRepository.search(role, normalizedKeyword, pageable).map(UserResponse::of);
    }

    /** 내 부서 팀원 목록 (GET /api/users/team-members) — 부서 미배정이면 빈 목록 */
    @Transactional(readOnly = true)
    public List<UserSummaryResponse> getTeamMembers(Long viewerId) {
        User viewer = findUserOrThrow(viewerId);
        Department department = viewer.getDepartment();
        if (department == null) {
            return List.of();
        }
        return userRepository.findByDepartmentAndIsActiveTrueOrderByNameAsc(department).stream()
                .map(UserSummaryResponse::of)
                .toList();
    }

    /** 서브 승인자 후보 (GET /api/users/approvers) — 재직 중 TEAM_LEADER·SYSTEM_ADMIN, 본인 제외 */
    @Transactional(readOnly = true)
    public List<UserSummaryResponse> getApproverCandidates(Long viewerId) {
        return userRepository
                .findByRoleInAndIsActiveTrueAndIdNot(List.of(Role.TEAM_LEADER, Role.SYSTEM_ADMIN), viewerId).stream()
                .map(UserSummaryResponse::of)
                .toList();
    }

    /** 퇴직자 목록 (GET /api/users/retired, SA) */
    @Transactional(readOnly = true)
    public Page<UserResponse> getRetiredUsers(Pageable pageable) {
        return userRepository.findByIsActiveFalse(pageable).map(UserResponse::of);
    }

    // ---------------------------------------------------------------------
    // 수정
    // ---------------------------------------------------------------------

    /** 내 정보 수정 (PATCH /api/users/me) — 이름·생일만 */
    @Transactional
    public UserResponse updateMyProfile(Long userId, UserProfileUpdateRequest request) {
        User user = findUserOrThrow(userId);
        user.updateProfile(request.name(), request.birthDay());
        return UserResponse.of(user);
    }

    /** 권한 변경 (PATCH /api/users/{id}/role, SA) — 퇴직자 대상이면 ALREADY_RETIRED */
    @Transactional
    public UserResponse changeRole(Long targetId, Role role) {
        User target = findUserOrThrow(targetId);
        validateNotRetired(target);
        target.changeRole(role);
        return UserResponse.of(target);
    }

    /** 부서 변경 (PATCH /api/users/{id}/department, SA) — 퇴직자 대상이면 ALREADY_RETIRED */
    @Transactional
    public UserResponse changeDepartment(Long targetId, Long departmentId) {
        User target = findUserOrThrow(targetId);
        validateNotRetired(target);
        Department department = departmentRepository.findByIdAndActiveTrue(departmentId)
                .orElseThrow(() -> new BusinessException(ErrorCode.DEPARTMENT_NOT_FOUND));
        target.assignDepartment(department);
        return UserResponse.of(target);
    }

    /**
     * 연차 직접 설정 (PATCH /api/users/{id}/base-days, SA).
     * 과거 데이터 정정 목적이라 퇴직자 여부는 검사하지 않는다(의도적 판단).
     */
    @Transactional
    public UserResponse updateBaseDays(Long targetId, BaseDaysUpdateRequest request) {
        User target = findUserOrThrow(targetId);
        BigDecimal baseDays = request.baseDays();
        if (baseDays.compareTo(BigDecimal.ZERO) < 0) {
            throw new BusinessException(ErrorCode.INVALID_INPUT_VALUE);
        }
        target.updateBaseDays(baseDays);
        return UserResponse.of(target);
    }

    // ---------------------------------------------------------------------
    // 퇴직 처리
    // ---------------------------------------------------------------------

    /**
     * 퇴직 처리 (POST /api/users/{id}/retire, SA — docs/01 2-9).
     * - 이미 퇴직 처리된 대상이면 ALREADY_RETIRED
     * - 대상이 팀장인 부서는 전부 leader 해제
     * - 대상이 primary/sub 승인자로 걸린 대기 건(LeaveRequest: PENDING·CANCEL_PENDING,
     *   WelfareRequest: PENDING)을 SYSTEM_ADMIN fallback으로 재배정
     */
    @Transactional
    public void retire(Long targetId) {
        User target = findUserOrThrow(targetId);
        if (!target.isActive()) {
            throw new BusinessException(ErrorCode.ALREADY_RETIRED);
        }
        target.retire(LocalDate.now(KST));

        departmentRepository.findByLeader(target).forEach(Department::clearLeader);
        reassignPendingApprovals(target);

        log.info("[퇴직 처리] userId={}, retiredAt={}", targetId, target.getRetiredAt());
    }

    /**
     * 퇴직자가 걸려 있는 대기 승인 건을 SYSTEM_ADMIN fallback으로 재배정.
     * 이관 대상이 전혀 없으면 fallback 조회 자체를 생략한다.
     */
    private void reassignPendingApprovals(User retiree) {
        List<LeaveRequest> leavesAsPrimary =
                leaveRequestRepository.findByPrimaryApproverAndStatusIn(retiree, LEAVE_REASSIGN_STATUSES);
        List<LeaveRequest> leavesAsSub =
                leaveRequestRepository.findBySubApproverAndStatusIn(retiree, LEAVE_REASSIGN_STATUSES);
        List<WelfareRequest> welfaresAsPrimary =
                welfareRequestRepository.findByPrimaryApproverIdAndStatus(retiree.getId(), RequestStatus.PENDING);
        List<WelfareRequest> welfaresAsSub =
                welfareRequestRepository.findBySubApproverIdAndStatus(retiree.getId(), RequestStatus.PENDING);

        if (leavesAsPrimary.isEmpty() && leavesAsSub.isEmpty()
                && welfaresAsPrimary.isEmpty() && welfaresAsSub.isEmpty()) {
            return; // 이관 대상 없으면 fallback 조회도 불필요
        }

        User fallback = userRepository.findFirstByRoleAndIsActiveTrueOrderByIdAsc(Role.SYSTEM_ADMIN)
                .orElseThrow(() -> new BusinessException(ErrorCode.INVALID_APPROVER));

        leavesAsPrimary.forEach(lr -> lr.reassignPrimaryApprover(fallback));
        leavesAsSub.forEach(lr -> lr.reassignSubApprover(fallback));
        welfaresAsPrimary.forEach(wr -> wr.reassignPrimaryApprover(fallback.getId()));
        welfaresAsSub.forEach(wr -> wr.reassignSubApprover(fallback.getId()));

        log.info("[퇴직 이관] userId={}, fallbackId={}, leavePrimary={}, leaveSub={}, welfarePrimary={}, welfareSub={}",
                retiree.getId(), fallback.getId(),
                leavesAsPrimary.size(), leavesAsSub.size(), welfaresAsPrimary.size(), welfaresAsSub.size());
    }

    // ---------------------------------------------------------------------
    // 내부 헬퍼
    // ---------------------------------------------------------------------

    private void validateNotRetired(User user) {
        if (!user.isActive()) {
            throw new BusinessException(ErrorCode.ALREADY_RETIRED);
        }
    }

    private User findUserOrThrow(Long userId) {
        return userRepository.findById(userId)
                .orElseThrow(() -> new BusinessException(ErrorCode.USER_NOT_FOUND));
    }
}
