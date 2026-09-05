package com.mlsoft.backend.domain.holiday.dto;

/** 공휴일 동기화 결과 (POST /api/holidays/sync). */
public record HolidaySyncResponse(
        int year,
        int count
) {
}
