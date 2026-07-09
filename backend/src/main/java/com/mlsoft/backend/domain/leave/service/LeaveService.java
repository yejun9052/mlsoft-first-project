package com.mlsoft.backend.domain.leave.service;

import com.mlsoft.backend.domain.common.RequestAction;
import com.mlsoft.backend.domain.common.RequestStatus;
import com.mlsoft.backend.domain.department.entity.Department;
import com.mlsoft.backend.domain.leave.dto.ApprovalRequest;
import com.mlsoft.backend.domain.leave.dto.CancelRequest;
import com.mlsoft.backend.domain.leave.dto.LeaveCalendarResponse;
import com.mlsoft.backend.domain.leave.dto.LeaveCreateRequest;
import com.mlsoft.backend.domain.leave.dto.LeaveHistoryResponse;
import com.mlsoft.backend.domain.leave.dto.LeaveResponse;
import com.mlsoft.backend.domain.leave.dto.LeaveSummaryResponse;
import com.mlsoft.backend.domain.leave.entity.LeaveActionHistory;
import com.mlsoft.backend.domain.leave.entity.LeaveRequest;
import com.mlsoft.backend.domain.leave.repository.LeaveActionHistoryRepository;
import com.mlsoft.backend.domain.leave.repository.LeaveRequestRepository;
import com.mlsoft.backend.domain.user.entity.Role;
import com.mlsoft.backend.domain.user.entity.User;
import com.mlsoft.backend.domain.user.repository.UserRepository;
import com.mlsoft.backend.domain.policy.repository.LeavePolicyConfigRepository;
import com.mlsoft.backend.global.exception.BusinessException;
import com.mlsoft.backend.global.exception.ErrorCode;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.YearMonth;
import java.time.ZoneId;
import java.util.List;

/**
 * 연차 도메인 서비스 — 신청·조회·승인/반려·취소 (docs/01 2-3·2-3(b)·2-5, docs/03 연차).
 *
 * <p>동시성 (검증 R-5):
 * <ul>
 *   <li>동시 신청 잔여 초과 → {@code User.@Version} 낙관적 락 (충돌 시 GlobalExceptionHandler가 409 변환)</li>
 *   <li>이중 처리(primary·sub 동시 승인, 취소 더블클릭) → 상태 조건부 갱신(WHERE status=기대상태)으로
 *       1행만 claim, rowcount 0이면 {@code ALREADY_PROCESSED}</li>
 * </ul>
 * 조건부 갱신은 벌크 UPDATE(clear/flush)이므로 이후 필요한 엔티티를 재조회해 부수효과를 적용한다.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class LeaveService {

    // 연차 날짜 기준일은 한국 시간 고정 (서버 TZ 무관, DB도 Asia/Seoul) — AuthService와 동일 정책
    private static final ZoneId KST = ZoneId.of("Asia/Seoul");

    // LeavePolicyConfig 키 — DataInitializer가 시딩하는 당겨쓰기 허용 플래그 (docs/02 3-11)
    private static final String CONFIG_ADVANCE_LEAVE_ENABLED = "advance_leave_enabled";

    // 중복 검사 대상 — 잔여를 점유 중인(선차감·승인·소급취소대기) 상태
    private static final List<RequestStatus> ACTIVE_STATUSES =
            List.of(RequestStatus.PENDING, RequestStatus.APPROVED, RequestStatus.CANCEL_PENDING);
    // 승인자 대기 목록 — 신규 대기 + 소급취소 승인 대기
    private static final List<RequestStatus> APPROVER_PENDING_STATUSES =
            List.of(RequestStatus.PENDING, RequestStatus.CANCEL_PENDING);
    // 캘린더 — 확정된(승인) 연차만
    private static final List<RequestStatus> CALENDAR_STATUSES =
            List.of(RequestStatus.APPROVED);
    // 팀 현황 — 예정/확정 모두 (대기·승인·소급취소대기)
    private static final List<RequestStatus> TEAM_STATUSES =
            List.of(RequestStatus.PENDING, RequestStatus.APPROVED, RequestStatus.CANCEL_PENDING);

    private final LeaveRequestRepository leaveRequestRepository;
    private final LeaveActionHistoryRepository leaveActionHistoryRepository;
    private final UserRepository userRepository;
    private final LeavePolicyConfigRepository leavePolicyConfigRepository;

    // ---------------------------------------------------------------------
    // 신청
    // ---------------------------------------------------------------------

    /**
     * 연차 신청 (POST /api/leaves).
     * 승인자 확정 → 날짜 검증(주말·과거) → 중복 검사 → PENDING 저장 + 선차감(당겨쓰기 정책 반영) + 이력.
     */
    @Transactional
    public LeaveResponse apply(Long userId, LeaveCreateRequest request) {
        User applicant = findUserOrThrow(userId);
        validateDates(request.dates());

        User primaryApprover = resolvePrimaryApprover(applicant);
        User subApprover = resolveSubApprover(request.subApproverId(), applicant);

        if (!leaveRequestRepository.findOverlapping(applicant, request.dates(), ACTIVE_STATUSES).isEmpty()) {
            throw new BusinessException(ErrorCode.OVERLAPPING_LEAVE_REQUEST);
        }

        LeaveRequest leave = LeaveRequest.create(
                applicant, request.leaveType(), request.dates(), request.reason(), primaryApprover, subApprover);
        // 선차감 — 잔여 부족 + 당겨쓰기 off면 INSUFFICIENT_LEAVE_BALANCE, on이면 부족분 advance_days 누적
        BigDecimal advanceUsed = applicant.deductLeave(leave.getDays(), isAdvanceLeaveEnabled());
        leave.recordAdvanceUsage(advanceUsed);
        leaveRequestRepository.save(leave);

        saveHistory(leave, applicant, RequestAction.PENDING, request.reason());
        // TODO(email): 신청 알림 — 당사자·primary·sub에게 @Async + AFTER_COMMIT 이벤트 발행 (docs/01 2-3, 다음 마일스톤)
        log.info("[연차 신청] userId={}, leaveId={}, days={}, advanceUsed={}",
                userId, leave.getId(), leave.getDays(), advanceUsed);
        return LeaveResponse.of(leave);
    }

    // ---------------------------------------------------------------------
    // 조회
    // ---------------------------------------------------------------------

    /** 내 신청 내역 (GET /api/leaves/me) — status 필터 선택 */
    @Transactional(readOnly = true)
    public Page<LeaveResponse> getMyLeaves(Long userId, RequestStatus status, Pageable pageable) {
        User user = findUserOrThrow(userId);
        Page<LeaveRequest> page = (status == null)
                ? leaveRequestRepository.findByUser(user, pageable)
                : leaveRequestRepository.findByUserAndStatus(user, status, pageable);
        return page.map(LeaveResponse::of);
    }

    /** 잔여 현황 (GET /api/leaves/me/summary) */
    @Transactional(readOnly = true)
    public LeaveSummaryResponse getMySummary(Long userId) {
        User user = findUserOrThrow(userId);
        BigDecimal pendingDays = leaveRequestRepository.sumDaysByUserAndStatus(user, RequestStatus.PENDING);
        return LeaveSummaryResponse.of(user, pendingDays);
    }

    /** 캘린더용 승인 연차 (GET /api/leaves/calendar) — 타인 사유 마스킹 */
    @Transactional(readOnly = true)
    public List<LeaveCalendarResponse> getCalendar(Long viewerId, int year, int month) {
        if (month < 1 || month > 12) {
            throw new BusinessException(ErrorCode.INVALID_INPUT_VALUE);
        }
        User viewer = findUserOrThrow(viewerId);
        YearMonth yearMonth = YearMonth.of(year, month);
        List<LeaveRequest> leaves = leaveRequestRepository.findInDateRange(
                CALENDAR_STATUSES, yearMonth.atDay(1), yearMonth.atEndOfMonth());
        return leaves.stream()
                .map(leave -> LeaveCalendarResponse.of(leave, canViewReason(viewer, leave)))
                .toList();
    }

    /** 내 팀 연차 현황 (GET /api/leaves/team) — 기간 미지정 시 이번 달, 타인 사유 마스킹 */
    @Transactional(readOnly = true)
    public List<LeaveCalendarResponse> getTeam(Long viewerId, LocalDate from, LocalDate to) {
        User viewer = findUserOrThrow(viewerId);
        Department department = viewer.getDepartment();
        if (department == null) {
            return List.of(); // 부서 미배정 — 조회 대상 없음
        }
        YearMonth thisMonth = YearMonth.now(KST);
        LocalDate start = (from != null) ? from : thisMonth.atDay(1);
        LocalDate end = (to != null) ? to : thisMonth.atEndOfMonth();
        List<LeaveRequest> leaves = leaveRequestRepository.findByDepartmentInDateRange(
                department.getId(), TEAM_STATUSES, start, end);
        return leaves.stream()
                .map(leave -> LeaveCalendarResponse.of(leave, canViewReason(viewer, leave)))
                .toList();
    }

    /** 내가 승인자인 대기 목록 (GET /api/leaves/pending) — 취소 대기 포함 */
    @Transactional(readOnly = true)
    public Page<LeaveResponse> getPending(Long approverId, Pageable pageable) {
        User approver = findUserOrThrow(approverId);
        return leaveRequestRepository.findPendingForApprover(approver, APPROVER_PENDING_STATUSES, pageable)
                .map(LeaveResponse::of);
    }

    /** 전체 신청 목록 (GET /api/leaves, SA) — status·keyword 필터 */
    @Transactional(readOnly = true)
    public Page<LeaveResponse> getAllForAdmin(RequestStatus status, String keyword, Pageable pageable) {
        String normalized = (keyword == null || keyword.isBlank()) ? null : keyword.trim();
        return leaveRequestRepository.findForAdmin(status, normalized, pageable).map(LeaveResponse::of);
    }

    /** 처리 이력 (GET /api/leaves/{id}/histories) — 본인·승인자·SA만 */
    @Transactional(readOnly = true)
    public List<LeaveHistoryResponse> getHistories(Long leaveId, Long viewerId) {
        LeaveRequest leave = findLeaveOrThrow(leaveId);
        User viewer = findUserOrThrow(viewerId);
        if (!canViewReason(viewer, leave)) {
            throw new BusinessException(ErrorCode.ACCESS_DENIED);
        }
        return leaveActionHistoryRepository.findByLeaveRequestOrderByCreatedAtAsc(leave).stream()
                .map(LeaveHistoryResponse::of)
                .toList();
    }

    // ---------------------------------------------------------------------
    // 승인 / 반려
    // ---------------------------------------------------------------------

    /**
     * 승인/반려 (POST /api/leaves/{id}/approval) — PENDING에서만, 조건부 갱신으로 이중 처리 차단.
     * 반려 시 선차감(당겨쓰기 포함) 복구.
     */
    @Transactional
    public void processApproval(Long leaveId, Long actorId, ApprovalRequest request) {
        LeaveRequest leave = findLeaveOrThrow(leaveId);
        User actor = findUserOrThrow(actorId);
        validateApprover(leave, actor);
        if (leave.getStatus() != RequestStatus.PENDING) {
            throw new BusinessException(ErrorCode.ALREADY_PROCESSED);
        }

        boolean approved = Boolean.TRUE.equals(request.approved());
        RequestStatus next = approved ? RequestStatus.APPROVED : RequestStatus.REJECTED;
        if (leaveRequestRepository.updateStatusIfCurrent(leaveId, RequestStatus.PENDING, next) == 0) {
            throw new BusinessException(ErrorCode.ALREADY_PROCESSED); // 동시 처리에서 패배
        }

        LeaveRequest fresh = findLeaveOrThrow(leaveId);
        if (!approved) {
            fresh.getUser().restoreLeave(fresh.getDays(), fresh.getAdvanceUsedDays());
        }
        saveHistory(fresh, findUserOrThrow(actorId),
                approved ? RequestAction.APPROVED : RequestAction.REJECTED, request.comment());
        // TODO(email): 승인/반려 알림 (docs/01 2-3, 다음 마일스톤)
        log.info("[연차 {}] leaveId={}, actorId={}", approved ? "승인" : "반려", leaveId, actorId);
    }

    // ---------------------------------------------------------------------
    // 취소 / 소급취소 승인
    // ---------------------------------------------------------------------

    /**
     * 취소 신청 (POST /api/leaves/{id}/cancel) — 본인만.
     * - PENDING: 즉시 CANCELLED + 복구
     * - APPROVED & 미래 날짜만: 즉시 CANCELLED + 복구
     * - APPROVED & 과거 날짜 포함: CANCEL_PENDING(승인자 승인 대기, 복구 보류)
     */
    @Transactional
    public RequestStatus cancel(Long leaveId, Long ownerId, CancelRequest request) {
        LeaveRequest leave = findLeaveOrThrow(leaveId);
        if (!leave.getUser().getId().equals(ownerId)) {
            throw new BusinessException(ErrorCode.ACCESS_DENIED);
        }

        String reason = request.reason();
        RequestStatus status = leave.getStatus();
        RequestStatus result;
        if (status == RequestStatus.PENDING) {
            claimCancelTransition(leaveId, RequestStatus.PENDING, RequestStatus.CANCELLED, reason);
            restoreAndRecord(leaveId, RequestAction.CANCELLED, reason);
            result = RequestStatus.CANCELLED;
        } else if (status == RequestStatus.APPROVED) {
            boolean hasPastDate = leave.getDates().stream().anyMatch(date -> date.isBefore(LocalDate.now(KST)));
            if (hasPastDate) {
                // 소급 취소 — 복구는 승인자 승인 시점으로 미룬다
                claimCancelTransition(leaveId, RequestStatus.APPROVED, RequestStatus.CANCEL_PENDING, reason);
                saveHistory(findLeaveOrThrow(leaveId), leave.getUser(), RequestAction.CANCEL_PENDING, reason);
                result = RequestStatus.CANCEL_PENDING;
            } else {
                claimCancelTransition(leaveId, RequestStatus.APPROVED, RequestStatus.CANCELLED, reason);
                restoreAndRecord(leaveId, RequestAction.CANCELLED, reason);
                result = RequestStatus.CANCELLED;
            }
        } else {
            throw new BusinessException(ErrorCode.ALREADY_PROCESSED); // REJECTED/CANCELLED/CANCEL_PENDING은 취소 불가
        }
        // TODO(email): 취소 알림 (다음 마일스톤)
        log.info("[연차 취소] leaveId={}, ownerId={}, from={} → {}", leaveId, ownerId, status, result);
        return result;
    }

    /**
     * 소급 취소 승인/반려 (POST /api/leaves/{id}/cancel-approval) — CANCEL_PENDING에서만.
     * 승인 → CANCELLED + 복구, 반려 → APPROVED 복원.
     */
    @Transactional
    public void processCancelApproval(Long leaveId, Long actorId, ApprovalRequest request) {
        LeaveRequest leave = findLeaveOrThrow(leaveId);
        User actor = findUserOrThrow(actorId);
        validateApprover(leave, actor);
        if (leave.getStatus() != RequestStatus.CANCEL_PENDING) {
            throw new BusinessException(ErrorCode.ALREADY_PROCESSED);
        }

        boolean approved = Boolean.TRUE.equals(request.approved());
        RequestStatus next = approved ? RequestStatus.CANCELLED : RequestStatus.APPROVED;
        if (leaveRequestRepository.updateStatusIfCurrent(leaveId, RequestStatus.CANCEL_PENDING, next) == 0) {
            throw new BusinessException(ErrorCode.ALREADY_PROCESSED);
        }

        LeaveRequest fresh = findLeaveOrThrow(leaveId);
        if (approved) {
            fresh.getUser().restoreLeave(fresh.getDays(), fresh.getAdvanceUsedDays());
        }
        saveHistory(fresh, findUserOrThrow(actorId),
                approved ? RequestAction.CANCEL_APPROVED : RequestAction.CANCEL_REJECTED, request.comment());
        // TODO(email): 소급취소 처리 알림 (다음 마일스톤)
        log.info("[소급취소 {}] leaveId={}, actorId={}", approved ? "승인" : "반려", leaveId, actorId);
    }

    // ---------------------------------------------------------------------
    // 내부 헬퍼
    // ---------------------------------------------------------------------

    /** 취소 계열 조건부 전이 — rowcount 0이면 이미 처리됨 */
    private void claimCancelTransition(Long leaveId, RequestStatus expected, RequestStatus next, String reason) {
        if (leaveRequestRepository.updateStatusToCancelIfCurrent(leaveId, expected, next, reason) == 0) {
            throw new BusinessException(ErrorCode.ALREADY_PROCESSED);
        }
    }

    /** 조건부 전이 후 재조회해 선차감 복구 + 이력 기록 (즉시 취소 경로) */
    private void restoreAndRecord(Long leaveId, RequestAction action, String reason) {
        LeaveRequest fresh = findLeaveOrThrow(leaveId);
        fresh.getUser().restoreLeave(fresh.getDays(), fresh.getAdvanceUsedDays());
        saveHistory(fresh, fresh.getUser(), action, reason);
    }

    /** 당겨쓰기 허용 여부 (leave_policy_config.advance_leave_enabled, 미설정 시 false) */
    private boolean isAdvanceLeaveEnabled() {
        return leavePolicyConfigRepository.findByName(CONFIG_ADVANCE_LEAVE_ENABLED)
                .map(config -> Boolean.parseBoolean(config.getValue()))
                .orElse(false);
    }

    /** 신청 날짜 검증 — 주말·과거 거부 (공휴일 검증은 holidays API 마일스톤에서) */
    private void validateDates(List<LocalDate> dates) {
        LocalDate today = LocalDate.now(KST);
        for (LocalDate date : dates) {
            DayOfWeek dayOfWeek = date.getDayOfWeek();
            if (dayOfWeek == DayOfWeek.SATURDAY || dayOfWeek == DayOfWeek.SUNDAY) {
                throw new BusinessException(ErrorCode.WEEKEND_NOT_ALLOWED);
            }
            if (date.isBefore(today)) {
                throw new BusinessException(ErrorCode.PAST_DATE_NOT_ALLOWED);
            }
        }
    }

    /** 기본 승인자 = 소속 부서 팀장. 미배정·공석·팀장 퇴직·본인이 팀장이면 SYSTEM_ADMIN fallback (검증 Y-3) */
    private User resolvePrimaryApprover(User applicant) {
        Department department = applicant.getDepartment();
        if (department != null && department.getLeader() != null) {
            User leader = department.getLeader();
            if (leader.isActive() && !leader.getId().equals(applicant.getId())) {
                return leader;
            }
        }
        return userRepository.findFirstByRoleAndIsActiveTrueOrderByIdAsc(Role.SYSTEM_ADMIN)
                .orElseThrow(() -> new BusinessException(ErrorCode.INVALID_APPROVER));
    }

    /** 서브 승인자 검증 — 선택 시 재직 중 TEAM_LEADER·SYSTEM_ADMIN, 본인 제외 (docs/01 2-3) */
    private User resolveSubApprover(Long subApproverId, User applicant) {
        if (subApproverId == null) {
            return null;
        }
        User sub = userRepository.findById(subApproverId)
                .orElseThrow(() -> new BusinessException(ErrorCode.INVALID_APPROVER));
        boolean eligible = sub.isActive()
                && (sub.getRole() == Role.TEAM_LEADER || sub.getRole() == Role.SYSTEM_ADMIN)
                && !sub.getId().equals(applicant.getId());
        if (!eligible) {
            throw new BusinessException(ErrorCode.INVALID_APPROVER);
        }
        return sub;
    }

    /** 처리자가 이 건의 primary·sub 승인자인지 검증 (검증 R-5, docs/03 approval 권한) */
    private void validateApprover(LeaveRequest leave, User actor) {
        Long actorId = actor.getId();
        boolean isPrimary = leave.getPrimaryApprover() != null && leave.getPrimaryApprover().getId().equals(actorId);
        boolean isSub = leave.getSubApprover() != null && leave.getSubApprover().getId().equals(actorId);
        if (!isPrimary && !isSub) {
            throw new BusinessException(ErrorCode.ACCESS_DENIED);
        }
    }

    /** 사유 열람 권한 — 본인·해당 건 승인자·SYSTEM_ADMIN (docs/01 2-5(b), 검증 Y-4). 이력 조회 권한과 동일 */
    private boolean canViewReason(User viewer, LeaveRequest leave) {
        if (viewer.getRole() == Role.SYSTEM_ADMIN) {
            return true;
        }
        Long viewerId = viewer.getId();
        return leave.getUser().getId().equals(viewerId)
                || (leave.getPrimaryApprover() != null && leave.getPrimaryApprover().getId().equals(viewerId))
                || (leave.getSubApprover() != null && leave.getSubApprover().getId().equals(viewerId));
    }

    /** 처리 이력 저장 — comment 없으면 빈 문자열 (컬럼 not-null) */
    private void saveHistory(LeaveRequest leave, User actor, RequestAction action, String comment) {
        String safeComment = (comment != null) ? comment : "";
        leaveActionHistoryRepository.save(LeaveActionHistory.create(leave, actor, action, safeComment));
    }

    private User findUserOrThrow(Long userId) {
        return userRepository.findById(userId)
                .orElseThrow(() -> new BusinessException(ErrorCode.USER_NOT_FOUND));
    }

    private LeaveRequest findLeaveOrThrow(Long leaveId) {
        return leaveRequestRepository.findById(leaveId)
                .orElseThrow(() -> new BusinessException(ErrorCode.LEAVE_REQUEST_NOT_FOUND));
    }
}
