package com.mlsoft.backend.domain.welfare.repository;

import com.mlsoft.backend.domain.welfare.entity.WelfareActionHistory;
import org.springframework.data.jpa.repository.JpaRepository;

/**
 * 복리후생 처리 이력 저장소.
 */
public interface WelfareActionHistoryRepository extends JpaRepository<WelfareActionHistory, Long> {
}
