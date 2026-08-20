package com.mlsoft.backend.domain.leave.dto;

import java.math.BigDecimal;
import java.time.LocalDate;

/**
 * 개인 연차 사용 히트맵의 날짜별 확정 사용량.
 *
 * <p>신청 건수가 아니라 {@code LeaveType.daysPerDate}를 합산한다. 반차를 0.5일로
 * 보존해야 하므로 사용량은 화면 전용 응답에서도 BigDecimal을 유지한다.
 */
public record MyLeaveHeatmapResponse(
        LocalDate date,
        BigDecimal days
) {
}
