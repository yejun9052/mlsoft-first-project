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
 * 연차 시스템 설정 key-value 행 (docs/02 3-11 leave_policy_config).
 *
 * <p>이 엔티티는 <b>값만</b> 들고 있다. 어떤 키가 존재하고 그 타입·기본값·허용 범위가 무엇인지는
 * {@link PolicyConfigKey} 카탈로그가 정의한다 — 값 검증도 그쪽 책임이다.
 * 값이 문자열인 것은 타입이 여러 가지이기 때문이고, 아무 문자열이나 들어와도 된다는 뜻이 아니다.
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
