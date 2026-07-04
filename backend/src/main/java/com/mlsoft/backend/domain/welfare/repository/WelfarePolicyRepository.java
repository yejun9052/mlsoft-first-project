package com.mlsoft.backend.domain.welfare.repository;

import com.mlsoft.backend.domain.welfare.entity.WelfarePolicy;
import org.springframework.data.jpa.repository.JpaRepository;

/**
 * 복리후생 정책 저장소.
 */
public interface WelfarePolicyRepository extends JpaRepository<WelfarePolicy, Long> {
}
