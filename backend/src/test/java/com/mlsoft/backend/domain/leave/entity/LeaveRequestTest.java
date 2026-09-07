package com.mlsoft.backend.domain.leave.entity;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * 연차 신청의 날짜 창 집계 단위 테스트.
 */
class LeaveRequestTest {

    @Test
    @DisplayName("창 집계 — 시작일은 포함하고 종료일은 제외한다")
    void daysWithin_시작일포함_종료일제외() {
        LocalDate from = LocalDate.of(2026, 3, 1);
        LeaveRequest leave = request(LeaveType.ANNUAL,
                List.of(from.minusDays(1), from, from.plusDays(1), from.plusDays(2)));

        assertDays("2.0", leave.daysWithin(from, from.plusDays(2)));
    }

    @Test
    @DisplayName("창 집계 — 기산일을 걸친 신청은 새 회차 날짜만 센다")
    void daysWithin_기산일경계신청_새회차만집계() {
        LocalDate resetDate = LocalDate.of(2026, 3, 1);
        LeaveRequest leave = request(LeaveType.ANNUAL,
                List.of(resetDate.minusDays(1), resetDate, resetDate.plusDays(1)));

        assertDays("2.0", leave.daysWithin(resetDate, resetDate.plusYears(1)));
    }

    @Test
    @DisplayName("창 집계 — 오전·오후 반차는 날짜당 0.5일로 센다")
    void daysWithin_반차종류별_날짜당반일() {
        LocalDate day = LocalDate.of(2026, 3, 1);
        for (LeaveType type : List.of(LeaveType.HALF_AM, LeaveType.HALF_PM)) {
            LeaveRequest leave = request(type, List.of(day));

            assertDays("0.5", leave.daysWithin(day, day.plusDays(1)));
        }
    }

    @Test
    @DisplayName("창 집계 — 시작일·종료일이 null이면 각각 하한·상한을 두지 않는다")
    void daysWithin_null경계_무제한으로집계() {
        LocalDate first = LocalDate.of(2026, 1, 1);
        LeaveRequest leave = request(LeaveType.ANNUAL,
                List.of(first, first.plusMonths(1), first.plusMonths(2)));

        assertDays("1.0", leave.daysWithin(null, first.plusMonths(1)));
        assertDays("2.0", leave.daysWithin(first.plusMonths(1), null));
        assertEquals(0, leave.getDays().compareTo(leave.daysWithin(null, null)));
    }

    @Test
    @DisplayName("창 집계 — 모든 날짜가 창 밖이면 0을 반환한다")
    void daysWithin_창밖날짜만있음_zero반환() {
        LocalDate from = LocalDate.of(2026, 3, 1);
        LeaveRequest leave = request(LeaveType.ANNUAL,
                List.of(from.minusDays(2), from.plusDays(2)));

        assertDays("0", leave.daysWithin(from, from.plusDays(2)));
    }

    private LeaveRequest request(LeaveType type, List<LocalDate> dates) {
        return LeaveRequest.create(null, type, dates, "테스트", null, null);
    }

    private void assertDays(String expected, BigDecimal actual) {
        assertEquals(0, new BigDecimal(expected).compareTo(actual), "실제 " + actual);
    }
}
