package com.mlsoft.backend.domain.user.repository;

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
}
