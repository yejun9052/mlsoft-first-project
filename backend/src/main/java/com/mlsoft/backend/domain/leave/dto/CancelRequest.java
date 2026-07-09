package com.mlsoft.backend.domain.leave.dto;

import jakarta.validation.constraints.NotBlank;

/**
 * 연차 취소 요청 (POST /api/leaves/{id}/cancel — docs/03).
 * 미래 날짜만 포함이면 즉시 취소, 과거 날짜 포함이면 소급취소(승인 대기)로 분기한다.
 */
public record CancelRequest(
        @NotBlank(message = "취소 사유를 입력해주세요.")
        String reason
) {
}
