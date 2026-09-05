package com.mlsoft.backend.domain.email.dto;

/** 공휴일 API 검증 결과. */
public record HolidayVerifyResponse(
        boolean valid,
        int count
) {
}
