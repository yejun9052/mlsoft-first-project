package com.mlsoft.backend.domain.email.entity;

/**
 * 이메일 발송 상태 (docs/02 3-12, 검증 R-4).
 * FAILED는 스케줄러 재시도(최대 3회) + 관리자 수동 재발송 대상.
 */
public enum EmailStatus {
    PENDING, // 발송 대기
    SENT,    // 발송 완료
    FAILED   // 실패 — 재시도 대상
}
