package com.mlsoft.backend.domain.email.entity;

/**
 * 연차 소진 안내의 자동 발송 주기.
 *
 * <p>값을 문자열로 저장하는 정책 설정과 달리, 업무 이력의 주기는 허용 값이
 * 고정된 도메인 enum이므로 DB에서도 MySQL ENUM으로 제한한다.</p>
 */
public enum ReminderCycle {
    D30,
    D60,
    D90,
    QUARTER;

    /** D 주기의 대상 구간 일수. 분기는 별도 달력 규칙을 사용한다. */
    public int rangeDays() {
        return switch (this) {
            case D30 -> 30;
            case D60 -> 60;
            case D90 -> 90;
            case QUARTER -> 0;
        };
    }

    /** 정책 설정의 문자열을 자동 발송 주기로 변환한다. NONE·오타는 대상 없음으로 처리한다. */
    public static java.util.Optional<ReminderCycle> fromConfig(String value) {
        if (value == null || value.isBlank() || "NONE".equals(value)) {
            return java.util.Optional.empty();
        }
        try {
            return java.util.Optional.of(valueOf(value));
        } catch (IllegalArgumentException ignored) {
            return java.util.Optional.empty();
        }
    }
}
