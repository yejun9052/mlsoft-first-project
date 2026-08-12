package com.mlsoft.backend.domain.email.event;

/**
 * 커밋 후 발송할 수신자 스냅샷.
 *
 * <p>JPA 엔티티를 이벤트에 넣지 않는다. 실제 재직·이메일 상태는 발송 직전에 다시 조회한다.
 *
 * @param userId        수신자 사용자 id
 * @param reasonVisible 신청·취소 사유를 본문에 표시할 수 있는지
 */
public record EmailRecipient(
        Long userId,
        boolean reasonVisible
) {
}
