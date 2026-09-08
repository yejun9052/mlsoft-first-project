package com.mlsoft.backend.domain.audit.entity;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

/**
 * 감사 대상 관리자 조작 (리뷰 S-3).
 *
 * <p>여기 있는 것만 {@code admin_audit_log}에 남는다. 기준은 <b>사원의 권한이나 연차 잔액을
 * 관리자가 직접 바꾸는 조작</b>이다 — 사원 본인의 신청·취소는 이미 {@code leave_action_history}·
 * {@code welfare_action_history}가 담고 있으므로 여기서 중복해 남기지 않는다.
 *
 * <p>조작을 추가할 땐 상수 한 줄이면 된다. 라벨을 서버가 들고 있으므로
 * {@code GET /api/admin/audit-logs/actions}가 필터 목록까지 내려주고 프론트가 따라온다
 * ({@code ScheduleType}·{@code PolicyConfigKey}와 같은 방식).
 */
@Getter
@RequiredArgsConstructor
public enum AdminAction {

    ROLE_CHANGED("권한 변경"),
    DEPARTMENT_CHANGED("부서 변경"),
    BASE_DAYS_CHANGED("연차 직접 설정"),
    USER_RETIRED("퇴직 처리"),
    USER_RESTORED("퇴직 복구"),
    USER_REHIRED("재입사 처리"),
    USER_PURGED("퇴직자 데이터 파기"),
    ONBOARDING_APPROVED("온보딩 승인"),
    ONBOARDING_REJECTED("온보딩 반려"),
    CONFIG_CHANGED("시스템 설정 변경"),
    EMAIL_BULK_SENT("이메일 일괄 발송"),
    EMAIL_TEMPLATE_CHANGED("이메일 양식 수정"),
    EMAIL_RESENT("이메일 재발송");

    private final String label;
}
