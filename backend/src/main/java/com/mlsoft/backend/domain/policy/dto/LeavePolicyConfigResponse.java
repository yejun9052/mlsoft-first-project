package com.mlsoft.backend.domain.policy.dto;

import com.mlsoft.backend.domain.policy.entity.LeavePolicyConfig;

/**
 * 연차 시스템 설정 응답 (GET/PUT /api/admin/configs — docs/03).
 */
public record LeavePolicyConfigResponse(
        Long id,
        String name,
        String value
) {

    public static LeavePolicyConfigResponse of(LeavePolicyConfig config) {
        return new LeavePolicyConfigResponse(config.getId(), config.getName(), config.getValue());
    }
}
