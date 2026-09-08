package com.mlsoft.backend.domain.user.dto;

import jakarta.validation.constraints.NotNull;

import java.time.LocalDate;

/** 재입사 처리 요청 (POST /api/users/{id}/rehire, SYSTEM_ADMIN 전용). */
public record RehireRequest(
        @NotNull(message = "재입사일을 입력해주세요.")
        LocalDate hireDate,

        Long departmentId
) {
}
