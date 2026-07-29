package com.mlsoft.backend.domain.policy.dto;

import jakarta.validation.constraints.NotBlank;

/**
 * 연차 시스템 설정 변경 요청 (PUT /api/admin/configs, SA 전용 — docs/03).
 * 값은 문자열로 저장하고 서비스에서 파싱하는 loosely-typed 설정이라 비어있지 않은지만 검증한다.
 */
public record LeavePolicyConfigUpdateRequest(
        @NotBlank(message = "설정 키를 입력해주세요.")
        String name,

        @NotBlank(message = "설정 값을 입력해주세요.")
        String value
) {
}
