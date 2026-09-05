package com.mlsoft.backend.domain.email.dto;

/** 관리자 외부 연동 설정 응답. */
public record IntegrationResponse(
        MailIntegrationResponse mail,
        HolidayIntegrationResponse holiday,
        boolean encryptionConfigured
) {
}
