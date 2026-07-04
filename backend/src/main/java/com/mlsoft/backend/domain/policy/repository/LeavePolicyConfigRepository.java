package com.mlsoft.backend.domain.policy.repository;

import com.mlsoft.backend.domain.policy.entity.LeavePolicyConfig;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

/**
 * 연차 시스템 설정 저장소.
 */
public interface LeavePolicyConfigRepository extends JpaRepository<LeavePolicyConfig, Long> {

    /** 설정 키로 조회 (advance_leave_enabled 등) */
    Optional<LeavePolicyConfig> findByName(String name);
}
