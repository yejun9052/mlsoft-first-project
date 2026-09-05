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
    QUARTER
}
