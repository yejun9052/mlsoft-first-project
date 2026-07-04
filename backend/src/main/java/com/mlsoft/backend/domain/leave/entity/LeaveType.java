package com.mlsoft.backend.domain.leave.entity;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

import java.math.BigDecimal;

/**
 * 연차 종류 (docs/02 3-3).
 * 날짜 1개당 차감 일수를 함께 정의한다 — 수치는 BigDecimal (검증 R-1).
 */
@Getter
@RequiredArgsConstructor
public enum LeaveType {
    ANNUAL(new BigDecimal("1.0")),   // 연차
    HALF_AM(new BigDecimal("0.5")),  // 오전 반차
    HALF_PM(new BigDecimal("0.5"));  // 오후 반차

    /** 날짜 1개당 차감 일수 */
    private final BigDecimal daysPerDate;
}
