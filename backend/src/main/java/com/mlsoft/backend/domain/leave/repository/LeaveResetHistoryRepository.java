package com.mlsoft.backend.domain.leave.repository;

import com.mlsoft.backend.domain.leave.entity.LeaveResetHistory;
import org.springframework.data.jpa.repository.JpaRepository;

/**
 * 기산일 리셋 이력 저장소.
 */
public interface LeaveResetHistoryRepository extends JpaRepository<LeaveResetHistory, Long> {
}
