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

/**
 * 연차 시스템 설정 key-value (docs/02 3-11 leave_policy_config).
 * 초기 키: advance_leave_enabled / reminder_list_days / reminder_auto_cycle
 * 값은 문자열로 저장하고 서비스에서 파싱한다.
 */
@Entity
@Table(name = "leave_policy_config")
@Getter
@Builder
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor(access = AccessLevel.PRIVATE)
public class LeavePolicyConfig {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /** 설정 키 */
    @Column(nullable = false, unique = true)
    private String name;

    /** 설정 값 (문자열 저장) */
    @Column(nullable = false)
    private String value;

    /** 설정 생성 */
    public static LeavePolicyConfig create(String name, String value) {
        return LeavePolicyConfig.builder()
                .name(name)
                .value(value)
                .build();
    }

    /** 설정 값 변경 (관리자 화면) */
    public void updateValue(String value) {
        this.value = value;
    }
}
