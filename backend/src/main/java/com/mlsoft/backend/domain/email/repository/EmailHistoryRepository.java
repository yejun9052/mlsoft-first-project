package com.mlsoft.backend.domain.email.repository;

import com.mlsoft.backend.domain.email.entity.EmailHistory;
import org.springframework.data.jpa.repository.JpaRepository;

/**
 * 이메일 발송 이력 저장소 — FAILED 재시도 조회 등은 기능 구현 시 추가.
 */
public interface EmailHistoryRepository extends JpaRepository<EmailHistory, Long> {
}
