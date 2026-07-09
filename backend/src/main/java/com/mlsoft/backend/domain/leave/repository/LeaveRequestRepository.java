package com.mlsoft.backend.domain.leave.repository;

import com.mlsoft.backend.domain.common.RequestStatus;
import com.mlsoft.backend.domain.leave.entity.LeaveRequest;
import com.mlsoft.backend.domain.user.entity.User;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
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
 */
public interface LeaveRequestRepository extends JpaRepository<LeaveRequest, Long> {

    /** 내 신청 내역 (GET /api/leaves/me) */
    Page<LeaveRequest> findByUser(User user, Pageable pageable);

    /** 내 신청 내역 — status 필터 */
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
    @Query("select lr from LeaveRequest lr "
            + "where (lr.primaryApprover = :approver or lr.subApprover = :approver) "
            + "and lr.status in :statuses")
    Page<LeaveRequest> findPendingForApprover(@Param("approver") User approver,
                                              @Param("statuses") Collection<RequestStatus> statuses,
                                              Pageable pageable);

    /** 캘린더 — 특정 상태 & 날짜범위와 겹치는 전체 건 (GET /api/leaves/calendar) */
    @Query("select distinct lr from LeaveRequest lr join lr.dates d "
            + "where lr.status in :statuses and d between :start and :end")
    List<LeaveRequest> findInDateRange(@Param("statuses") Collection<RequestStatus> statuses,
                                       @Param("start") LocalDate start,
                                       @Param("end") LocalDate end);

    /** 팀 현황 — 특정 부서원의 날짜범위와 겹치는 건 (GET /api/leaves/team) */
    @Query("select distinct lr from LeaveRequest lr join lr.dates d "
            + "where lr.user.department.id = :departmentId and lr.status in :statuses "
            + "and d between :start and :end")
    List<LeaveRequest> findByDepartmentInDateRange(@Param("departmentId") Long departmentId,
                                                   @Param("statuses") Collection<RequestStatus> statuses,
                                                   @Param("start") LocalDate start,
                                                   @Param("end") LocalDate end);

    /** 전체 신청 목록 — status·keyword(신청자명) 필터 (GET /api/leaves, SA) */
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
}
