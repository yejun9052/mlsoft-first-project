package com.mlsoft.backend.global.exception;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

/**
 * 에러 코드 — HTTP 상태 + 메시지를 함께 관리 (docs/03 에러 코드 표와 1:1 대응).
 * 에러 메시지 하드코딩 금지: 실패 응답은 반드시 이 enum을 통해 생성한다.
 */
@Getter
@RequiredArgsConstructor
public enum ErrorCode {

    // 400 Bad Request
    INSUFFICIENT_LEAVE_BALANCE(400, "잔여 연차가 부족합니다."),
    ALREADY_PROCESSED(400, "이미 처리된 신청입니다."),
    ALREADY_ONBOARDED(400, "이미 온보딩이 완료된 계정입니다."),
    INVALID_INPUT_VALUE(400, "입력값이 올바르지 않습니다."),
    INVALID_APPROVER(400, "승인자로 지정할 수 없는 사용자입니다."),

    // 401 Unauthorized
    UNAUTHENTICATED(401, "로그인이 필요합니다."),
    UNAUTHORIZED_DOMAIN(401, "허용되지 않은 도메인입니다."),
    RETIRED_USER(401, "퇴직 처리된 계정입니다."),
    OAUTH_LOGIN_FAILED(401, "로그인에 실패했습니다. 다시 시도해주세요."),

    // 403 Forbidden
    ACCESS_DENIED(403, "접근 권한이 없습니다."),
    ONBOARDING_NOT_COMPLETED(403, "온보딩(생일·입사일 입력)을 먼저 완료해야 합니다."),

    // 404 Not Found
    USER_NOT_FOUND(404, "사용자를 찾을 수 없습니다."),
    DEPARTMENT_NOT_FOUND(404, "부서를 찾을 수 없습니다."),
    LEAVE_REQUEST_NOT_FOUND(404, "연차 신청을 찾을 수 없습니다."),
    WELFARE_POLICY_NOT_FOUND(404, "복리후생 정책을 찾을 수 없습니다."),
    WELFARE_REQUEST_NOT_FOUND(404, "복리후생 신청을 찾을 수 없습니다."),

    // 409 Conflict
    OVERLAPPING_LEAVE_REQUEST(409, "이미 신청된 기간과 중복됩니다."),
    CONCURRENT_UPDATE(409, "다른 요청과 동시에 처리되어 실패했습니다. 다시 시도해주세요."),

    // 500 Internal Server Error
    INTERNAL_SERVER_ERROR(500, "서버 내부 오류가 발생했습니다.");

    private final int status;
    private final String message;
}
