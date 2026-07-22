package com.mlsoft.backend.domain.user.dto;

import jakarta.validation.constraints.NotNull;

/**
 * 부서 변경 요청 (PATCH /api/users/{id}/department, SA 전용 — docs/03).
 */
public record DepartmentAssignRequest(
        @NotNull(message = "부서를 선택해주세요.")
        Long departmentId
) {
}
