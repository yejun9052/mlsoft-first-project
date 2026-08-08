package com.mlsoft.backend.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableScheduling;

import java.time.Clock;
import java.time.ZoneId;

/**
 * 스케줄링 활성화 + 기준 시계 (docs/09 §8).
 *
 * <p><b>Clock을 빈으로 두는 이유</b>: 스케줄러는 "1년 뒤"를 시뮬레이션해야 검증할 수 있는데
 * {@code LocalDate.now()}가 코드에 박혀 있으면 그게 불가능하다. 이전 프로젝트에서 스케줄러 검증을
 * 끝내 못 했던 원인이 이것이라 docs/02 3-11(b)가 명시적으로 지적하고 있다.
 *
 * <p>주입 범위는 <b>진입점({@code LeaveScheduler})뿐</b>이다. 잡 서비스들은 {@code Clock}이 아니라
 * 계산된 {@code today}를 인자로 받는다 — 그래야 단위 테스트가 시계를 흉내 내지 않고 날짜만 넘기면 된다.
 * 기존 서비스({@code AuthService}·{@code LeaveService})는 건드리지 않는다.
 *
 * <p><b>{@code @EnableScheduling}은 여기 있고 크론은 {@code @Profile("!test")}로 막는다.</b>
 * 이 설정 자체를 프로필로 막으면 테스트 컨텍스트에서 {@code Clock} 빈이 사라져 기동이 깨진다.
 */
@Configuration
@EnableScheduling
public class SchedulingConfig {

    /** 연차 기준일은 한국 시간 고정 — 서버 TZ가 UTC여도 KST 자정~09시 사이 하루가 밀리지 않는다 */
    @Bean
    public Clock clock() {
        return Clock.system(ZoneId.of("Asia/Seoul"));
    }
}
