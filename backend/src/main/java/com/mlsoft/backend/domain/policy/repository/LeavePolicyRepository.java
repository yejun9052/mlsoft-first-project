package com.mlsoft.backend.domain.policy.repository;

import com.mlsoft.backend.domain.policy.entity.LeavePolicy;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

/**
 * 근속년수별 연차 정책 저장소.
 */
public interface LeavePolicyRepository extends JpaRepository<LeavePolicy, Long> {

    /** 근속년수로 정책 조회 (기산일 리셋·온보딩 연차 산정) */
    Optional<LeavePolicy> findByYearsOfService(int yearsOfService);
}
