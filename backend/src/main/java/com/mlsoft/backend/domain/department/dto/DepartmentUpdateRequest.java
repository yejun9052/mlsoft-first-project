package com.mlsoft.backend.domain.department.dto;

import jakarta.validation.constraints.NotBlank;

/**
 * 부서 수정 요청 (PUT /api/departments/{id}, SA 전용 — docs/03).
 * 전체 갱신 방식 — leaderId 미포함 시 팀장 공석으로 처리한다(서비스 책임).
 */
public record DepartmentUpdateRequest(
        @NotBlank(message = "부서명을 입력해주세요.")
        String name,

        @NotBlank(message = "부서 설명을 입력해주세요.")
        String description,

        Long leaderId,

        Long parentId
) {
}
