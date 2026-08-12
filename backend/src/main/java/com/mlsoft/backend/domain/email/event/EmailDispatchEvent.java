package com.mlsoft.backend.domain.email.event;

import java.util.List;

/**
 * 발송할 {@code email_history} 행의 id 목록 (1차 테스트 A·B 대응).
 *
 * <p><b>이력 행은 이 이벤트가 발행되기 전에 업무 트랜잭션 안에서 이미 만들어져 있다.</b>
 * 그래서 이벤트는 id만 나르면 된다 — 수신자·본문을 담고 다니던 이전 구조는
 * 리스너가 실행되지 못하면(큐 포화·강제 종료) 알림이 흔적 없이 사라졌다.
 *
 * <p>지금은 업무가 커밋됐다면 {@code PENDING} 행이 DB에 남아 있으므로,
 * 리스너가 못 돌아도 재시도 스케줄러가 주워 간다.
 */
public record EmailDispatchEvent(List<Long> historyIds) {

    public EmailDispatchEvent {
        historyIds = List.copyOf(historyIds);
    }
}
