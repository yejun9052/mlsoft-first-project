package com.mlsoft.backend.domain.user.repository;

import com.mlsoft.backend.domain.user.entity.Role;
import com.mlsoft.backend.domain.user.entity.User;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

/**
 * 사원 저장소.
 */
public interface UserRepository extends JpaRepository<User, Long> {

    /** 이메일로 사원 조회 (OAuth 로그인 키) */
    Optional<User> findByEmail(String email);

    /** 이메일 가입 여부 (자동 가입 판별) */
    boolean existsByEmail(String email);

    /** 재직 중인 해당 권한의 최초(id 오름차순) 사용자 — primary 승인자 SYSTEM_ADMIN fallback (검증 Y-3) */
    Optional<User> findFirstByRoleAndIsActiveTrueOrderByIdAsc(Role role);
}
