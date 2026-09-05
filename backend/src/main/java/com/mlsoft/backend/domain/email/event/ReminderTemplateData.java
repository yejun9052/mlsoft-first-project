package com.mlsoft.backend.domain.email.event;

/**
 * 연차 소진 안내 양식에 주입할 값.
 *
 * <p>모든 값을 문자열로 전달해 표시 형식과 HTML escaping을 양식 공통 경계에서
 * 한 번만 처리한다. W4의 양식 미리보기도 이 자료형을 재사용한다.</p>
 */
public record ReminderTemplateData(
        String name,
        String remainingDays,
        String nextResetDate,
        String daysUntilReset,
        String serviceUrl
) {
}
