package com.mlsoft.backend.domain.common;

/**
 * 신청 상태 — 연차·복리후생 공통 (docs/02 3-3, 승인 구조 동일).
 * 취소 승인/거부의 세부 결과는 status가 아니라 action_history의 RequestAction으로 기록한다.
 */
public enum RequestStatus {
    PENDING,        // 대기 (선차감 적용 상태)
    APPROVED,       // 승인
    REJECTED,       // 반려 (선차감 복구)
    CANCELLED,      // 취소 (선차감 복구)
    CANCEL_PENDING  // 소급 취소 승인 대기 (APPROVED 유지 효과)
}
