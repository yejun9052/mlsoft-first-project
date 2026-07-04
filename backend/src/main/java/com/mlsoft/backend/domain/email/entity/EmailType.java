package com.mlsoft.backend.domain.email.entity;

/**
 * 이메일 유형 (docs/02 3-12).
 */
public enum EmailType {
    LEAVE,    // 연차 알림 (신청/승인/생일 반차)
    WELFARE,  // 복리후생 알림
    REMINDER, // 연차 소진 안내
    NOTICE    // 공지
}
