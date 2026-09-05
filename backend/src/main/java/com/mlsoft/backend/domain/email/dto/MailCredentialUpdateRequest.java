package com.mlsoft.backend.domain.email.dto;

import jakarta.validation.constraints.NotBlank;

/** SMTP 발신 계정 저장 요청. */
public record MailCredentialUpdateRequest(
        @NotBlank(message = "메일 사용자명을 입력해주세요.") String username,
        @NotBlank(message = "메일 비밀값을 입력해주세요.") String secret
) {
}
