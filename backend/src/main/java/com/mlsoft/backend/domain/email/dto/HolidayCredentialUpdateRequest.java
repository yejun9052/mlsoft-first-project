package com.mlsoft.backend.domain.email.dto;

import jakarta.validation.constraints.NotBlank;

/** 공휴일 API 키 저장 요청. */
public record HolidayCredentialUpdateRequest(
        @NotBlank(message = "공휴일 API 키를 입력해주세요.") String apiKey
) {
}
