package com.mlsoft.backend.domain.email.dto;

import java.math.BigDecimal;
import java.time.LocalDate;

/** 관리자 대상 미리보기와 리마인더 목록에 사용하는 최소 공개 정보. */
public record ReminderTarget(
        Long userId,
        String name,
        String departmentName,
        BigDecimal remainingDays,
        LocalDate nextResetDate,
        long daysUntilReset,
        boolean emailAvailable
) {
}
