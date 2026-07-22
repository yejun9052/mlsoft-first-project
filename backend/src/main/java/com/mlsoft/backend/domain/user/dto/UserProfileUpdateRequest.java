package com.mlsoft.backend.domain.user.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

import java.time.LocalDate;

/**
 * 내 정보 수정 요청 (PATCH /api/users/me — docs/03).
 * 이름·생일만 수정 가능 — 연차·부서·권한 등은 관리자 전용 API로 분리한다.
 */
public record UserProfileUpdateRequest(
        @NotBlank(message = "이름을 입력해주세요.")
        String name,

        @NotNull(message = "생일을 입력해주세요.")
        LocalDate birthDay
) {
}
