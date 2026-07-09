package com.mlsoft.backend.domain.leave.dto;

import com.mlsoft.backend.domain.user.entity.User;

import java.math.BigDecimal;
import java.time.LocalDate;

/**
 * 잔여 연차 현황 응답 (GET /api/leaves/me/summary — docs/01 2-5).
 * - remainingDays = base + bonus - use (선차감 반영, 대기 중 연차도 use에 포함)
 * - pendingDays = PENDING 상태 신청 일수 합 (아직 승인 안 난 선차감분)
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
        LocalDate nextResetDate
) {

    public static LeaveSummaryResponse of(User user, BigDecimal pendingDays) {
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
                nextResetDate
        );
    }
}
