package com.mlsoft.backend.domain.welfare.entity;

import com.mlsoft.backend.global.entity.BaseTimeEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
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
 * 복리후생 정책 (docs/02 3-6 welfare_policies).
 * 삭제 대신 active=false 소프트 삭제 — 기존 신청의 근거 보존.
 */
@Entity
@Table(name = "welfare_policies")
@Getter
@Builder
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor(access = AccessLevel.PRIVATE)
public class WelfarePolicy extends BaseTimeEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /** 활성화 여부 */
    @Column(nullable = false)
    private boolean active;

    /** 카테고리 (결혼/출산/조의 등) */
    @Column(nullable = false)
    private String category;

    /** 대상 */
    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private WelfareTarget target;

    /** 제출자료 안내 */
    @Column(nullable = false)
    private String defaultEvidence;

    /** 부가 설명 */
    @Column(nullable = false)
    private String description;

    /** 기본 제공 일수 */
    @Column(nullable = false, precision = 4, scale = 1)
    private BigDecimal defaultDays;

    /** 정책 생성 */
    public static WelfarePolicy create(String category, WelfareTarget target, BigDecimal defaultDays,
                                       String defaultEvidence, String description) {
        return WelfarePolicy.builder()
                .active(true)
                .category(category)
                .target(target)
                .defaultDays(defaultDays)
                .defaultEvidence(defaultEvidence)
                .description(description)
                .build();
    }

    /** 정책 수정 — 구분·대상·부여일수·제출자료·설명 전체 갱신 (PATCH /api/welfare-policies/{id}) */
    public void update(String category, WelfareTarget target, BigDecimal defaultDays,
                       String defaultEvidence, String description) {
        this.category = category;
        this.target = target;
        this.defaultDays = defaultDays;
        this.defaultEvidence = defaultEvidence;
        this.description = description;
    }

    /** 정책 비활성화 (소프트 삭제) */
    public void deactivate() {
        this.active = false;
    }

    /** 정책 재활성화 */
    public void activate() {
        this.active = true;
    }
}
