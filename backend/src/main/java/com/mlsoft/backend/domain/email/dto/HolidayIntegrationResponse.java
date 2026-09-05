package com.mlsoft.backend.domain.email.dto;

/** 공휴일 연동 관리자 조회 응답. */
public record HolidayIntegrationResponse(
        String provider,
        String maskedKey,
        boolean active
) {
}
