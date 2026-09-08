package com.mlsoft.backend.domain.policy.dto;

import com.mlsoft.backend.domain.policy.entity.ConfigValueType;
import com.mlsoft.backend.domain.policy.entity.LeavePolicyConfig;
import com.mlsoft.backend.domain.policy.entity.PolicyConfigKey;
import com.mlsoft.backend.domain.policy.entity.PolicyConfigStatus;
import com.mlsoft.backend.domain.policy.entity.PolicyConfigKey.VisibilityCondition;

import java.math.BigDecimal;
import java.util.List;

/**
 * 연차 시스템 설정 응답 (GET/PUT /api/admin/configs — docs/03).
 *
 * <p>값(value)과 함께 <b>메타데이터를 같이 내려준다</b> — 타입·허용 범위·라벨·설명·동작 여부.
 * 이전에는 {@code {id, name, value}}만 내려주고 라벨·타입은 프론트({@code CONFIG_META})가
 * 키 이름별로 하드코딩했다. 그래서 서버에 설정을 추가하면 프론트를 함께 고치기 전까지
 * 관리자 화면에서 <b>보이지 않았다</b>(메타데이터 없는 키를 필터로 걸러냈다).
 *
 * <p>이제 프론트는 키 이름을 모른 채 {@code type}으로 컨트롤을 고르고 {@code min}/{@code max}로
 * 입력을 제한한다 — 설정 추가가 백엔드 enum 한 줄로 끝난다.
 */
public record LeavePolicyConfigResponse(
        /** DB 행 id — 아직 시딩되지 않은 키는 null */
        Long id,
        String name,
        /** 현재 값. DB 행이 없으면 카탈로그 기본값 */
        String value,
        ConfigValueType type,
        String defaultValue,
        /** INTEGER·DECIMAL 하한 (포함). 그 외 null */
        BigDecimal min,
        /** INTEGER·DECIMAL 상한 (포함). 그 외 null */
        BigDecimal max,
        /** 값 뒤에 붙일 단위 (없으면 null) */
        String unit,
        /** ENUM 선택지 (그 외 빈 리스트) */
        List<String> options,
        String label,
        String description,
        /** ACTIVE면 지금 동작하는 설정, PENDING_FEATURE면 값만 저장되고 읽는 기능이 아직 없다 */
        PolicyConfigStatus status,
        /** 의존 설정이 requiredValue일 때만 표시. null이면 항상 표시 */
        VisibilityCondition visibleWhen
) {

    /**
     * 카탈로그 항목 + 저장된 행으로 응답을 만든다.
     *
     * @param config 저장된 행. 아직 시딩되지 않았으면 null — 이때 값은 카탈로그 기본값을 쓴다
     */
    public static LeavePolicyConfigResponse of(PolicyConfigKey key, LeavePolicyConfig config) {
        return new LeavePolicyConfigResponse(
                (config != null) ? config.getId() : null,
                key.getKey(),
                (config != null) ? config.getValue() : key.getDefaultValue(),
                key.getType(),
                key.getDefaultValue(),
                key.getMin(),
                key.getMax(),
                key.getUnit(),
                key.getOptions(),
                key.getLabel(),
                key.getDescription(),
                key.getStatus(),
                key.getVisibleWhen()
        );
    }
}
