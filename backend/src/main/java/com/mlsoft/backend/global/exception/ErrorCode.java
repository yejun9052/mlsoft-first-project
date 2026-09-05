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
    ONBOARDING_NOT_PENDING(400, "승인 대기 중인 온보딩이 아닙니다."),
    ONBOARDING_REVISION_EXHAUSTED(400, "승인 대기 중 입사일 수정은 한 번만 가능합니다."),
    PAGE_SIZE_EXCEEDED(400, "한 번에 조회할 수 있는 건수를 초과했습니다."),
    DATE_RANGE_TOO_WIDE(400, "조회 기간이 너무 깁니다."),
    INVALID_INPUT_VALUE(400, "입력값이 올바르지 않습니다."),
    INVALID_APPROVER(400, "승인자로 지정할 수 없는 사용자입니다."),
    DUPLICATE_APPROVER(400, "기본 승인자와 다른 사람을 서브 승인자로 지정해주세요."),
    FUTURE_HIRE_DATE(400, "입사일은 미래 날짜일 수 없습니다."),
    WEEKEND_NOT_ALLOWED(400, "주말은 연차로 신청할 수 없습니다."),
    HOLIDAY_NOT_ALLOWED(400, "공휴일은 연차로 신청할 수 없습니다."),
    SELF_PARENT_DEPARTMENT(400, "부서를 자기 자신의 상위 부서로 지정할 수 없습니다."),
    PAST_DATE_NOT_ALLOWED(400, "지난 날짜는 신청할 수 없습니다."),
    ALREADY_RETIRED(400, "이미 퇴직 처리된 사용자입니다."),
    NOT_RETIRED(400, "퇴직 처리된 계정이 아닙니다."),
    DEPARTMENT_REQUIRED_FOR_LEADER(400, "팀장으로 지정하려면 소속 부서를 먼저 배정해야 합니다."),
    SYSTEM_DEFAULT_DEPARTMENT_LOCKED(400, "기본 미배정 부서는 이름 변경·이동·비활성화할 수 없습니다."),
    LAST_SYSTEM_ADMIN(400, "마지막 시스템 관리자입니다. 다른 관리자를 먼저 지정해주세요."),
    // 퇴직은 그 즉시 로그인까지 막혀 스스로 되돌릴 수 없다 — 역할 자가 강등보다 나쁘다 (S-7)
    CANNOT_RETIRE_SELF(400, "본인 계정은 퇴직 처리할 수 없습니다. 다른 관리자에게 요청해주세요."),
    ADVANCE_LIMIT_EXCEEDED(400, "당겨쓸 수 있는 연차 상한을 초과했습니다. 관리자에게 문의해주세요."),
    TOO_MANY_LEAVE_DATES(400, "한 번에 신청할 수 있는 날짜 수를 초과했습니다."),
    INVALID_CONFIG_VALUE(400, "설정 값 형식이 올바르지 않습니다."),
    CONFIG_VALUE_OUT_OF_RANGE(400, "설정 값이 허용 범위를 벗어났습니다."),
    REMINDER_LIST_DAYS_TOO_SHORT(400, "자동 발송 주기보다 소진 안내 기준일이 짧습니다."),
    EMAIL_BULK_LIMIT_EXCEEDED(400, "이메일 일괄 발송 한도를 초과했습니다."),
    EMAIL_RESEND_NOT_ALLOWED(400, "실패한 이메일만 재발송할 수 있습니다."),
    EMAIL_TEMPLATE_INVALID(400, "이메일 양식 제목과 본문을 확인해주세요."),
    EMAIL_TEMPLATE_BODY_TOO_LONG(400, "이메일 양식 본문은 20,000자 이내로 입력해주세요."),
    EMAIL_TEMPLATE_NOT_FOUND(404, "이메일 양식을 찾을 수 없습니다."),
    EMAIL_HISTORY_NOT_FOUND(404, "이메일 이력을 찾을 수 없습니다."),
    EMAIL_PROVIDER_NOT_FOUND(400, "지원하지 않는 연동 제공자입니다."),
    CREDENTIAL_ENCRYPTION_NOT_CONFIGURED(400, "자격 증명 암호화 키가 설정되지 않았습니다."),

    // 401 Unauthorized
    UNAUTHENTICATED(401, "로그인이 필요합니다."),
    UNAUTHORIZED_DOMAIN(401, "허용되지 않은 도메인입니다."),
    RETIRED_USER(401, "퇴직 처리된 계정입니다."),
    OAUTH_LOGIN_FAILED(401, "로그인에 실패했습니다. 다시 시도해주세요."),

    // 403 Forbidden
    ACCESS_DENIED(403, "접근 권한이 없습니다."),
    // 총관리자라도 예외가 아니다 — 스스로 승인하면 결재라는 절차가 없는 것과 같다
    CANNOT_APPROVE_OWN_REQUEST(403, "본인이 신청한 건은 본인이 결재할 수 없습니다."),
    ONBOARDING_NOT_COMPLETED(403, "온보딩(생일·입사일 입력)을 먼저 완료해야 합니다."),
    // 승인 대기와 미시작을 구분한다 — 같은 메시지를 주면 사원이 온보딩을 다시 내려다 ALREADY_ONBOARDED를 맞는다 (S-1)
    ONBOARDING_PENDING_APPROVAL(403, "입력하신 입사일은 관리자 확인이 필요합니다. 승인 후 이용할 수 있습니다."),
    ONBOARDING_REVISION_DISABLED(403, "관리자가 승인 대기 중 입사일 수정 기능을 비활성화했습니다."),

    // 404 Not Found
    USER_NOT_FOUND(404, "사용자를 찾을 수 없습니다."),
    DEPARTMENT_NOT_FOUND(404, "부서를 찾을 수 없습니다."),
    LEAVE_REQUEST_NOT_FOUND(404, "연차 신청을 찾을 수 없습니다."),
    WELFARE_POLICY_NOT_FOUND(404, "복리후생 정책을 찾을 수 없습니다."),
    WELFARE_REQUEST_NOT_FOUND(404, "복리후생 신청을 찾을 수 없습니다."),
    LEAVE_POLICY_NOT_FOUND(404, "연차 정책을 찾을 수 없습니다."),
    LEAVE_POLICY_CONFIG_NOT_FOUND(404, "연차 시스템 설정을 찾을 수 없습니다."),
    SCHEDULE_NOT_FOUND(404, "일정을 찾을 수 없습니다."),
    RESOURCE_NOT_FOUND(404, "요청한 리소스를 찾을 수 없습니다."),

    // 405 Method Not Allowed
    METHOD_NOT_ALLOWED(405, "지원하지 않는 HTTP 메서드입니다."),

    // 409 Conflict
    OVERLAPPING_LEAVE_REQUEST(409, "이미 신청된 기간과 중복됩니다."),
    CONCURRENT_UPDATE(409, "다른 요청과 동시에 처리되어 실패했습니다. 다시 시도해주세요."),
    DUPLICATE_WELFARE_POLICY(409, "이미 동일한 구분/대상 조합의 정책이 존재합니다."),
    DUPLICATE_SCHEDULE(409, "선택한 날짜에 같은 종류의 일정이 이미 등록되어 있습니다."),

    // 500 Internal Server Error
    INTERNAL_SERVER_ERROR(500, "서버 내부 오류가 발생했습니다.");

    private final int status;
    private final String message;
}
