package com.mlsoft.backend.domain.leave.repository;

import com.mlsoft.backend.domain.leave.entity.LeaveActionHistory;
import org.springframework.data.jpa.repository.JpaRepository;

/**
 * 연차 처리 이력 저장소.
 */
public interface LeaveActionHistoryRepository extends JpaRepository<LeaveActionHistory, Long> {
}
