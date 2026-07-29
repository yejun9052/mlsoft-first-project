package com.mlsoft.backend.domain.policy.dto;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotNull;

import java.math.BigDecimal;

/**
 * 연차 정책 일수 수정 요청 (PATCH /api/admin/leave-policies/{id}, SA 전용 — docs/03).
 * description은 선택 입력 — 미포함(null) 시 기존 값을 유지한다.
 */
public record LeavePolicyUpdateRequest(
        @NotNull(message = "연차 일수를 입력해주세요.")
        @DecimalMin(value = "0.0", message = "연차 일수는 0일 이상이어야 합니다.")
        BigDecimal annualLeaveDays,

        String description
) {
}
