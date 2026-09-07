package com.mlsoft.backend.domain.holiday.dto;

import com.mlsoft.backend.domain.holiday.client.HolidayApiClient;

/** 공휴일 동기화 내부 결과 — 외부 조회 outcome과 적재 건수를 함께 전달한다. */
public record HolidaySyncResult(
        HolidayApiClient.Outcome outcome,
        int count
) {
}
