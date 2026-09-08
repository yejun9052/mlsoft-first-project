package com.mlsoft.backend.domain.user.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/** 퇴직자 파기 보류 요청 (POST /api/users/{id}/purge-hold). */
public record PurgeHoldRequest(
        @NotBlank(message = "파기 보류 사유를 입력해주세요.")
        @Size(max = 255, message = "파기 보류 사유는 255자 이내로 입력해주세요.")
        String reason
) {
}
