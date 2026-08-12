package com.mlsoft.backend.domain.email.service;

import com.mlsoft.backend.config.EmailAsyncConfig;
import com.mlsoft.backend.domain.email.event.EmailDispatchEvent;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

/**
 * 업무 커밋 뒤 이메일을 실제로 내보낸다.
 *
 * <p><b>이 리스너에는 트랜잭션이 없다</b> (1차 테스트 A). 수신자마다
 * {@link EmailDeliveryService#send}가 자기 트랜잭션({@code REQUIRES_NEW})을 연다.
 *
 * <p>이전에는 리스너 하나가 전체를 한 트랜잭션으로 묶어서, 뒤쪽 수신자 처리에서 예외가 나면
 * <b>앞쪽 수신자의 {@code SENT} 이력까지 롤백됐다</b> — 메일은 이미 나갔는데 기록만 사라지고,
 * {@code FAILED} 이력도 없어 재시도 스케줄러가 줍지 못했다. 수동 재처리하면 앞사람은 중복 수신했다.
 *
 * <p>한 건이 실패해도 나머지는 계속 보낸다. 스케줄러의 "사원 1명 = 1트랜잭션"과 같은 이유다.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class EmailNotificationListener {

    private final EmailDeliveryService emailDeliveryService;

    @Async(EmailAsyncConfig.MAIL_TASK_EXECUTOR)
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void handle(EmailDispatchEvent event) {
        for (Long historyId : event.historyIds()) {
            try {
                emailDeliveryService.send(historyId);
            } catch (RuntimeException e) {
                // 한 건의 실패가 나머지 수신자를 막지 않는다. 이력은 PENDING으로 남아
                // 재시도 스케줄러가 다시 집어 간다.
                log.error("[이메일] 발송 처리 실패 — historyId={}", historyId, e);
            }
        }
    }
}
