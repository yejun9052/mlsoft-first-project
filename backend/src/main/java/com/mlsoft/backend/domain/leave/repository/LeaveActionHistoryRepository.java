package com.mlsoft.backend.domain.leave.repository;

import com.mlsoft.backend.domain.leave.entity.LeaveActionHistory;
import com.mlsoft.backend.domain.leave.entity.LeaveRequest;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

/**
 * 연차 처리 이력 저장소.
 */
public interface LeaveActionHistoryRepository extends JpaRepository<LeaveActionHistory, Long> {

    /** 해당 신청의 처리 이력 — 발생 순서(오름차순) (GET /api/leaves/{id}/histories) */
    List<LeaveActionHistory> findByLeaveRequestOrderByCreatedAtAsc(LeaveRequest leaveRequest);
}
