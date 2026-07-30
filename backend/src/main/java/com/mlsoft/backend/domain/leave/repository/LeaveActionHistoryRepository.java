package com.mlsoft.backend.domain.leave.repository;

import com.mlsoft.backend.domain.common.RequestAction;
import com.mlsoft.backend.domain.leave.entity.LeaveActionHistory;
import com.mlsoft.backend.domain.leave.entity.LeaveRequest;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

/**
 * 연차 처리 이력 저장소.
 *
 * <p>로그 목록(GET /api/leave-histories)은 한 행마다 처리자·신청자·신청 정보를 함께 노출하므로
 * LAZY 연관을 그대로 두면 페이지 크기만큼 추가 쿼리가 발생한다. 목록 조회 메서드는 전부
 * {@code @EntityGraph}로 필요한 연관을 함께 적재한다 (리뷰 D-2 N+1).
 */
public interface LeaveActionHistoryRepository extends JpaRepository<LeaveActionHistory, Long> {

    /** 해당 신청의 처리 이력 — 발생 순서(오름차순) (GET /api/leaves/{id}/histories) */
    List<LeaveActionHistory> findByLeaveRequestOrderByCreatedAtAsc(LeaveRequest leaveRequest);

    /** 전체 처리 로그 (GET /api/leave-histories, SA) */
    @Override
    @EntityGraph(attributePaths = {"actor", "user", "user.department", "leaveRequest"})
    Page<LeaveActionHistory> findAll(Pageable pageable);

    /** 전체 처리 로그 — action 필터 (GET /api/leave-histories?action=, SA) */
    @EntityGraph(attributePaths = {"actor", "user", "user.department", "leaveRequest"})
    Page<LeaveActionHistory> findByAction(RequestAction action, Pageable pageable);

    /** 팀 처리 로그 — 신청자 소속 부서 기준 (GET /api/leave-histories/my-team, TL) */
    @EntityGraph(attributePaths = {"actor", "user", "user.department", "leaveRequest"})
    Page<LeaveActionHistory> findByUserDepartmentId(Long departmentId, Pageable pageable);

    /** 팀 처리 로그 — action 필터 (GET /api/leave-histories/my-team?action=, TL) */
    @EntityGraph(attributePaths = {"actor", "user", "user.department", "leaveRequest"})
    Page<LeaveActionHistory> findByUserDepartmentIdAndAction(Long departmentId, RequestAction action,
                                                             Pageable pageable);
}
