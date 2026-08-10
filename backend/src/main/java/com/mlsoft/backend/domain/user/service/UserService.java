package com.mlsoft.backend.domain.user.service;

import com.mlsoft.backend.domain.audit.entity.AdminAction;
import com.mlsoft.backend.domain.audit.service.AdminAuditService;
import com.mlsoft.backend.domain.common.RequestStatus;
import com.mlsoft.backend.domain.department.entity.Department;
import com.mlsoft.backend.domain.department.repository.DepartmentRepository;
import com.mlsoft.backend.domain.leave.entity.LeaveRequest;
import com.mlsoft.backend.domain.leave.repository.LeaveRequestRepository;
import com.mlsoft.backend.domain.user.dto.BaseDaysUpdateRequest;
import com.mlsoft.backend.domain.user.dto.UserProfileUpdateRequest;
import com.mlsoft.backend.domain.user.dto.UserResponse;
import com.mlsoft.backend.domain.user.dto.UserSummaryResponse;
import com.mlsoft.backend.domain.user.entity.OnboardingStatus;
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
    private final AdminAuditService adminAuditService;

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
                .findByRoleInAndIsActiveTrueAndOnboardingStatusAndIdNot(
                        List.of(Role.TEAM_LEADER, Role.SYSTEM_ADMIN), OnboardingStatus.COMPLETED, viewerId).stream()
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

    /**
     * 권한 변경 (PATCH /api/users/{id}/role, SA) — 퇴직자 대상이면 ALREADY_RETIRED.
     * 마지막 관리자를 강등하려 하면 LAST_SYSTEM_ADMIN (리뷰 S-2).
     */
    @Transactional
    public UserResponse changeRole(Long targetId, Role role, Long actorId) {
        User target = findUserOrThrow(targetId);
        validateNotRetired(target);
        if (role != Role.SYSTEM_ADMIN) {
            validateNotLastSystemAdmin(target);
        }
        Role before = target.getRole();
        target.changeRole(role);
        adminAuditService.recordUserChange(actorId, AdminAction.ROLE_CHANGED, target,
                before.getLabel(), role.getLabel());

        // 강등이면 팀장직과 대기 결재를 함께 정리한다 (리뷰 I-5a).
        // department.leader_id를 그대로 두면 결재할 수 없는 사람이 primary로 지정돼
        // 그 부서의 신청이 영구 PENDING으로 남는다(선차감이 유지된 채로).
        // 퇴직 경로에는 이관 로직이 있었는데 강등 경로에만 없었다.
        if (before != Role.EMPLOYEE && role == Role.EMPLOYEE) {
            releaseLeadership(target);
            reassignPendingApprovals(target);
        }
        return UserResponse.of(target);
    }

    /** 강등·퇴직 시 맡고 있던 부서의 팀장직 해제 — 공석이면 SYSTEM_ADMIN fallback이 받는다 (검증 Y-3) */
    private void releaseLeadership(User user) {
        departmentRepository.findByLeader(user).forEach(department -> {
            log.info("[권한 변경] 팀장직 해제 — departmentId={}, userId={}", department.getId(), user.getId());
            department.clearLeader();
        });
    }

    /** 부서 변경 (PATCH /api/users/{id}/department, SA) — 퇴직자 대상이면 ALREADY_RETIRED */
    @Transactional
    public UserResponse changeDepartment(Long targetId, Long departmentId, Long actorId) {
        User target = findUserOrThrow(targetId);
        validateNotRetired(target);
        Department department = departmentRepository.findByIdAndActiveTrue(departmentId)
                .orElseThrow(() -> new BusinessException(ErrorCode.DEPARTMENT_NOT_FOUND));
        String before = departmentLabel(target.getDepartment());
        target.assignDepartment(department);
        adminAuditService.recordUserChange(actorId, AdminAction.DEPARTMENT_CHANGED, target,
                before, department.getName());
        return UserResponse.of(target);
    }

    /** 부서 미배정도 감사 기록에서는 값으로 남아야 한다 — 빈칸이면 무엇에서 바뀌었는지 알 수 없다 */
    private String departmentLabel(Department department) {
        return department == null ? "미배정" : department.getName();
    }

    /**
     * 연차 직접 설정 (PATCH /api/users/{id}/base-days, SA).
     * 과거 데이터 정정 목적이라 퇴직자 여부는 검사하지 않는다(의도적 판단).
     */
    @Transactional
    public UserResponse updateBaseDays(Long targetId, BaseDaysUpdateRequest request, Long actorId) {
        User target = findUserOrThrow(targetId);
        BigDecimal baseDays = request.baseDays();
        if (baseDays.compareTo(BigDecimal.ZERO) < 0) {
            throw new BusinessException(ErrorCode.INVALID_INPUT_VALUE);
        }
        BigDecimal before = target.getBaseDays();
        target.updateBaseDays(baseDays);
        adminAuditService.recordUserChange(actorId, AdminAction.BASE_DAYS_CHANGED, target,
                before + "일", baseDays + "일");
        return UserResponse.of(target);
    }

    // ---------------------------------------------------------------------
    // 퇴직 처리
    // ---------------------------------------------------------------------

    /**
     * 퇴직 처리 (POST /api/users/{id}/retire, SA — docs/01 2-9).
     * - 이미 퇴직 처리된 대상이면 ALREADY_RETIRED
     * - 대상이 마지막 SYSTEM_ADMIN이면 LAST_SYSTEM_ADMIN (리뷰 S-2)
     * - 대상이 팀장인 부서는 전부 leader 해제
     * - 대상이 primary/sub 승인자로 걸린 대기 건(LeaveRequest: PENDING·CANCEL_PENDING,
     *   WelfareRequest: PENDING)을 SYSTEM_ADMIN fallback으로 재배정
     */
    @Transactional
    public void retire(Long targetId, Long actorId) {
        User target = findUserOrThrow(targetId);
        if (!target.isActive()) {
            throw new BusinessException(ErrorCode.ALREADY_RETIRED);
        }
        validateNotLastSystemAdmin(target);
        target.retire(LocalDate.now(KST));

        releaseLeadership(target);
        reassignPendingApprovals(target);

        adminAuditService.recordUserChange(actorId, AdminAction.USER_RETIRED, target,
                "재직", "퇴직 (" + target.getRetiredAt() + ")");
        log.info("[퇴직 처리] userId={}, retiredAt={}, actorId={}", targetId, target.getRetiredAt(), actorId);
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

    /**
     * 마지막 SYSTEM_ADMIN 보호 (리뷰 S-2).
     *
     * <p>강등·퇴직 뒤에도 실제로 관리 화면을 쓸 수 있는 관리자가 최소 1명 남는지 본다.
     * 0명이 되면 권한 부여 엔드포인트 자체가 SA 전용이라 <b>복구 수단이 DB 직접 UPDATE뿐</b>이다.
     *
     * <p>기존 {@code reassignPendingApprovals}의 fallback 조회가 우연히 이걸 막아 주는 것처럼
     * 보였지만, <b>대기 결재가 0건이면 그 조회 자체를 건너뛴다</b>. 갓 만든 시스템이나
     * 결재가 비어 있는 시점이 오히려 락아웃에 가장 가깝다.
     *
     * <p>남은 인원을 셀 때 <b>온보딩 완료까지 함께 본다</b> — 미완료 계정은
     * {@code OnboardingCheckInterceptor}가 {@code /api/auth/*} 밖을 막아 관리자 화면에 못 들어간다.
     * 게다가 그 계정의 입사일이 자동 승인 기간 밖이면 {@code PENDING_APPROVAL}로 들어가는데
     * 그걸 승인해 줄 관리자가 없어 교착이 된다 (리뷰 S-1).
     *
     * <p><b>조회에 행 잠금을 건다</b> — 단순 {@code count}는 동시 요청 두 개가 서로를 세어
     * 함께 통과하는 쓰기 스큐를 막지 못했다. 근거는
     * {@link UserRepository#findActiveByRoleForUpdate}의 주석에 있다.
     */
    private void validateNotLastSystemAdmin(User target) {
        if (target.getRole() != Role.SYSTEM_ADMIN) {
            return;
        }
        // 대상까지 포함해 잠근 뒤(직렬화) 자바에서 "본인 제외 + 온보딩 완료"를 센다
        boolean usableAdminRemains = userRepository.findActiveByRoleForUpdate(Role.SYSTEM_ADMIN).stream()
                .anyMatch(admin -> !admin.getId().equals(target.getId())
                        && admin.getOnboardingStatus() == OnboardingStatus.COMPLETED);
        if (!usableAdminRemains) {
            throw new BusinessException(ErrorCode.LAST_SYSTEM_ADMIN);
        }
    }

    private User findUserOrThrow(Long userId) {
        return userRepository.findById(userId)
                .orElseThrow(() -> new BusinessException(ErrorCode.USER_NOT_FOUND));
    }
}
