package com.mlsoft.backend.domain.holiday.dto;

import com.mlsoft.backend.domain.holiday.entity.Holiday;

import java.time.LocalDate;

/**
 * 공휴일 응답 (GET /api/holidays).
 * 프론트가 쓰던 mock({@code date, name})과 같은 모양이라 화면 코드 변경이 최소로 끝난다.
 */
public record HolidayResponse(
        LocalDate date,
        String name
) {

    public static HolidayResponse of(Holiday holiday) {
        return new HolidayResponse(holiday.getDate(), holiday.getName());
    }
}
