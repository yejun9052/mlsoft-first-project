package com.mlsoft.backend.global.response;

/**
 * 성공 메시지 상수 클래스 — 컨트롤러에서 문자열 리터럴 직접 사용 금지 (docs/04).
 * 기능 구현 단계에서 필요한 상수를 여기에 추가한다.
 */
public final class ResponseMessage {

    // 연차
    public static final String LEAVE_CREATED = "연차 신청이 완료되었습니다.";
    public static final String LEAVE_APPROVED = "승인 처리가 완료되었습니다.";
    public static final String LEAVE_REJECTED = "반려 처리가 완료되었습니다.";
    public static final String LEAVE_CANCELLED = "연차 신청이 취소되었습니다.";
    public static final String LEAVE_CANCEL_REQUESTED = "취소 요청이 접수되었습니다. 승인자 확인 후 처리됩니다.";
    public static final String LEAVE_CANCEL_APPROVED = "취소 요청이 승인되었습니다.";
    public static final String LEAVE_CANCEL_REJECTED = "취소 요청이 거부되었습니다.";
    public static final String LEAVE_FETCHED = "연차 정보를 조회했습니다.";

    // 복리후생 신청
    public static final String WELFARE_CREATED = "복리후생 신청이 완료되었습니다.";
    public static final String WELFARE_APPROVED = "승인 처리가 완료되었습니다.";
    public static final String WELFARE_REJECTED = "반려 처리가 완료되었습니다.";
    public static final String WELFARE_CANCELLED = "복리후생 신청이 취소되었습니다.";
    public static final String WELFARE_FETCHED = "복리후생 신청 정보를 조회했습니다.";

    // 복리후생 정책
    public static final String WELFARE_POLICY_CREATED = "복리후생 정책이 추가되었습니다.";
    public static final String WELFARE_POLICY_UPDATED = "복리후생 정책이 수정되었습니다.";
    public static final String WELFARE_POLICY_DEACTIVATED = "복리후생 정책이 비활성화되었습니다.";
    public static final String WELFARE_POLICY_FETCHED = "복리후생 정책 정보를 조회했습니다.";

    // 사용자
    public static final String USER_INFO_FETCHED = "사용자 정보를 조회했습니다.";
    public static final String ONBOARDING_COMPLETED = "온보딩이 완료되었습니다.";
    public static final String LOGOUT_COMPLETED = "로그아웃 되었습니다.";

    // 사용자 관리
    public static final String USER_PROFILE_UPDATED = "내 정보가 수정되었습니다.";
    public static final String USER_ROLE_UPDATED = "권한이 변경되었습니다.";
    public static final String USER_DEPARTMENT_UPDATED = "부서가 변경되었습니다.";
    public static final String USER_BASE_DAYS_UPDATED = "연차 일수가 설정되었습니다.";
    public static final String USER_RETIRED = "퇴직 처리가 완료되었습니다.";

    // 부서 관리
    public static final String DEPARTMENT_FETCHED = "부서 정보를 조회했습니다.";
    public static final String DEPARTMENT_CREATED = "부서가 생성되었습니다.";
    public static final String DEPARTMENT_UPDATED = "부서 정보가 수정되었습니다.";
    public static final String DEPARTMENT_DEACTIVATED = "부서가 비활성화되었습니다.";

    private ResponseMessage() {
        // 인스턴스화 방지
    }
}
