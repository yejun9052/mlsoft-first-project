package com.mlsoft.backend.domain.user.dto;

import jakarta.validation.constraints.NotNull;

import java.math.BigDecimal;

/**
 * 연차 직접 설정 요청 (PATCH /api/users/{id}/base-days, SA 전용 — docs/03).
 * 음수 검증은 서비스 책임 (INVALID_INPUT_VALUE).
 */
public record BaseDaysUpdateRequest(
        @NotNull(message = "연차 일수를 입력해주세요.")
        BigDecimal baseDays
) {
}
