package com.mlsoft.backend.domain.email.dto;

/**
 * 공휴일 API 검증 결과 — 조회에 성공한 건수만 담는다.
 *
 * <p>실패는 응답 필드가 아니라 {@code HOLIDAY_API_*} 오류로 나간다. 성공 응답에만 도달하므로
 * "유효 여부" 필드를 두면 항상 true인 값이 화면에 분기를 만든다.
 */
public record HolidayVerifyResponse(
        int count
) {
}
