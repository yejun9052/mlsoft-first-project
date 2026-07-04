package com.mlsoft.backend.global.response;

/**
 * 공통 응답 포맷 (docs/03 공통 규칙).
 * - 성공: { "success": true, "message": "...", "data": { } }
 * - 실패: { "success": false, "message": "...", "data": null } — GlobalExceptionHandler에서 일괄 생성
 */
public record CommonResponse<T>(
        boolean success,
        String message,
        T data
) {

    /** 성공 응답 (데이터 포함) */
    public static <T> CommonResponse<T> success(String message, T data) {
        return new CommonResponse<>(true, message, data);
    }

    /** 성공 응답 (데이터 없음) */
    public static CommonResponse<Void> success(String message) {
        return new CommonResponse<>(true, message, null);
    }

    /** 실패 응답 — 예외 핸들러 전용 */
    public static CommonResponse<Void> fail(String message) {
        return new CommonResponse<>(false, message, null);
    }
}
