package com.mlsoft.backend.domain.user.dto;

import com.mlsoft.backend.domain.user.entity.Role;
import jakarta.validation.constraints.NotNull;

/**
 * 역할·부서 동시 변경 요청
 * (PATCH /api/users/{id}/role-and-department, SA 전용).
 *
 * <p>미배정 사원을 팀장으로 올릴 때 두 값을 한 트랜잭션으로 바꾸기 위한 계약이다.
 * 기존 역할·부서 개별 변경 API는 각 화면의 독립 변경을 위해 그대로 유지한다.
 */
public record RoleDepartmentUpdateRequest(
        @NotNull(message = "권한을 선택해주세요.")
        Role role,

        @NotNull(message = "부서를 선택해주세요.")
        Long departmentId
) {
}
