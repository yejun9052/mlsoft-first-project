package com.mlsoft.backend.domain.leave.scheduler;

import com.mlsoft.backend.domain.holiday.service.HolidayService;
import com.mlsoft.backend.domain.leave.service.AnnualLeaveResetService;
import com.mlsoft.backend.domain.leave.service.BirthdayLeaveGrantService;
import com.mlsoft.backend.domain.email.service.LeaveReminderService;
import com.mlsoft.backend.domain.leave.service.MonthlyLeaveGrantService;
import com.mlsoft.backend.domain.user.service.RetireePurgeService;
import org.springframework.beans.factory.annotation.Autowired;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Profile;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.Clock;
import java.time.LocalDate;
import java.util.List;
import java.util.function.ToIntFunction;

/**
 * 연차 스케줄러 진입점 (docs/09 §1·§7).
 *
 * <p>매일 00:10 KST에 다섯 잡을 <b>고정된 순서</b>로 돌린다. 순서는 취향이 아니라 데이터 의존성이다:
 * <pre>
 * ① 기산일 리셋  →  ② 월차 적립  →  ③ 생일 반차  →  ④ 연차 소진 안내  →  ⑤ 퇴직자 파기
 * </pre>
 * <ul>
 *   <li><b>① → ②</b>: 1주년 당일에 둘이 겹친다. 리셋이 먼저면 월차의 "1년 미만" 조건에서 자연히
 *       빠지지만, 반대면 11일째 월차를 적립한 직후 리셋이 그걸 소멸시켜 하루짜리 유령 적립이 남는다</li>
 *   <li><b>① → ③</b>: 리셋은 {@code bonus_days}를 갈아 끼운다. 생일과 기산일이 같은 날인 사원은
 *       순서가 뒤바뀌면 그 해 생일 반차가 지급 즉시 증발한다</li>
 * </ul>
 *
 * <p><b>여기에 도메인 로직은 없다.</b> 잡별 규칙은 각 서비스가 갖고, 이 클래스는 트리거와
 * <b>사원별 오류 격리</b>만 한다. 서비스를 프록시 경유로 호출해야 사원 1명당
 * {@code REQUIRES_NEW} 트랜잭션이 실제로 걸리므로(자기 호출은 프록시를 타지 않는다) 루프가
 * 서비스 안이 아니라 여기 있다 — docs/09 §9의 "트리거 전용"에서 이 부분만 벗어난다.
 *
 * <p>{@code @Profile("!test")} — H2 테스트가 도는 중 스케줄러가 실제로 돌면 테스트끼리 오염된다.
 * 잡 서비스들은 프로필과 무관하게 등록되므로 테스트는 서비스를 직접 호출해 검증한다.
 */
@Slf4j
@Component
@Profile("!test")
public class LeaveScheduler {

    private final Clock clock;
    private final AnnualLeaveResetService annualLeaveResetService;
    private final MonthlyLeaveGrantService monthlyLeaveGrantService;
    private final BirthdayLeaveGrantService birthdayLeaveGrantService;
    private final LeaveReminderService leaveReminderService;
    private final RetireePurgeService retireePurgeService;
    private final HolidayService holidayService;

    /** Spring용 생성자 — 리마인더 뒤에 퇴직자 파기 잡을 배치한다. */
    @Autowired
    public LeaveScheduler(
            Clock clock,
            AnnualLeaveResetService annualLeaveResetService,
            MonthlyLeaveGrantService monthlyLeaveGrantService,
            BirthdayLeaveGrantService birthdayLeaveGrantService,
            HolidayService holidayService,
            LeaveReminderService leaveReminderService,
            RetireePurgeService retireePurgeService
    ) {
        this.clock = clock;
        this.annualLeaveResetService = annualLeaveResetService;
        this.monthlyLeaveGrantService = monthlyLeaveGrantService;
        this.birthdayLeaveGrantService = birthdayLeaveGrantService;
        this.holidayService = holidayService;
        this.leaveReminderService = leaveReminderService;
        this.retireePurgeService = retireePurgeService;
    }

    /** 기존 단위 테스트와의 호환용 생성자 — 다섯 번째 잡은 등록되지 않은 상태로 둔다. */
    public LeaveScheduler(
            Clock clock,
            AnnualLeaveResetService annualLeaveResetService,
            MonthlyLeaveGrantService monthlyLeaveGrantService,
            BirthdayLeaveGrantService birthdayLeaveGrantService,
            HolidayService holidayService,
            LeaveReminderService leaveReminderService
    ) {
        this(clock, annualLeaveResetService, monthlyLeaveGrantService, birthdayLeaveGrantService,
                holidayService, leaveReminderService, null);
    }

    /** 매일 00:10 KST — 날짜가 바뀐 직후, 근무 시작 전에 끝난다 */
    @Scheduled(cron = "0 10 0 * * *", zone = "Asia/Seoul")
    public void runDailyJobs() {
        LocalDate today = LocalDate.now(clock);
        log.info("[스케줄러] 일일 잡 시작 — 기준일={}", today);

        runPerUser("기산일 리셋", annualLeaveResetService.findTargetIds(today),
                userId -> annualLeaveResetService.reset(userId, today));
        runPerUser("월차 적립", monthlyLeaveGrantService.findTargetIds(today),
                userId -> monthlyLeaveGrantService.grant(userId, today));
        runPerUser("생일 반차", birthdayLeaveGrantService.findTargetIds(today),
                userId -> birthdayLeaveGrantService.grant(userId, today) ? 1 : 0);
        runPerUser("연차 소진 안내", leaveReminderService.findTargetIds(today),
                userId -> leaveReminderService.dispatch(userId, today));

        if (retireePurgeService == null || !retireePurgeService.isAutoMode()) {
            log.info("[스케줄러:퇴직자 파기] AUTO 모드가 아니므로 건너뜀 — 기준일={}", today);
        } else {
            List<Long> noticeTargetIds = retireePurgeService.findNoticeTargetIds(today);
            runPerUser("퇴직자 파기 예고", noticeTargetIds,
                    userId -> retireePurgeService.sendNotice(userId, today));
            try {
                retireePurgeService.publishNotice(noticeTargetIds, today);
            } catch (RuntimeException e) {
                log.error("[스케줄러:퇴직자 파기] 예고 메일 발송 실패 — 대상 {}명", noticeTargetIds.size(), e);
            }
            int purgedCount = runPerUser("퇴직자 파기", retireePurgeService.findTargetIds(today),
                    userId -> retireePurgeService.purge(userId, today));
            try {
                retireePurgeService.publishResult(purgedCount, today);
            } catch (RuntimeException e) {
                log.error("[스케줄러:퇴직자 파기] 결과 메일 발송 실패 — 파기 {}건", purgedCount, e);
            }
        }

        log.info("[스케줄러] 일일 잡 종료 — 기준일={}", today);
    }

    /**
     * 매년 1월 1일 00:05 KST — 그 해 공휴일을 미리 적재한다.
     * <p>없어도 조회 시 1회 적재로 채워지지만(HolidayService), 그때까지는 공휴일 검증이 느슨해진다.
     * 일일 잡보다 먼저 돌려 새해 첫 연차 신청이 공휴일 검증을 통과하게 한다.
     */
    @Scheduled(cron = "0 5 0 1 1 *", zone = "Asia/Seoul")
    public void syncHolidaysForNewYear() {
        int year = LocalDate.now(clock).getYear();
        try {
            log.info("[스케줄러] {}년 공휴일 {}건 적재", year, holidayService.syncYear(year).count());
        } catch (RuntimeException e) {
            // 외부 API 장애가 다른 잡을 막아서는 안 된다 — 조회 시점에 다시 시도된다
            log.error("[스케줄러] {}년 공휴일 동기화 실패", year, e);
        }
    }

    /**
     * 사원별로 실행하고 실패는 <b>그 사원만</b> 건너뛴다 (docs/09 §7).
     *
     * <p>실패를 삼키지 않고 {@code userId}와 함께 ERROR로 남긴다. 건너뛴 사원은 다음 실행에서도
     * 같은 대상 조건에 걸리므로 별도 재시도 큐 없이 자동으로 복구된다.
     */
    private int runPerUser(String jobName, List<Long> targetIds, ToIntFunction<Long> action) {
        int processed = 0;
        int failed = 0;
        for (Long userId : targetIds) {
            try {
                if (action.applyAsInt(userId) > 0) {
                    processed++;
                }
            } catch (RuntimeException e) {
                failed++;
                log.error("[스케줄러:{}] 처리 실패 — userId={}", jobName, userId, e);
            }
        }
        log.info("[스케줄러:{}] 대상 {}명 중 {}명 처리, 실패 {}명", jobName, targetIds.size(), processed, failed);
        return processed;
    }
}
