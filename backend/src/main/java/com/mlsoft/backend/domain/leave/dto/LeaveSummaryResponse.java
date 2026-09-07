package com.mlsoft.backend.domain.leave.dto;

import com.mlsoft.backend.domain.user.entity.User;

import java.math.BigDecimal;
import java.time.LocalDate;

/**
 * 잔여 연차 현황 응답 (GET /api/leaves/me/summary — docs/01 2-5).
 * - remainingDays = base + bonus - use (선차감 반영, 대기 중 연차도 use에 포함)
 * - pendingDays = 현재 회차 창 안의 PENDING 신청 일수 합 (다음 회차 예약분은 이미 use에 들어가지 않음)
 * - advanceDays = 당겨쓴 연차 (다음 기산일 차감 예정)
 * - nextResetDate = 마지막 기산일 + 1년 (온보딩 전이면 null)
 */
public record LeaveSummaryResponse(
        BigDecimal baseDays,
        BigDecimal bonusDays,
        BigDecimal useDays,
        BigDecimal remainingDays,
        BigDecimal pendingDays,
        BigDecimal advanceDays,
        LocalDate nextResetDate,
        BigDecimal nextCycleReservedDays,
        BigDecimal nextCycleAllowanceDays,
        boolean nextCycleReservationEnabled
) {

    public static LeaveSummaryResponse of(User user, BigDecimal pendingDays,
                                          BigDecimal nextCycleReservedDays,
                                          BigDecimal nextCycleAllowanceDays,
                                          boolean nextCycleReservationEnabled) {
        BigDecimal bonus = user.getBonusDays() != null ? user.getBonusDays() : BigDecimal.ZERO;
        LocalDate nextResetDate = user.getLastResetDate() != null
                ? user.getLastResetDate().plusYears(1)
                : null;
        return new LeaveSummaryResponse(
                user.getBaseDays(),
                bonus,
                user.getUseDays(),
                user.getRemainingDays(),
                pendingDays,
                user.getAdvanceDays(),
                nextResetDate,
                nextCycleReservedDays,
                nextCycleAllowanceDays,
                nextCycleReservationEnabled
        );
    }

    /** 기존 호출부 호환용 — 다음 회차 집계를 하지 않는 호출은 예약 블록을 0으로 표시한다. */
    public static LeaveSummaryResponse of(User user, BigDecimal pendingDays) {
        return of(user, pendingDays, BigDecimal.ZERO, BigDecimal.ZERO, false);
    }
}
