package com.mlsoft.backend.domain.holiday.credential;

/** 공휴일 API 자격 증명의 안전한 관리자 조회 형태. */
public record HolidayApiCredentialMaskDto(
        String provider,
        String maskedKey,
        boolean active
) {
}
