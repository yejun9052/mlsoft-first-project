package com.mlsoft.backend.global.entity;

import jakarta.persistence.Column;
import jakarta.persistence.EntityListeners;
import jakarta.persistence.MappedSuperclass;
import lombok.Getter;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.jpa.domain.support.AuditingEntityListener;

import java.time.LocalDateTime;

/**
 * 생성 시각 자동 관리 공통 엔티티 (@EnableJpaAuditing — BackendApplication).
 * 설계 원문의 created_at DATE는 감사 목적을 고려해 DATETIME(LocalDateTime)으로 확정 (docs/02 검토 메모 1).
 */
@Getter
@MappedSuperclass
@EntityListeners(AuditingEntityListener.class)
public abstract class BaseTimeEntity {

    @CreatedDate
    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;
}
