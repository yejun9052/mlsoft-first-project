package com.mlsoft.backend.domain.leave.entity;

import com.mlsoft.backend.domain.common.RequestStatus;
import com.mlsoft.backend.domain.user.entity.User;
import com.mlsoft.backend.global.entity.BaseTimeEntity;
import com.mlsoft.backend.global.exception.BusinessException;
import com.mlsoft.backend.global.exception.ErrorCode;
import jakarta.persistence.CollectionTable;
import jakarta.persistence.Column;
import jakarta.persistence.ElementCollection;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.OrderBy;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.BatchSize;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

/**
 * 연차 신청 (docs/02 3-3 leave_requests + 3-4 leave_dates).
 * - 신청(PENDING) 시 use_days 선차감, 반려/취소 시 복구 — 차감·복구는 서비스에서 User 도메인 메서드 호출
 * - 승인은 primary/sub 병렬 선착순: 먼저 처리한 1명으로 종료, 이중 처리는 ALREADY_PROCESSED (검증 R-5)
 * - ⚠️ 여기의 상태 가드는 in-memory 검사일 뿐 — 서비스 계층은 반드시 status 조건부 갱신
 *   (UPDATE ... WHERE status='PENDING')으로 동시 처리를 차단해야 한다 (검증 R-5, 리포트 체크리스트 2)
 */
@Entity
// 조회 인덱스 (리뷰 D-1) — 목록 3종이 전부 "누구의 + 어떤 상태" 조합이다.
// FK 자동 생성 인덱스는 컬럼 하나뿐이라 상태까지 걸러 주지 못한다.
@Table(name = "leave_requests", indexes = {
        @Index(name = "idx_leave_requests_user_status", columnList = "user_id, status"),
        @Index(name = "idx_leave_requests_primary_status", columnList = "primary_approver_id, status"),
        @Index(name = "idx_leave_requests_sub_status", columnList = "sub_approver_id, status")
})
@Getter
@Builder
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor(access = AccessLevel.PRIVATE)
public class LeaveRequest extends BaseTimeEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /** 신청자 */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

    /** 연차/반차 */
    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private LeaveType leaveType;

    /** 사용 개수 (leave_type × 날짜 수) */
    @Column(nullable = false, precision = 4, scale = 1)
    private BigDecimal days;

    /**
     * 당겨쓰기 충당분 스냅샷 — <b>감사 기록 전용</b>이다. 이 신청이 잔여를 얼마나 초과했는지를 남긴다.
     * 한때 반려·취소 시 advance_days 복구의 근거였지만(검증 B2), advance_days가 파생값으로
     * 재계산되면서(User.syncAdvanceDays, 리뷰 I-1) 복구가 이 값에 의존하지 않게 됐다.
     */
    @Column(nullable = false, precision = 4, scale = 1)
    @Builder.Default
    private BigDecimal advanceUsedDays = BigDecimal.ZERO;

    /** 신청 사유 (필수) */
    @Column(nullable = false)
    private String requestReason;

    /** 취소 사유 */
    private String cancelReason;

    /** 기본 승인자 — 부서 팀장, 공석 시 SYSTEM_ADMIN fallback (검증 Y-3) */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "primary_approver_id", nullable = false)
    private User primaryApprover;

    /** 서브 승인자 — 재직 중 TEAM_LEADER·SYSTEM_ADMIN 중 신청자가 선택 (선택 사항) */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "sub_approver_id")
    private User subApprover;

    /** 신청 상태 */
    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private RequestStatus status;

    /** 사용 날짜 목록 (leave_dates 테이블) — 값 오름차순 조회 보장, (신청, 날짜) 중복은 DB 유니크로 차단 */
    @ElementCollection
    @CollectionTable(
            name = "leave_dates",
            joinColumns = @JoinColumn(name = "leave_requests_id"),
            uniqueConstraints = @UniqueConstraint(name = "uk_leave_dates_request_day",
                    columnNames = {"leave_requests_id", "day"}),
            // 캘린더 범위 조회와 리셋의 이월분 집계가 day로 스캔한다 (리뷰 D-1).
            // 위 UNIQUE는 (신청, 날짜) 순서라 day 단독 범위 검색에 쓸 수 없다 — 중복이 아니다.
            indexes = @Index(name = "idx_leave_dates_day", columnList = "day"))
    @OrderBy // 기본(값) 오름차순 — 시작일·종료일 판정이 순서에 의존하므로 명시 (검증 B5)
    @Column(name = "day", nullable = false)
    @Builder.Default
    // 목록 조회에서 신청 N건의 날짜를 개별 쿼리로 읽지 않게 배치로 묶는다 (리뷰 D-2 N+1).
    // 컬렉션이라 @EntityGraph로 함께 적재하면 페이징이 메모리에서 처리되므로 배치 fetch를 쓴다.
    @BatchSize(size = 50)
    private List<LocalDate> dates = new ArrayList<>();

    /**
     * 연차 신청 생성 — PENDING으로 시작 (선차감은 서비스에서 user.deductLeave 호출).
     * 중복 날짜는 제거 후 오름차순 정렬해 저장한다 — 같은 날짜 2회 전달 시 일수 이중 계산 방지 (검증 B5).
     */
    public static LeaveRequest create(User user, LeaveType leaveType, List<LocalDate> dates,
                                      String requestReason, User primaryApprover, User subApprover) {
        List<LocalDate> distinctDates = dates.stream().distinct().sorted().toList();
        return LeaveRequest.builder()
                .user(user)
                .leaveType(leaveType)
                .dates(new ArrayList<>(distinctDates))
                .days(leaveType.getDaysPerDate().multiply(BigDecimal.valueOf(distinctDates.size())))
                .requestReason(requestReason)
                .primaryApprover(primaryApprover)
                .subApprover(subApprover)
                .status(RequestStatus.PENDING)
                .build();
    }

    /**
     * 선차감 시 당겨쓰기로 충당된 일수 기록 (User.deductLeave 반환값) — 감사·조회 목적.
     * 복구 계산에는 쓰이지 않는다 (User.restoreLeave가 use_days만 되돌리고 advance는 재계산).
     */
    public void recordAdvanceUsage(BigDecimal advanceUsedDays) {
        this.advanceUsedDays = advanceUsedDays;
    }

    /**
     * 기준일 이후 날짜의 일수 — <b>지금 {@code use_days}에 남아 있는 몫</b>이다 (리뷰 I-10).
     *
     * <p>반려·취소 복구는 신청 전체가 아니라 이 값을 되돌려야 한다. 기산일 리셋은
     * {@code use_days}를 "기산일 이후 날짜"로만 다시 채우기 때문에(docs/09 §5 날짜 단위 재차감),
     * 기산일을 걸친 신청을 전체 복구하면 이전 연도 몫까지 되살아난다.
     *
     * <pre>
     * 2/28·3/1·3/2 신청(3일), 기산일 3/1
     *   리셋 후 use_days = 2.0  (3/1·3/2만)
     *   전체 복구 → 2.0 − 3.0 = −1.0   ← 연차 1일이 공짜로 생긴다
     *   이 메서드 → 2.0 − 2.0 = 0      ← 정확
     * </pre>
     *
     * @param boundary 최근 기산일. null(온보딩 미완료 등 기산일이 없는 예외 상태)이면 전체를 돌려준다
     */
    public BigDecimal daysOnOrAfter(LocalDate boundary) {
        if (boundary == null) {
            return days;
        }
        long count = dates.stream().filter(date -> !date.isBefore(boundary)).count();
        return leaveType.getDaysPerDate().multiply(BigDecimal.valueOf(count));
    }

    /**
     * 반열린 구간 {@code [from, toExclusive)} 안에 포함되는 날짜의 일수.
     *
     * <p>회차 경계일은 다음 회차의 첫날이므로 양쪽 회차에 겹쳐서 집계되면 안 된다.
     * 따라서 시작일은 포함하고 종료일은 제외하는 반열린 구간으로 경계를 표현한다.
     * {@code from}이 null이면 하한을, {@code toExclusive}가 null이면 상한을 두지 않는다.
     *
     * @param from 포함할 시작일. null이면 하한 없음
     * @param toExclusive 제외할 종료일. null이면 상한 없음
     * @return 구간 안 날짜 수에 날짜당 차감 일수를 곱한 값
     */
    public BigDecimal daysWithin(LocalDate from, LocalDate toExclusive) {
        if (from == null && toExclusive == null) {
            return days;
        }
        long count = dates.stream()
                .filter(date -> (from == null || !date.isBefore(from))
                        && (toExclusive == null || date.isBefore(toExclusive)))
                .count();
        return leaveType.getDaysPerDate().multiply(BigDecimal.valueOf(count));
    }

    /**
     * 승인 — PENDING에서만 가능 (선착순 이중 처리 방지).
     */
    public void approve() {
        validateStatus(RequestStatus.PENDING);
        this.status = RequestStatus.APPROVED;
    }

    /**
     * 반려 — PENDING에서만 가능. 선차감 복구는 서비스 책임.
     */
    public void reject() {
        validateStatus(RequestStatus.PENDING);
        this.status = RequestStatus.REJECTED;
    }

    /**
     * 즉시 취소 (docs/01 2-3(b)).
     * - PENDING: 날짜 무관 즉시 취소
     * - APPROVED: 미래 날짜만 포함된 경우 즉시 취소 (날짜 검증은 서비스 책임)
     */
    public void cancel(String cancelReason) {
        if (status != RequestStatus.PENDING && status != RequestStatus.APPROVED) {
            throw new BusinessException(ErrorCode.ALREADY_PROCESSED);
        }
        this.status = RequestStatus.CANCELLED;
        this.cancelReason = cancelReason;
    }

    /**
     * 소급 취소 요청 — APPROVED + 과거 날짜 포함 건은 승인자 승인 필요.
     */
    public void requestCancel(String cancelReason) {
        validateStatus(RequestStatus.APPROVED);
        this.status = RequestStatus.CANCEL_PENDING;
        this.cancelReason = cancelReason;
    }

    /**
     * 소급 취소 승인 — CANCELLED 확정. 선차감 복구는 서비스 책임.
     */
    public void approveCancel() {
        validateStatus(RequestStatus.CANCEL_PENDING);
        this.status = RequestStatus.CANCELLED;
    }

    /**
     * 소급 취소 거부 — APPROVED 복원.
     */
    public void rejectCancel() {
        validateStatus(RequestStatus.CANCEL_PENDING);
        this.status = RequestStatus.APPROVED;
    }

    // 상태 전이 가드 — 기대 상태가 아니면 이미 처리된 신청
    private void validateStatus(RequestStatus expected) {
        if (this.status != expected) {
            throw new BusinessException(ErrorCode.ALREADY_PROCESSED);
        }
    }

    /** 기본 승인자 재배정 — 팀장 퇴직 시 결재 이관 (갭분석 B-4, docs/01 2-9) */
    public void reassignPrimaryApprover(User newApprover) {
        this.primaryApprover = newApprover;
    }

    /** 서브 승인자 재배정 — 팀장 퇴직 시 결재 이관 (갭분석 B-4, docs/01 2-9) */
    public void reassignSubApprover(User newApprover) {
        this.subApprover = newApprover;
    }
}
