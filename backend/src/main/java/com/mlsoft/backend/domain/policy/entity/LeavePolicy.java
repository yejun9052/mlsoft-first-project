package com.mlsoft.backend.domain.policy.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;

/**
 * 근속년수별 연차 정책 (docs/02 3-10 leave_policy).
 * 근로기준법 공식: MIN(15 + (년차-1)/2, 25) — 1~2년차 15일, 21년차 이상 25일.
 * created_at 없는 독립 테이블이므로 BaseTimeEntity 미상속.
 */
@Entity
@Table(name = "leave_policy")
@Getter
@Builder
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor(access = AccessLevel.PRIVATE)
public class LeavePolicy {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /** 활성화 여부 */
    @Column(nullable = false)
    private boolean active;

    /** 연차 개수 */
    @Column(nullable = false, precision = 4, scale = 1)
    private BigDecimal annualLeaveDays;

    /** 정책 설명 */
    @Column(nullable = false)
    private String description;

    /** N년차 */
    @Column(nullable = false, unique = true)
    private int yearsOfService;

    /** 정책 생성 */
    public static LeavePolicy create(int yearsOfService, BigDecimal annualLeaveDays, String description) {
        return LeavePolicy.builder()
                .active(true)
                .yearsOfService(yearsOfService)
                .annualLeaveDays(annualLeaveDays)
                .description(description)
                .build();
    }
}
