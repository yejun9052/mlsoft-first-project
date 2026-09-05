package com.mlsoft.backend.domain.email.event;

/**
 * 건별 알림 문구 유형.
 *
 * <p>문구 생성 분기를 서비스 상태 문자열과 분리해 한곳에서 관리한다.
 */
public enum EmailTemplateKind {
    LEAVE_APPLIED,
    LEAVE_APPROVED,
    LEAVE_REJECTED,
    LEAVE_CANCELLED,
    LEAVE_CANCEL_PENDING,
    LEAVE_CANCEL_APPROVED,
    LEAVE_CANCEL_REJECTED,
    WELFARE_APPLIED,
    WELFARE_APPROVED,
    WELFARE_REJECTED,
    BIRTHDAY_LEAVE_GRANTED,
    ONBOARDING_PENDING,
    ONBOARDING_APPROVED,
    ONBOARDING_REJECTED,
    ONBOARDING_REVISED
}
