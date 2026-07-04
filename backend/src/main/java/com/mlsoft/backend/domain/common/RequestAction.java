package com.mlsoft.backend.domain.common;

/**
 * 처리 이력 액션 7종 — 연차·복리후생 공통 (docs/02 3-5).
 * CANCEL_APPROVED / CANCEL_REJECTED는 SYSTEM_ADMIN·TEAM_LEADER만 트리거 가능.
 */
public enum RequestAction {
    PENDING,          // 신청 접수
    APPROVED,         // 승인
    REJECTED,         // 반려
    CANCELLED,        // 취소 (즉시)
    CANCEL_PENDING,   // 소급 취소 요청
    CANCEL_APPROVED,  // 소급 취소 승인
    CANCEL_REJECTED   // 소급 취소 거부
}
