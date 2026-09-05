package com.mlsoft.backend.domain.email.dto;

/** 메일 연동 관리자 조회 응답. */
public record MailIntegrationResponse(
        String provider,
        String username,
        String maskedSecret,
        boolean active
) {
}
