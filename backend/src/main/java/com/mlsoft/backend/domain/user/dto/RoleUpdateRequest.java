package com.mlsoft.backend.domain.user.dto;

import com.mlsoft.backend.domain.user.entity.Role;
import jakarta.validation.constraints.NotNull;

/**
 * 권한 변경 요청 (PATCH /api/users/{id}/role, SA 전용 — docs/03).
 */
public record RoleUpdateRequest(
        @NotNull(message = "권한을 선택해주세요.")
        Role role
) {
}
