package com.mlsoft.backend.domain.welfare.repository;

import com.mlsoft.backend.domain.welfare.entity.WelfareRequest;
import org.springframework.data.jpa.repository.JpaRepository;

/**
 * 복리후생 신청 저장소.
 */
public interface WelfareRequestRepository extends JpaRepository<WelfareRequest, Long> {
}
