package com.mlsoft.backend.domain.email.scheduler;

import com.mlsoft.backend.domain.email.service.EmailDeliveryService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Profile;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * FAILED 이메일 재시도 스케줄러.
 *
 * <p>SchedulingConfig는 Clock 빈도 제공하므로 프로필로 막지 않고, 크론 진입점만 test 프로필에서 제외한다.
 */
@Slf4j
@Component
@Profile("!test")
@RequiredArgsConstructor
public class EmailRetryScheduler {

    private final EmailDeliveryService emailDeliveryService;

    /** 15분마다 최대 100건 — Gmail 장애 중 재접속 폭주와 무한 재시도를 막는다 */
    @Scheduled(cron = "0 */15 * * * *", zone = "Asia/Seoul")
    public void retryFailedEmails() {
        var targetIds = emailDeliveryService.findRetryTargetIds();
        int succeededOrHandled = 0;

        for (Long historyId : targetIds) {
            try {
                emailDeliveryService.retry(historyId);
                succeededOrHandled++;
            } catch (RuntimeException e) {
                // 한 이력의 DB 오류가 나머지 재시도를 막지 않게 격리한다.
                log.error("[이메일 재시도] 처리 실패 — historyId={}", historyId, e);
            }
        }

        if (!targetIds.isEmpty()) {
            log.info("[이메일 재시도] 대상 {}건 중 {}건 처리",
                    targetIds.size(), succeededOrHandled);
        }
    }
}
