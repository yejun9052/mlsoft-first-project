package com.mlsoft.backend.domain.leave.repository;

import com.mlsoft.backend.domain.common.RequestStatus;
import com.mlsoft.backend.domain.leave.entity.LeaveRequest;
import com.mlsoft.backend.domain.leave.entity.LeaveType;
import com.mlsoft.backend.domain.user.entity.User;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.Collection;
import java.util.List;

/**
 * 연차 신청 저장소.
 * - 상태 전이(승인·반려·취소)는 반드시 {@link #updateStatusIfCurrent}/{@link #updateStatusToCancelIfCurrent}의
 *   조건부 갱신(WHERE status = 기대상태)으로 처리해 primary/sub 동시 처리를 1행만 claim 시킨다 (검증 R-5).
 *
 * <h3>N+1 대책 (리뷰 D-2)</h3>
 * 목록 조회는 {@code @EntityGraph}로 <b>to-one 연관만</b> 함께 적재한다 —
 * 신청자·부서·승인자 2명. 응답 DTO({@code LeaveResponse}·{@code LeaveCalendarResponse})가
 * 행마다 이 이름들을 읽으므로 LAZY로 두면 페이지 크기 20에서 추가 쿼리가 80회까지 붙었다.
 *
 * <p><b>{@code dates}는 그래프에 넣지 않는다.</b> 컬렉션을 {@code @EntityGraph}에 넣고
 * {@code Pageable}을 쓰면 Hibernate가 전체를 메모리로 올려 페이징한다({@code HHH90003004}).
 * 그래서 {@code dates}만 {@code @BatchSize(50)}로 분리했고, 페이지 크기 20이면 추가 조회 1회로 끝난다.
 */
public interface LeaveRequestRepository extends JpaRepository<LeaveRequest, Long> {

    /** 퇴직자 파기 — 신청 사유만 벌크 익명화하고 신청 행·연차 통계는 유지한다. */
    @Modifying(flushAutomatically = true)
    @Query("update LeaveRequest l set l.requestReason = null where l.user.id = :userId")
    int anonymizeRequestReasonsByUserId(@Param("userId") Long userId);

    /** 내 신청 내역 (GET /api/leaves/me) */
    @EntityGraph(attributePaths = {"user", "user.department", "primaryApprover", "subApprover"})
    Page<LeaveRequest> findByUser(User user, Pageable pageable);

    /** 재입사 처리 — 이전 근속의 선차감이 유지된 대기 신청을 종결하기 위한 조회. */
    List<LeaveRequest> findByUserAndStatusIn(User user, Collection<RequestStatus> statuses);

    /** 내 신청 내역 — status 필터 */
    @EntityGraph(attributePaths = {"user", "user.department", "primaryApprover", "subApprover"})
    Page<LeaveRequest> findByUserAndStatus(User user, RequestStatus status, Pageable pageable);

    /** 대기 중 사용 개수 합 — 요약의 "대기" 표기 (없으면 0) */
    @Query("select coalesce(sum(lr.days), 0) from LeaveRequest lr "
            + "where lr.user = :user and lr.status = :status")
    BigDecimal sumDaysByUserAndStatus(@Param("user") User user, @Param("status") RequestStatus status);

    /**
     * 날짜 중복 검사 — 신청자의 활성 상태(PENDING·APPROVED·CANCEL_PENDING) 건 중 요청 날짜와 겹치는 것.
     * leave_dates를 조인해 하나라도 교집합이 있으면 반환한다.
     */
    @Query("select distinct lr from LeaveRequest lr join lr.dates d "
            + "where lr.user = :user and lr.status in :statuses and d in :dates")
    List<LeaveRequest> findOverlapping(@Param("user") User user,
                                       @Param("dates") Collection<LocalDate> dates,
                                       @Param("statuses") Collection<RequestStatus> statuses);

    /** 내가 승인자(primary 또는 sub)인 대기 목록 — PENDING + 소급취소대기 (GET /api/leaves/pending) */
    @EntityGraph(attributePaths = {"user", "user.department", "primaryApprover", "subApprover"})
    @Query("select lr from LeaveRequest lr "
            + "where (lr.primaryApprover = :approver or lr.subApprover = :approver) "
            + "and lr.status in :statuses")
    Page<LeaveRequest> findPendingForApprover(@Param("approver") User approver,
                                              @Param("statuses") Collection<RequestStatus> statuses,
                                              Pageable pageable);

    /**
     * 캘린더 — 특정 상태 &amp; 날짜범위와 겹치는 전체 건 (GET /api/leaves/calendar).
     * keyword(신청자명 부분일치)·departmentId는 둘 다 선택 — null이면 조건이 무력화된다.
     */
    @EntityGraph(attributePaths = {"user", "user.department"})
    @Query("select distinct lr from LeaveRequest lr join lr.dates d "
            + "where lr.status in :statuses and d between :start and :end "
            + "and (:keyword is null or lower(lr.user.name) like lower(concat('%', :keyword, '%'))) "
            + "and (:departmentId is null or lr.user.department.id = :departmentId)")
    List<LeaveRequest> findInDateRange(@Param("statuses") Collection<RequestStatus> statuses,
                                       @Param("start") LocalDate start,
                                       @Param("end") LocalDate end,
                                       @Param("keyword") String keyword,
                                       @Param("departmentId") Long departmentId);

    /**
     * 개인 히트맵 원자료 — 승인 완료 건만 날짜 범위로 가져온다.
     * 날짜별 단가는 LeaveType이 단일 출처이므로 서비스에서 BigDecimal로 합산한다.
     */
    @EntityGraph(attributePaths = {"user"})
    @Query("select distinct lr from LeaveRequest lr join lr.dates d "
            + "where lr.user = :user and lr.status = :status and d between :start and :end")
    List<LeaveRequest> findByUserAndStatusInDateRange(@Param("user") User user,
                                                      @Param("status") RequestStatus status,
                                                      @Param("start") LocalDate start,
                                                      @Param("end") LocalDate end);

    /** 팀 현황 — 특정 부서원의 날짜범위와 겹치는 건 (GET /api/leaves/team) */
    @EntityGraph(attributePaths = {"user", "user.department"})
    @Query("select distinct lr from LeaveRequest lr join lr.dates d "
            + "where lr.user.department.id = :departmentId and lr.status in :statuses "
            + "and d between :start and :end")
    List<LeaveRequest> findByDepartmentInDateRange(@Param("departmentId") Long departmentId,
                                                   @Param("statuses") Collection<RequestStatus> statuses,
                                                   @Param("start") LocalDate start,
                                                   @Param("end") LocalDate end);

    /** 전체 신청 목록 — status·keyword(신청자명) 필터 (GET /api/leaves, SA) */
    @EntityGraph(attributePaths = {"user", "user.department", "primaryApprover", "subApprover"})
    @Query("select lr from LeaveRequest lr "
            + "where (:status is null or lr.status = :status) "
            + "and (:keyword is null or lr.user.name like concat('%', :keyword, '%'))")
    Page<LeaveRequest> findForAdmin(@Param("status") RequestStatus status,
                                    @Param("keyword") String keyword,
                                    Pageable pageable);

    /**
     * 상태 조건부 전이 (승인·반려·소급취소 승인/반려) — 기대 상태일 때만 1행 갱신.
     * 반환 0이면 이미 다른 승인자가 처리한 것 → 서비스가 ALREADY_PROCESSED 로 변환 (검증 R-5).
     * 벌크 갱신이므로 실행 전 flush, 실행 후 영속성 컨텍스트를 정리하고 필요한 엔티티를 재조회한다.
     */
    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query("update LeaveRequest lr set lr.status = :next "
            + "where lr.id = :id and lr.status = :expected")
    int updateStatusIfCurrent(@Param("id") Long id,
                              @Param("expected") RequestStatus expected,
                              @Param("next") RequestStatus next);

    /** 취소 전이 — 상태와 취소 사유를 함께 조건부 갱신 (즉시 취소 / 소급취소 요청 공용) */
    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query("update LeaveRequest lr set lr.status = :next, lr.cancelReason = :reason "
            + "where lr.id = :id and lr.status = :expected")
    int updateStatusToCancelIfCurrent(@Param("id") Long id,
                                      @Param("expected") RequestStatus expected,
                                      @Param("next") RequestStatus next,
                                      @Param("reason") String reason);

    /**
     * 기산일 이후 날짜를 종류별로 센다 — 미래 승인분 재차감의 원자료 (docs/09 §5).
     * 단가를 곱하는 것은 {@link #sumPreDeductedDaysOnOrAfter}가 한다.
     */
    @Query("select lr.leaveType, count(d) from LeaveRequest lr join lr.dates d "
            + "where lr.user = :user and lr.status in :statuses and d >= :from "
            + "group by lr.leaveType")
    List<Object[]> countDatesOnOrAfterByType(@Param("user") User user,
                                             @Param("from") LocalDate from,
                                             @Param("statuses") Collection<RequestStatus> statuses);

    /**
     * 반열린 날짜 구간 {@code [from, toExclusive)} 안의 날짜를 종류별로 센다.
     * 시작일은 포함하고 종료일은 제외해 회차 경계일이 두 회차에 중복 집계되지 않게 한다.
     */
    @Query("select lr.leaveType, count(d) from LeaveRequest lr join lr.dates d "
            + "where lr.user = :user and lr.status in :statuses and d >= :from and d < :toExclusive "
            + "group by lr.leaveType")
    List<Object[]> countDatesWithinByType(@Param("user") User user,
                                          @Param("statuses") List<RequestStatus> statuses,
                                          @Param("from") LocalDate from,
                                          @Param("toExclusive") LocalDate toExclusive);

    /**
     * 기산일 이후 선차감 유지분 합계 — 리셋의 {@code carriedUse} (docs/09 §5).
     *
     * <p><b>신청 단위가 아니라 날짜 단위</b>로 센다. 신청 단위로 하면 {@code 2/28~3/2}처럼 기산일을
     * 걸친 건에서 이전 연도에 이미 쓴 2/28까지 새 연도에 다시 차감돼 사원이 손해를 본다.
     *
     * <p>단가를 JPQL의 CASE로 쓰지 않고 자바에서 곱하는 이유는 {@link LeaveType}이 단가의 단일
     * 출처이기 때문이다 — 쿼리 문자열에 0.5를 적으면 종류가 늘 때 여기가 조용히 틀린다.
     *
     * @param statuses 선차감이 <b>유지되고 있는</b> 상태만 (APPROVED·PENDING·CANCEL_PENDING).
     *                 CANCELLED·REJECTED는 이미 복구됐으므로 넣으면 이중 계상이 된다
     */
    default BigDecimal sumPreDeductedDaysOnOrAfter(User user, LocalDate from,
                                                   Collection<RequestStatus> statuses) {
        return countDatesOnOrAfterByType(user, from, statuses).stream()
                .map(row -> ((LeaveType) row[0]).getDaysPerDate()
                        .multiply(BigDecimal.valueOf((Long) row[1])))
                .reduce(BigDecimal.ZERO, BigDecimal::add);
    }

    /**
     * 반열린 날짜 구간의 선차감 유지분 합계.
     * 날짜별 종류의 단가를 자바에서 곱해 합산한다.
     */
    default BigDecimal sumPreDeductedDaysWithin(User user, LocalDate from, LocalDate toExclusive,
                                                List<RequestStatus> statuses) {
        return countDatesWithinByType(user, statuses, from, toExclusive).stream()
                .map(row -> ((LeaveType) row[0]).getDaysPerDate()
                        .multiply(BigDecimal.valueOf((Long) row[1])))
                .reduce(BigDecimal.ZERO, BigDecimal::add);
    }

    /** 이 사람이 primary 승인자인 대기 건 — 퇴직 이관 대상 조회 (PENDING·CANCEL_PENDING, docs/01 2-9) */
    List<LeaveRequest> findByPrimaryApproverAndStatusIn(User primaryApprover, Collection<RequestStatus> statuses);

    /** 이 사람이 sub 승인자인 대기 건 — 퇴직 이관 대상 조회 (PENDING·CANCEL_PENDING, docs/01 2-9) */
    List<LeaveRequest> findBySubApproverAndStatusIn(User subApprover, Collection<RequestStatus> statuses);
}
