package com.mlsoft.backend.domain.department.dto;

import jakarta.validation.constraints.NotBlank;

/**
 * 부서 생성 요청 (POST /api/departments, SA 전용 — docs/03).
 * leaderId·parentId는 선택 사항(공석·최상위 부서 허용)이라 @NotNull을 두지 않는다.
 */
public record DepartmentCreateRequest(
        @NotBlank(message = "부서명을 입력해주세요.")
        String name,

        @NotBlank(message = "부서 설명을 입력해주세요.")
        String description,

        Long leaderId,

        Long parentId
) {
}
