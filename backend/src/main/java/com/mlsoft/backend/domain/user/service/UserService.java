package com.mlsoft.backend.domain.user.service;

import com.mlsoft.backend.domain.audit.entity.AdminAction;
import com.mlsoft.backend.domain.audit.service.AdminAuditService;
import com.mlsoft.backend.domain.common.RequestAction;
import com.mlsoft.backend.domain.common.RequestStatus;
import com.mlsoft.backend.domain.department.entity.Department;
import com.mlsoft.backend.domain.department.repository.DepartmentRepository;
import com.mlsoft.backend.domain.leave.entity.LeaveActionHistory;
import com.mlsoft.backend.domain.leave.entity.LeaveRequest;
import com.mlsoft.backend.domain.leave.repository.LeaveActionHistoryRepository;
import com.mlsoft.backend.domain.leave.repository.LeaveRequestRepository;
import com.mlsoft.backend.domain.user.dto.BaseDaysUpdateRequest;
import com.mlsoft.backend.domain.user.dto.RehireRequest;
import com.mlsoft.backend.domain.user.dto.UserProfileUpdateRequest;
import com.mlsoft.backend.domain.user.dto.UserResponse;
import com.mlsoft.backend.domain.user.dto.UserSummaryResponse;
import com.mlsoft.backend.domain.user.entity.EmploymentPeriod;
import com.mlsoft.backend.domain.user.entity.OnboardingStatus;
import com.mlsoft.backend.domain.user.entity.Role;
import com.mlsoft.backend.domain.user.entity.User;
import com.mlsoft.backend.domain.user.repository.EmploymentPeriodRepository;
import com.mlsoft.backend.domain.user.repository.UserRepository;
import com.mlsoft.backend.domain.welfare.entity.WelfareActionHistory;
import com.mlsoft.backend.domain.welfare.entity.WelfareRequest;
import com.mlsoft.backend.domain.welfare.repository.WelfareActionHistoryRepository;
import com.mlsoft.backend.domain.welfare.repository.WelfareRequestRepository;
import com.mlsoft.backend.domain.email.repository.EmailHistoryRepository;
import com.mlsoft.backend.domain.policy.entity.PolicyConfigKey;
import com.mlsoft.backend.domain.policy.service.PolicyConfigReader;
import com.mlsoft.backend.global.exception.BusinessException;
import com.mlsoft.backend.global.exception.ErrorCode;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.Period;
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

    /** 근로기준법상 파기 전에 지켜야 할 최소 보존 기간 — 정책으로 낮출 수 없다. */
    private static final int MIN_PURGE_RETENTION_YEARS = 3;

    // 연차 이관 대상 상태 — 선차감이 걸려 있어 승인자가 반드시 존재해야 하는 상태
    private static final List<RequestStatus> LEAVE_REASSIGN_STATUSES =
            List.of(RequestStatus.PENDING, RequestStatus.CANCEL_PENDING);

    // 재입사 시 선차감이 남아 있는 이전 근속 신청을 종결할 상태
    private static final List<RequestStatus> REHIRE_CLOSE_STATUSES =
            List.of(RequestStatus.PENDING, RequestStatus.CANCEL_PENDING);
    private static final String REHIRE_CANCELLATION_COMMENT = "재입사 처리로 자동 취소";

    private final UserRepository userRepository;
    private final DepartmentRepository departmentRepository;
    private final EmploymentPeriodRepository employmentPeriodRepository;
    private final LeaveRequestRepository leaveRequestRepository;
    private final LeaveActionHistoryRepository leaveActionHistoryRepository;
    private final WelfareRequestRepository welfareRequestRepository;
    private final WelfareActionHistoryRepository welfareActionHistoryRepository;
    private final EmailHistoryRepository emailHistoryRepository;
    private final PolicyConfigReader policyConfigReader;
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

    /**
     * 팀장 후보 (GET /api/users/leader-candidates, SA).
     *
     * <p><b>후보 조건이 서버 검증과 같아야 한다.</b> 팀장 지정은
     * {@link com.mlsoft.backend.domain.user.service.ApproverResolver#canApprove}로 검증하는데,
     * 화면이 전 사원을 후보로 주면 EMPLOYEE를 골랐을 때 저장 단계에서 거부된다.
     *
     * <p><b>페이징하지 않는다.</b> 결재 자격자는 전체 사원의 일부라 한 번에 내려도 된다.
     * 페이징하면 상한(100)에 걸려 뒷사람이 사라지는데, 부서 관리 화면이 이 목록을
     * {@code size=200}으로 요청하다 {@code PAGE_SIZE_EXCEEDED}로 화면 전체가 열리지 않은 것이
     * 이 메서드를 만든 이유다.
     */
    @Transactional(readOnly = true)
    public List<UserSummaryResponse> getLeaderCandidates() {
        return userRepository
                .findByRoleInAndIsActiveTrueAndOnboardingStatus(
                        List.of(Role.TEAM_LEADER, Role.SYSTEM_ADMIN), OnboardingStatus.COMPLETED).stream()
                .map(UserSummaryResponse::of)
                .toList();
    }

    /**
     * 퇴직자 목록 (GET /api/users/retired, SA).
     *
     * <p>MANUAL은 모든 퇴직자를 보여주고, AUTO는 설정된 보존 기간이 지난 퇴직자만 보여준다.
     * 보류·기파기 행은 관리자가 상태를 확인할 수 있도록 목록에 남기되 파기 가능 여부를 false로
     * 내려 실제 파기 공통 가드와 화면 표시를 분리한다.
     */
    @Transactional(readOnly = true)
    public Page<UserResponse> getRetiredUsers(Pageable pageable) {
        String mode = policyConfigReader.getString(PolicyConfigKey.RETIREE_PURGE_MODE);
        boolean autoMode = "AUTO".equals(mode);
        int retentionYears = autoMode
                ? policyConfigReader.getInt(PolicyConfigKey.RETIREE_PURGE_YEARS)
                : MIN_PURGE_RETENTION_YEARS;
        LocalDate today = LocalDate.now(KST);
        Page<User> users = autoMode
                ? userRepository.findByIsActiveFalseAndRetiredAtLessThanEqual(
                        today.minusYears(retentionYears), pageable)
                : userRepository.findByIsActiveFalse(pageable);
        return users.map(user -> toRetiredResponse(user, today, autoMode, retentionYears));
    }

    /** 퇴직자 목록 응답의 경과 기간·파기 가능 여부를 계산한다. */
    private UserResponse toRetiredResponse(User user, LocalDate today,
                                           boolean autoMode, int retentionYears) {
        LocalDate retiredAt = user.getRetiredAt();
        if (retiredAt == null || retiredAt.isAfter(today)) {
            return UserResponse.ofRetired(user, 0L, 0, 0, false);
        }
        long elapsedDays = java.time.temporal.ChronoUnit.DAYS.between(retiredAt, today);
        Period elapsed = Period.between(retiredAt, today);
        boolean retentionMet = !retiredAt.plusYears(MIN_PURGE_RETENTION_YEARS).isAfter(today);
        boolean modeThresholdMet = !retiredAt.plusYears(retentionYears).isAfter(today);
        boolean purgeEligible = !user.isActive()
                && user.getPurgedAt() == null
                && user.getPurgeHoldReason() == null
                && retentionMet
                && (!autoMode || modeThresholdMet);
        return UserResponse.ofRetired(user, elapsedDays, elapsed.getYears(), elapsed.getMonths(), purgeEligible);
    }

    // ---------------------------------------------------------------------
    // 수정
    // ---------------------------------------------------------------------

    /** 내 정보 수정 (PATCH /api/users/me) — 이름·생일·직책 */
    @Transactional
    public UserResponse updateMyProfile(Long userId, UserProfileUpdateRequest request) {
        User user = findUserOrThrow(userId);
        String position = request.position() == null || request.position().isBlank()
                ? null
                : request.position().trim();
        user.updateProfile(request.name().trim(), request.birthDay(), position);
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
        // 팀장 승격은 소속 부서가 있어야 성립한다 — 아래에서 그 부서의 leader_id에 앉히기 때문이다
        if (role == Role.TEAM_LEADER && target.getDepartment() == null) {
            throw new BusinessException(ErrorCode.DEPARTMENT_REQUIRED_FOR_LEADER);
        }

        Role before = target.getRole();
        target.changeRole(role);
        adminAuditService.recordUserChange(actorId, AdminAction.ROLE_CHANGED, target,
                before.getLabel(), role.getLabel());

        // 팀장 승격이면 **부서 팀장 자리까지 함께 옮긴다** (2026-08-16).
        // 예전에는 역할만 바뀌어서, 관리자가 구성원 관리에서 팀장으로 올려도 부서는 "공석"인 채였고
        // 그 부서 신청의 승인자가 SYSTEM_ADMIN fallback으로 갔다. 같은 단어를 쓰는 두 값이
        // 따로 놀았던 것이 원인이다 — 이제 역할이 단일 출처고 leader_id가 그걸 따라온다.
        if (role == Role.TEAM_LEADER) {
            assignDepartmentLeader(target.getDepartment(), target, actorId);
        }

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

    /**
     * 부서 팀장 교체 — <b>부서마다 팀장은 한 명</b>이라는 불변식을 지키는 유일한 자리.
     *
     * <p>이미 팀장이 있으면 그 사람을 사원으로 내린다. 자리에서만 빼고 역할을 남기면
     * "그 부서에 역할이 팀장인 사람 2명"이 되어, 관리자 화면에서 누가 실제 결재자인지 알 수 없다 —
     * 그 혼선이 이번 문제의 절반이었다.
     *
     * <p>내려온 사람의 대기 결재는 반드시 이관한다. 결재할 수 없는 사람이 승인자로 남으면
     * 그 신청은 선차감이 걸린 채 영구 PENDING이 된다 (리뷰 I-5a).
     *
     * <p>구성원 관리(역할 변경)와 부서 관리(팀장 지정) 두 경로가 이 메서드를 함께 쓴다.
     * 각자 구현하면 한쪽에서만 불변식이 깨진다.
     */
    @Transactional
    public void assignDepartmentLeader(Department department, User newLeader, Long actorId) {
        User previousLeader = department.getLeader();
        if (previousLeader != null && previousLeader.getId().equals(newLeader.getId())) {
            return; // 이미 이 사람이 팀장이다
        }

        if (previousLeader != null) {
            Role before = previousLeader.getRole();
            // SYSTEM_ADMIN은 내리지 않는다 — 팀장 자리에서 빠질 뿐 관리자 권한은 부서와 무관하다
            if (before == Role.TEAM_LEADER) {
                previousLeader.changeRole(Role.EMPLOYEE);
                adminAuditService.recordUserChange(actorId, AdminAction.ROLE_CHANGED, previousLeader,
                        before.getLabel(), Role.EMPLOYEE.getLabel());
                reassignPendingApprovals(previousLeader);
                log.info("[팀장 교체] 이전 팀장 강등 — departmentId={}, userId={}",
                        department.getId(), previousLeader.getId());
            }
        }

        department.assignLeader(newLeader);
        log.info("[팀장 교체] departmentId={}, newLeaderId={}, actorId={}",
                department.getId(), newLeader.getId(), actorId);
    }

    /**
     * 부서 팀장 해제 — {@link #assignDepartmentLeader}의 역연산이다 (2026-08-17 감사).
     *
     * <p>부서 관리에서 팀장을 <b>공석으로</b> 바꾸는 경로가 예전에는 {@code department.clearLeader()}만
     * 불렀다. 그러면 화면에는 공석으로 보이는데 <b>그 사람은 팀장 역할과 기존 결재선을 그대로 들고 있어</b>
     * 이미 접수된 신청을 계속 승인·반려할 수 있었다. 새 신청만 상위 부서·총관리자로 가서, 같은 부서의
     * 결재선이 신청 시점에 따라 갈라졌다.
     *
     * <p>지정 경로만 공용 메서드를 타고 해제 경로가 빠져 있으면 <b>불변식이 한쪽에서만 지켜진다</b> —
     * 그건 지켜지지 않는 것과 같다. 08-16에 지정 경로를 합치면서 이쪽을 놓쳤다.
     */
    @Transactional
    public void releaseDepartmentLeader(Department department, Long actorId) {
        User previousLeader = department.getLeader();
        if (previousLeader == null) {
            return; // 이미 공석
        }

        department.clearLeader();

        // SYSTEM_ADMIN은 내리지 않는다 — 팀장 자리에서 빠질 뿐 관리자 권한은 부서와 무관하다
        // (assignDepartmentLeader의 교체 규칙과 같다)
        if (previousLeader.getRole() == Role.TEAM_LEADER) {
            previousLeader.changeRole(Role.EMPLOYEE);
            adminAuditService.recordUserChange(actorId, AdminAction.ROLE_CHANGED, previousLeader,
                    Role.TEAM_LEADER.getLabel(), Role.EMPLOYEE.getLabel());
            // 결재할 수 없게 된 사람에게 대기 건이 남으면 그 신청은 선차감이 걸린 채 영구 PENDING이다 (리뷰 I-5a)
            reassignPendingApprovals(previousLeader);
        }

        log.info("[팀장 해제] departmentId={}, previousLeaderId={}, actorId={}",
                department.getId(), previousLeader.getId(), actorId);
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

    /**
     * 역할·부서 동시 변경
     * (PATCH /api/users/{id}/role-and-department, SA).
     *
     * <p>미배정 사원을 팀장으로 올릴 때 기존 API를 두 번 호출하면 부서만 반영되는
     * 부분 성공을 막을 수 없다. 이 진입점의 트랜잭션이 두 기존 메서드와 감사 기록을
     * 함께 감싸므로, 역할 변경이 실패하면 먼저 수행한 부서 변경도 롤백된다.
     *
     * <p>순서는 의도적으로 부서 변경이 먼저다. {@link #changeRole}의
     * {@code DEPARTMENT_REQUIRED_FOR_LEADER} 가드는 마지막 그물로 그대로 두고,
     * 팀장 교체·기존 팀장 강등·대기 결재 이관은 {@link #assignDepartmentLeader}가
     * 가진 기존 규칙을 그대로 재사용한다.
     */
    @Transactional
    public UserResponse changeRoleAndDepartment(
            Long targetId,
            Role role,
            Long departmentId,
            Long actorId
    ) {
        changeDepartment(targetId, departmentId, actorId);
        return changeRole(targetId, role, actorId);
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
        // 본인 퇴직 금지 (S-7, 2026-08-17). 마지막 관리자 검사만으로는 부족하다 —
        // 관리자가 2명 이상이면 통과해 버리는데, 퇴직은 그 즉시 로그인까지 막혀
        // **스스로 되돌릴 수 없다**(복구는 SYSTEM_ADMIN 전용). 역할 자가 강등보다 나쁘다.
        if (targetId.equals(actorId)) {
            throw new BusinessException(ErrorCode.CANNOT_RETIRE_SELF);
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
     * 수동 퇴직자 파기 (POST /api/users/{id}/purge, SA).
     * users 익명화와 자유 텍스트 벌크 UPDATE, 감사 기록을 하나의 트랜잭션으로 묶는다.
     */
    @Transactional
    public void purge(Long targetId, Long actorId) {
        User target = findUserOrThrow(targetId);
        if (targetId.equals(actorId)) {
            throw new BusinessException(ErrorCode.CANNOT_RETIRE_SELF);
        }
        if (target.getPurgedAt() != null) {
            throw new BusinessException(ErrorCode.ALREADY_PURGED);
        }
        if (target.getPurgeHoldReason() != null) {
            throw new BusinessException(ErrorCode.PURGE_ON_HOLD);
        }
        if (target.isActive()) {
            throw new BusinessException(ErrorCode.NOT_RETIRED);
        }
        LocalDate retiredAt = target.getRetiredAt();
        LocalDate today = LocalDate.now(KST);
        if (retiredAt == null || retiredAt.plusYears(MIN_PURGE_RETENTION_YEARS).isAfter(today)) {
            throw new BusinessException(ErrorCode.PURGE_RETENTION_NOT_MET);
        }

        purgeTarget(target, actorId, LocalDateTime.now(KST), false);
        log.info("[퇴직자 파기] userId={}, purgedAt={}, actorId={}",
                targetId, target.getPurgedAt(), actorId);
    }

    /**
     * 자동 모드 퇴직자 파기 — 사원 1명당 새 트랜잭션에서 실행한다.
     * 대상이 아니게 된 사원은 예고 시각도 함께 초기화해 다음 대상 판정이 새로 시작되게 한다.
     *
     * @return 파기했으면 1, 대상이 아니면 0
     */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public int purgeAutomatically(Long targetId, LocalDate today) {
        if (!"AUTO".equals(policyConfigReader.getString(PolicyConfigKey.RETIREE_PURGE_MODE))) {
            return 0;
        }
        User target = findUserOrThrow(targetId);
        if (target.getPurgedAt() != null) {
            return 0;
        }
        if (target.isActive() || target.getPurgeHoldReason() != null || target.getRetiredAt() == null) {
            target.clearPurgeNoticeSent();
            return 0;
        }

        int retentionYears = policyConfigReader.getInt(PolicyConfigKey.RETIREE_PURGE_YEARS);
        if (target.getRetiredAt().plusYears(retentionYears).isAfter(today)) {
            target.clearPurgeNoticeSent();
            return 0;
        }
        LocalDateTime noticeSentAt = target.getPurgeNoticeSentAt();
        if (noticeSentAt == null
                || noticeSentAt.toLocalDate().isAfter(today.minusDays(RetireePurgeService.NOTICE_LEAD_DAYS))) {
            return 0;
        }

        purgeTarget(target, null, today.atStartOfDay(), true);
        log.info("[퇴직자 자동 파기] userId={}, purgedAt={}", targetId, target.getPurgedAt());
        return 1;
    }

    /** 수동·자동 파기가 공유하는 익명화·본문 파기·감사 기록 경계. */
    private void purgeTarget(User target, Long actorId, LocalDateTime purgedAt, boolean systemActor) {
        target.purge(purgedAt);
        leaveRequestRepository.anonymizeRequestReasonsByUserId(target.getId());
        welfareRequestRepository.anonymizeReasonsByUserId(target.getId());
        leaveActionHistoryRepository.anonymizeCommentsByUserId(target.getId());
        welfareActionHistoryRepository.anonymizeCommentsByUserId(target.getId());
        emailHistoryRepository.anonymizeContentByRecipientId(target.getId());
        if (systemActor) {
            adminAuditService.recordSystemUserPurged(target);
        } else {
            adminAuditService.recordUserPurged(actorId, target);
        }
    }

    /** 수동 파기 보류 설정 — 사유가 없는 보류는 허용하지 않는다. */
    @Transactional
    public void placePurgeHold(Long targetId, String reason, Long actorId) {
        User target = findUserOrThrow(targetId);
        validatePurgeHoldTarget(targetId, actorId, target);
        if (reason == null || reason.isBlank()) {
            throw new BusinessException(ErrorCode.INVALID_INPUT_VALUE);
        }
        target.placePurgeHold(reason.trim());
    }

    /** 수동 파기 보류 해제. */
    @Transactional
    public void releasePurgeHold(Long targetId, Long actorId) {
        User target = findUserOrThrow(targetId);
        validatePurgeHoldTarget(targetId, actorId, target);
        target.releasePurgeHold();
    }

    private void validatePurgeHoldTarget(Long targetId, Long actorId, User target) {
        if (targetId.equals(actorId)) {
            throw new BusinessException(ErrorCode.CANNOT_RETIRE_SELF);
        }
        if (target.getPurgedAt() != null) {
            throw new BusinessException(ErrorCode.ALREADY_PURGED);
        }
        if (target.isActive()) {
            throw new BusinessException(ErrorCode.NOT_RETIRED);
        }
    }

    /**
     * 퇴직 복구 (POST /api/users/{id}/restore, SA) — 잘못 처리한 퇴직을 되돌린다.
     *
     * <p><b>퇴직의 완전한 역연산이 아니다.</b> {@code is_active}·{@code retired_at} 두 플래그만
     * 되돌리고, 퇴직이 함께 수행한 <b>팀장직 해제와 대기 결재 이관은 그대로 둔다</b>:
     * <ul>
     *   <li>팀장직 — 그사이 다른 사람이 그 부서 팀장이 됐을 수 있다. 되살리면 현재 팀장을 말없이
     *       밀어낸다. 필요하면 관리자가 부서 관리에서 다시 지정하면 되고, 그건 눈에 보이는 조작이다</li>
     *   <li>이관된 결재 — 이미 승인·반려됐을 수 있다. 되돌릴 대상이 남아 있는지 확인하려면
     *       퇴직 시점 스냅샷이 필요한데 그런 기록이 없고, 만들면 퇴직 트랜잭션이 더 무거워진다</li>
     * </ul>
     * 즉 <b>복구는 "다시 로그인하고 신청할 수 있게 만드는 것"까지</b>다. 그 범위를 화면 확인
     * 문구에도 그대로 적는다 — 관리자가 팀장직까지 돌아온다고 착각하면 그 부서 결재선이 어긋난다.
     *
     * <p>연차 잔액·기산일·역할·부서는 퇴직이 건드리지 않았으므로 손댈 것이 없다. 다만 퇴직 기간이
     * 길었다면 스케줄러가 {@code is_active = true}만 보고 대상을 고르므로, 다음 실행에서 밀린
     * 기산일이 catch-up으로 정산된다(docs/09 §6). 그게 맞는 동작이다 — 재직자로 되돌린 이상
     * 그 사람의 연차는 현재 연도 기준이어야 한다.
     *
     * @throws BusinessException 퇴직자가 아니면 NOT_RETIRED
     */
    @Transactional
    public void restore(Long targetId, Long actorId) {
        User target = findUserOrThrow(targetId);
        if (target.isActive()) {
            throw new BusinessException(ErrorCode.NOT_RETIRED);
        }
        if (target.getPurgedAt() != null) {
            throw new BusinessException(ErrorCode.ALREADY_PURGED);
        }
        // 복구하면 retired_at이 지워지므로 언제 퇴직했던 계정인지가 감사 이력 말고는 남지 않는다
        LocalDate retiredAt = target.getRetiredAt();
        target.restore();

        adminAuditService.recordUserChange(actorId, AdminAction.USER_RESTORED, target,
                "퇴직 (" + retiredAt + ")", "재직");
        log.info("[퇴직 복구] userId={}, 퇴직일이었던 값={}, actorId={}", targetId, retiredAt, actorId);
    }

    /**
     * 재입사 처리 (POST /api/users/{id}/rehire, SA — 설계-초안/재입사자-처리-설계-2026-09-07 §4~§5).
     *
     * <p>퇴직 복구와 달리 이전 근속을 종료하고 재입사일부터 연차를 0으로 다시 시작한다. 현재 사용 중인
     * {@code users.hireDate}/{@code retiredAt}를 덮어쓰기 전에 {@code employment_periods}에 이전 구간을
     * 저장하며, 재입사 처리와 살아 있는 신청 종결·감사 기록은 같은 트랜잭션에 참여한다.
     *
     * <p>파기된 사원은 이메일이 바뀌어 신규 가입으로 들어와야 하므로 재입사 대상으로 취급하지 않는다.
     */
    @Transactional
    public UserResponse rehire(Long targetId, RehireRequest request, Long actorId) {
        User target = findUserOrThrow(targetId);
        if (target.getPurgedAt() != null) {
            throw new BusinessException(ErrorCode.ALREADY_PURGED);
        }
        if (target.isActive()) {
            throw new BusinessException(ErrorCode.NOT_RETIRED);
        }

        LocalDate retiredAt = target.getRetiredAt();
        if (retiredAt == null) {
            // 비활성 계정은 정상 퇴직 경로에서 반드시 퇴직일을 갖는다.
            throw new BusinessException(ErrorCode.NOT_RETIRED);
        }
        if (request.hireDate().isBefore(retiredAt)) {
            throw new BusinessException(ErrorCode.REHIRE_DATE_BEFORE_RETIREMENT);
        }

        Department department = request.departmentId() == null
                ? null
                : departmentRepository.findByIdAndActiveTrue(request.departmentId())
                        .orElseThrow(() -> new BusinessException(ErrorCode.DEPARTMENT_NOT_FOUND));
        int nextSequence = employmentPeriodRepository.findTopByUserOrderBySeqDesc(target)
                .map(period -> period.getSeq() + 1)
                .orElse(1);

        // 현재 근속은 users에만 두고, 재입사 직전의 종료된 근속만 별도 이력으로 남긴다.
        employmentPeriodRepository.save(EmploymentPeriod.create(
                target, nextSequence, target.getHireDate(), retiredAt));
        closeLiveRequestsForRehire(target, actorId);

        target.rehire(request.hireDate());
        // 부서는 도메인의 근속 초기화와 분리해 서비스에서 폼 값(없으면 미배정)을 적용한다.
        target.assignDepartment(department);
        adminAuditService.recordUserChange(actorId, AdminAction.USER_REHIRED, target,
                "퇴직 (" + retiredAt + ")", "재직 (재입사 " + request.hireDate() + ")");
        log.info("[재입사 처리] userId={}, hireDate={}, departmentId={}, actorId={}",
                targetId, request.hireDate(), request.departmentId(), actorId);
        return UserResponse.of(target);
    }

    /**
     * 이전 근속의 선차감 신청을 취소 상태로 종결한다.
     *
     * <p>재입사에서 users 잔액을 먼저 0으로 만들므로 여기서 User.restoreLeave를 호출하면 use_days가
     * 음수가 된다. 살아 있는 신청의 상태와 이력만 취소하고, 새 근속의 잔액 초기화는 {@link User#rehire}
     * 하나가 담당한다. APPROVED는 실제 사용 기록이므로 조회 대상에 넣지 않는다.
     */
    private void closeLiveRequestsForRehire(User target, Long actorId) {
        List<LeaveRequest> leaves = leaveRequestRepository.findByUserAndStatusIn(target, REHIRE_CLOSE_STATUSES);
        List<WelfareRequest> welfares = welfareRequestRepository.findByUserAndStatusIn(target, REHIRE_CLOSE_STATUSES);
        if (leaves.isEmpty() && welfares.isEmpty()) {
            return;
        }

        User actor = userRepository.getReferenceById(actorId);
        leaves.forEach(leave -> {
            leave.cancelByRehire(REHIRE_CANCELLATION_COMMENT);
            leaveActionHistoryRepository.save(LeaveActionHistory.create(
                    leave, actor, RequestAction.CANCELLED, REHIRE_CANCELLATION_COMMENT));
        });
        welfares.forEach(welfare -> {
            welfare.cancelByRehire();
            welfareActionHistoryRepository.save(WelfareActionHistory.create(
                    welfare, actor, RequestAction.CANCELLED, REHIRE_CANCELLATION_COMMENT));
        });
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
                welfareRequestRepository.findByPrimaryApproverAndStatus(retiree, RequestStatus.PENDING);
        List<WelfareRequest> welfaresAsSub =
                welfareRequestRepository.findBySubApproverAndStatus(retiree, RequestStatus.PENDING);

        if (leavesAsPrimary.isEmpty() && leavesAsSub.isEmpty()
                && welfaresAsPrimary.isEmpty() && welfaresAsSub.isEmpty()) {
            return; // 이관 대상 없으면 fallback 조회도 불필요
        }

        User fallback = userRepository.findFirstByRoleAndIsActiveTrueOrderByIdAsc(Role.SYSTEM_ADMIN)
                .orElseThrow(() -> new BusinessException(ErrorCode.INVALID_APPROVER));

        leavesAsPrimary.forEach(lr -> lr.reassignPrimaryApprover(fallback));
        leavesAsSub.forEach(lr -> lr.reassignSubApprover(fallback));
        welfaresAsPrimary.forEach(wr -> wr.reassignPrimaryApprover(fallback));
        welfaresAsSub.forEach(wr -> wr.reassignSubApprover(fallback));

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
