package com.mlsoft.backend.domain.leave.entity;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

import java.math.BigDecimal;

/**
 * 연차 종류 (docs/02 3-3).
 * 날짜 1개당 차감 일수를 함께 정의한다 — 수치는 BigDecimal (검증 R-1).
 *
 * <p><b>라벨을 여기에 둔다.</b> 이 enum만 라벨이 없어서 사람이 읽는 자리에 {@code ANNUAL}이
 * 그대로 새어 나갔다 — 2026-08-16에 이메일 본문이 "구분: ANNUAL"로 발송됐다.
 * 프론트({@code constants/status.js})가 같은 문자열을 들고 있지만 그건 화면 전용이고,
 * 서버가 만드는 이메일·로그에는 닿지 않는다. {@code ScheduleType}·{@code AdminAction}·{@code Role}이
 * 모두 라벨을 서버에 두는 것과 같은 이유다.
 */
@Getter
@RequiredArgsConstructor
public enum LeaveType {
    ANNUAL(new BigDecimal("1.0"), "연차"),
    HALF_AM(new BigDecimal("0.5"), "오전 반차"),
    HALF_PM(new BigDecimal("0.5"), "오후 반차");

    /** 날짜 1개당 차감 일수 */
    private final BigDecimal daysPerDate;

    /** 사람이 읽는 이름 — 이메일 본문·안내 문구용. 프론트 라벨과 같은 문자열로 맞춘다 */
    private final String label;
}
