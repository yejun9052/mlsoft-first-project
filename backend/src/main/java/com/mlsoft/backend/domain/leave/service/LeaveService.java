package com.mlsoft.backend.domain.leave.service;

import com.mlsoft.backend.domain.common.RequestAction;
import com.mlsoft.backend.domain.common.RequestStatus;
import com.mlsoft.backend.domain.department.entity.Department;
import com.mlsoft.backend.domain.email.service.EmailNotificationPublisher;
import com.mlsoft.backend.domain.leave.dto.ApprovalRequest;
import com.mlsoft.backend.domain.leave.dto.CancelRequest;
import com.mlsoft.backend.domain.leave.dto.LeaveCalendarResponse;
import com.mlsoft.backend.domain.leave.dto.LeaveCreateRequest;
import com.mlsoft.backend.domain.leave.dto.LeaveHistoryResponse;
import com.mlsoft.backend.domain.leave.dto.LeaveResponse;
import com.mlsoft.backend.domain.leave.dto.LeaveSummaryResponse;
import com.mlsoft.backend.domain.leave.entity.LeaveActionHistory;
import com.mlsoft.backend.domain.leave.entity.LeaveRequest;
import com.mlsoft.backend.domain.leave.entity.LeaveType;
import com.mlsoft.backend.domain.leave.repository.LeaveActionHistoryRepository;
import com.mlsoft.backend.domain.leave.repository.LeaveRequestRepository;
import com.mlsoft.backend.domain.holiday.service.HolidayService;
import com.mlsoft.backend.domain.user.entity.Role;
import com.mlsoft.backend.domain.user.entity.User;
import com.mlsoft.backend.domain.user.repository.UserRepository;
import com.mlsoft.backend.domain.user.service.ApproverResolver;
import com.mlsoft.backend.domain.policy.entity.PolicyConfigKey;
import com.mlsoft.backend.domain.policy.service.PolicyConfigReader;
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
import java.time.temporal.ChronoUnit;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

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

    /** 팀 현황 조회 기간 상한 — 페이징이 없는 목록이라 기간이 곧 건수 상한이다 (리뷰 S-4) */
    private static final long MAX_QUERY_RANGE_DAYS = 366;

    /** 하루에 쓸 수 있는 연차 정원 — 반차 둘이 합쳐 하루가 된다 (리뷰 I-7) */
    private static final BigDecimal FULL_DAY = new BigDecimal("1.0");

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
    /** 정책 설정 읽기 — 키 상수·파싱을 각 서비스에 흩지 않는다 (docs/02 3-11) */
    private final PolicyConfigReader policyConfigReader;
    // 공휴일 판정 — 조회 실패해도 예외를 던지지 않는다(빈 집합)
    private final HolidayService holidayService;
    // 승인자 결정 — 연차·복리후생 공용 규칙 (리뷰 I-5)
    private final ApproverResolver approverResolver;
    // 이메일 알림 — 이벤트만 발행하고 발송은 커밋 후 비동기로 일어난다.
    // SMTP 장애가 이 서비스의 트랜잭션을 롤백시키지 않는다 (검증 R-4)
    private final EmailNotificationPublisher emailNotificationPublisher;

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

        User primaryApprover = approverResolver.resolvePrimary(applicant);
        User subApprover = approverResolver.resolveSub(request.subApproverId(), applicant, primaryApprover);

        validateNoDateConflict(applicant, request.leaveType(), request.dates());

        LeaveRequest leave = LeaveRequest.create(
                applicant, request.leaveType(), request.dates(), request.reason(), primaryApprover, subApprover);
        // 선차감 — 잔여 부족 + 당겨쓰기 off면 INSUFFICIENT_LEAVE_BALANCE,
        // on이면 부족분이 advance_days에 잡히되 상한(advance_max_days)을 넘으면 ADVANCE_LIMIT_EXCEEDED
        BigDecimal advanceUsed = applicant.deductLeave(
                leave.getDays(),
                policyConfigReader.getBoolean(PolicyConfigKey.ADVANCE_LEAVE_ENABLED),
                policyConfigReader.getDecimal(PolicyConfigKey.ADVANCE_MAX_DAYS));
        leave.recordAdvanceUsage(advanceUsed);
        leaveRequestRepository.save(leave);

        saveHistory(leave, applicant, RequestAction.PENDING, request.reason());
        emailNotificationPublisher.publishLeaveApplied(leave);
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

    /**
     * 캘린더용 승인 연차 (GET /api/leaves/calendar) — 타인 사유 마스킹.
     * keyword(신청자명)·departmentId는 선택 필터다. 캘린더가 이미 전사 공개라
     * 이름으로 좁히는 것이 노출 범위를 넓히지 않는다 — 사유 마스킹 기준은 그대로 유지된다.
     */
    @Transactional(readOnly = true)
    public List<LeaveCalendarResponse> getCalendar(Long viewerId, int year, int month,
                                                   String keyword, Long departmentId) {
        if (month < 1 || month > 12) {
            throw new BusinessException(ErrorCode.INVALID_INPUT_VALUE);
        }
        User viewer = findUserOrThrow(viewerId);
        YearMonth yearMonth = YearMonth.of(year, month);
        String normalized = (keyword == null || keyword.isBlank()) ? null : keyword.trim();
        List<LeaveRequest> leaves = leaveRequestRepository.findInDateRange(
                CALENDAR_STATUSES, yearMonth.atDay(1), yearMonth.atEndOfMonth(), normalized, departmentId);
        return leaves.stream()
                .map(leave -> LeaveCalendarResponse.of(leave, canViewReason(viewer, leave)))
                .toList();
    }

    /** 내 팀 연차 현황 (GET /api/leaves/team) — 기간 미지정 시 이번 달, 타인 사유 마스킹 */
    @Transactional(readOnly = true)
    public List<LeaveCalendarResponse> getTeam(Long viewerId, LocalDate from, LocalDate to) {
        User viewer = findUserOrThrow(viewerId);
        YearMonth thisMonth = YearMonth.now(KST);
        LocalDate start = (from != null) ? from : thisMonth.atDay(1);
        LocalDate end = (to != null) ? to : thisMonth.atEndOfMonth();
        // 입력 검증이 부서 배정 여부보다 먼저다 — 뒤에 두면 부서 미배정 사원에게만 잘못된 기간이
        // 조용히 통과한다(빈 목록으로 빠져나감). 같은 요청이 사람에 따라 다르게 판정되면 안 된다
        validateRange(start, end);

        Department department = viewer.getDepartment();
        if (department == null) {
            return List.of(); // 부서 미배정 — 조회 대상 없음
        }
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
            restoreCurrentYearPortion(fresh);
        }
        saveHistory(fresh, actor,
                approved ? RequestAction.APPROVED : RequestAction.REJECTED, request.comment());
        emailNotificationPublisher.publishLeaveProcessed(fresh, actor, approved);
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
        // 상태 전이가 벌크 갱신(clearAutomatically)이라 재조회해야 새 status·cancelReason이 보인다
        emailNotificationPublisher.publishLeaveCancelled(findLeaveOrThrow(leaveId));
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
            restoreCurrentYearPortion(fresh);
        }
        saveHistory(fresh, actor,
                approved ? RequestAction.CANCEL_APPROVED : RequestAction.CANCEL_REJECTED, request.comment());
        emailNotificationPublisher.publishLeaveCancelProcessed(fresh, actor, approved);
        log.info("[소급취소 {}] leaveId={}, actorId={}", approved ? "승인" : "반려", leaveId, actorId);
    }

    // ---------------------------------------------------------------------
    // 내부 헬퍼
    // ---------------------------------------------------------------------

    /**
     * 조회 기간 검증 — 팀 현황은 페이징 없이 {@code List}를 통째로 돌려주므로 기간이 곧 상한이다 (리뷰 S-4).
     *
     * <p>{@code from/to}에 제한이 없으면 {@code from=1900-01-01}으로 그 부서의 전체 이력을 한 번에
     * 끌어올 수 있다. 페이징을 붙이는 것이 정석이지만 화면이 기간 단위 캘린더라 그 형태가 맞지 않아,
     * 기간 자체에 상한을 둔다. 1년이면 실제 사용(월·분기 조회)을 넘어선다.
     */
    private void validateRange(LocalDate start, LocalDate end) {
        if (start.isAfter(end) || ChronoUnit.DAYS.between(start, end) > MAX_QUERY_RANGE_DAYS) {
            throw new BusinessException(ErrorCode.DATE_RANGE_TOO_WIDE);
        }
    }

    /** 취소 계열 조건부 전이 — rowcount 0이면 이미 처리됨 */
    private void claimCancelTransition(Long leaveId, RequestStatus expected, RequestStatus next, String reason) {
        if (leaveRequestRepository.updateStatusToCancelIfCurrent(leaveId, expected, next, reason) == 0) {
            throw new BusinessException(ErrorCode.ALREADY_PROCESSED);
        }
    }

    /** 조건부 전이 후 재조회해 선차감 복구 + 이력 기록 (즉시 취소 경로) */
    private void restoreAndRecord(Long leaveId, RequestAction action, String reason) {
        LeaveRequest fresh = findLeaveOrThrow(leaveId);
        restoreCurrentYearPortion(fresh);
        saveHistory(fresh, fresh.getUser(), action, reason);
    }

    /**
     * 선차감 복구 — <b>현재 기산연도에 남아 있는 몫만</b> 되돌린다 (리뷰 I-10).
     *
     * <p>기산일 리셋이 {@code use_days}를 "기산일 이후 날짜"로만 다시 채우므로(docs/09 §5),
     * 기산일을 걸친 신청을 전체 복구하면 이전 연도 몫이 되살아나 연차가 공짜로 생긴다 —
     * {@code 2/28~3/2} 신청이 3/1 리셋 뒤 취소되면 {@code use_days}가 −1이 됐다.
     *
     * <p>반려·즉시 취소·소급취소 승인 <b>세 경로가 이 하나를 쓴다.</b> 경로마다 따로 계산하면
     * 어느 하나가 갈라진다 — 같은 종류의 산재가 리뷰 I-1의 원인이었다.
     */
    private void restoreCurrentYearPortion(LeaveRequest leave) {
        User owner = leave.getUser();
        owner.restoreLeave(leave.daysOnOrAfter(owner.getLastResetDate()));
    }

    /**
     * 신청 날짜 검증 — 개수 상한·주말·과거·공휴일 거부 (리뷰 I-6).
     *
     * <p>개수 상한을 <b>중복 제거 전 원본 개수</b>로 본다 — 같은 날짜를 수백 개 담은 요청도
     * 여기서 막아야 한다 (중복 제거는 LeaveRequest.create가 한다).
     *
     * <p>공휴일은 DB 캐시로 판정한다. 그 해 공휴일이 적재돼 있지 않으면 빈 집합이 와서
     * <b>검증이 느슨해질 뿐 신청이 막히지는 않는다</b> — 외부 API 장애가 연차 신청을
     * 중단시키면 안 되기 때문이다 (HolidayService 참고).
     */
    /**
     * 날짜 충돌 검사 — <b>하루 정원 1.0일</b> 기준 (리뷰 I-7).
     *
     * <p>예전에는 날짜 교집합만 보고 거부했다. 그래서 <b>같은 날 오전 반차 + 오후 반차</b>가
     * 409로 막혔다 — 합계 1.0일로 정상적인 사용 패턴인데도 오전 반차를 낸 뒤에는
     * 오후 반차를 낼 방법이 없었다.
     *
     * <p>규칙 두 개로 바꿨다:
     * <ol>
     *   <li><b>같은 종류 중복 금지</b> — 오전 반차를 두 번 쓸 수는 없다. 합계 규칙만 두면
     *       {@code HALF_AM + HALF_AM = 1.0}이 통과한다</li>
     *   <li><b>날짜별 합계 1.0일 초과 금지</b> — 연차(1.0)는 어떤 것과도 겹칠 수 없고
     *       반차(0.5) 둘은 종류가 다르면 겹칠 수 있다</li>
     * </ol>
     *
     * <p>집계를 SQL이 아니라 자바에서 하는 이유: 종류별 단가가 {@code LeaveType}의 도메인 값이라
     * JPQL에 단가를 다시 적으면 두 곳이 갈라진다 (같은 종류의 산재가 리뷰 I-1의 원인이었다).
     * 겹치는 신청만 좁혀 온 뒤 계산하므로 대상 건수도 작다.
     */
    private void validateNoDateConflict(User applicant, LeaveType requestedType, List<LocalDate> dates) {
        List<LeaveRequest> overlapping =
                leaveRequestRepository.findOverlapping(applicant, dates, ACTIVE_STATUSES);
        if (overlapping.isEmpty()) {
            return;
        }
        Set<LocalDate> requested = new HashSet<>(dates);
        Map<LocalDate, BigDecimal> occupiedDays = new HashMap<>();
        Map<LocalDate, Set<LeaveType>> occupiedTypes = new HashMap<>();
        for (LeaveRequest existing : overlapping) {
            for (LocalDate date : existing.getDates()) {
                if (!requested.contains(date)) {
                    continue; // 겹치지 않는 날짜는 이 신청의 정원과 무관하다
                }
                occupiedDays.merge(date, existing.getLeaveType().getDaysPerDate(), BigDecimal::add);
                occupiedTypes.computeIfAbsent(date, key -> new HashSet<>()).add(existing.getLeaveType());
            }
        }
        BigDecimal requestedPerDate = requestedType.getDaysPerDate();
        for (LocalDate date : requested) {
            if (occupiedTypes.getOrDefault(date, Set.of()).contains(requestedType)) {
                throw new BusinessException(ErrorCode.OVERLAPPING_LEAVE_REQUEST);
            }
            BigDecimal total = occupiedDays.getOrDefault(date, BigDecimal.ZERO).add(requestedPerDate);
            if (total.compareTo(FULL_DAY) > 0) {
                throw new BusinessException(ErrorCode.OVERLAPPING_LEAVE_REQUEST);
            }
        }
    }

    private void validateDates(List<LocalDate> dates) {
        if (dates.size() > policyConfigReader.getInt(PolicyConfigKey.LEAVE_MAX_DATES_PER_REQUEST)) {
            throw new BusinessException(ErrorCode.TOO_MANY_LEAVE_DATES);
        }
        LocalDate today = LocalDate.now(KST);
        // 값싼 검사(주말·과거)를 먼저 끝낸다 — 어차피 거부될 요청 때문에 DB를 볼 이유가 없다
        for (LocalDate date : dates) {
            DayOfWeek dayOfWeek = date.getDayOfWeek();
            if (dayOfWeek == DayOfWeek.SATURDAY || dayOfWeek == DayOfWeek.SUNDAY) {
                throw new BusinessException(ErrorCode.WEEKEND_NOT_ALLOWED);
            }
            if (date.isBefore(today)) {
                throw new BusinessException(ErrorCode.PAST_DATE_NOT_ALLOWED);
            }
        }
        // 공휴일만 DB를 본다. 날짜 수만큼 개별 조회하지 않도록 IN 한 번으로 받는다
        Set<LocalDate> holidays = holidayService.findHolidayDates(dates);
        if (!holidays.isEmpty()) {
            throw new BusinessException(ErrorCode.HOLIDAY_NOT_ALLOWED);
        }
    }

    /** 기본 승인자 = 소속 부서 팀장. 미배정·공석·팀장 퇴직·본인이 팀장이면 SYSTEM_ADMIN fallback (검증 Y-3) */
    // 승인자 결정은 ApproverResolver 하나로 모았다 (리뷰 I-5) — 복리후생과 같은 규칙이어야 한다.
    // 예전에는 두 서비스에 같은 메서드가 복사돼 있었고 그 3~4줄에 결함 3개가 밀집해 있었다.

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
