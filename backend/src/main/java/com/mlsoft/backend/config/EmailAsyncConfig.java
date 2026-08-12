package com.mlsoft.backend.config;

import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableAsync;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;

import java.util.concurrent.Executor;

/**
 * 이메일 전용 비동기 실행기 (검증 R-4).
 *
 * <p>SMTP 장애 중에도 요청마다 스레드가 늘어나지 않도록 스레드와 큐에 상한을 둔다.
 * 큐가 가득 차면 이메일 작업만 버리고 업무 요청은 계속 성공시킨다. 버린 사실은 WARN으로 남겨
 * 모니터링할 수 있게 한다.
 */
@Slf4j
@Configuration
@EnableAsync
public class EmailAsyncConfig {

    public static final String MAIL_TASK_EXECUTOR = "mailTaskExecutor";

    @Bean(name = MAIL_TASK_EXECUTOR)
    public Executor mailTaskExecutor() {
        ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
        executor.setCorePoolSize(2);
        executor.setMaxPoolSize(4);
        executor.setQueueCapacity(100);
        executor.setThreadNamePrefix("mail-");
        executor.setWaitForTasksToCompleteOnShutdown(false);
        executor.setRejectedExecutionHandler((task, pool) ->
                log.warn("[이메일] 비동기 큐 포화로 발송 작업을 폐기했습니다. active={}, queued={}",
                        pool.getActiveCount(), pool.getQueue().size()));
        executor.initialize();
        return executor;
    }
}
