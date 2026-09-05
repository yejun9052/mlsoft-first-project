package com.mlsoft.backend.domain.leave.scheduler;

import com.mlsoft.backend.domain.holiday.service.HolidayService;
import com.mlsoft.backend.domain.email.service.LeaveReminderService;
import com.mlsoft.backend.domain.leave.service.AnnualLeaveResetService;
import com.mlsoft.backend.domain.leave.service.BirthdayLeaveGrantService;
import com.mlsoft.backend.domain.leave.service.MonthlyLeaveGrantService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InOrder;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.List;

import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.willThrow;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.verify;

/**
 * 스케줄러 진입점 (docs/09 §1·§7).
 *
 * <p>여기서 검증할 것은 <b>순서와 오류 격리</b> 둘뿐이다 — 잡의 내용은 각 서비스 테스트가 본다.
 * 시계는 {@code Clock.fixed}로 고정한다 (docs/09 §8).
 */
@ExtendWith(MockitoExtension.class)
class LeaveSchedulerTest {

    private static final ZoneId KST = ZoneId.of("Asia/Seoul");
    /** 2026-02-28T15:10Z = 2026-03-01 00:10 KST — 실제 크론이 도는 시각 */
    private static final Instant FIXED_INSTANT = Instant.parse("2026-02-28T15:10:00Z");
    private static final LocalDate TODAY = LocalDate.of(2026, 3, 1);

    @Mock
    private AnnualLeaveResetService annualLeaveResetService;
    @Mock
    private MonthlyLeaveGrantService monthlyLeaveGrantService;
    @Mock
    private BirthdayLeaveGrantService birthdayLeaveGrantService;
    @Mock
    private LeaveReminderService leaveReminderService;
    @Mock
    private HolidayService holidayService;

    private LeaveScheduler leaveScheduler;

    @BeforeEach
    void setUp() {
        leaveScheduler = new LeaveScheduler(Clock.fixed(FIXED_INSTANT, KST),
                annualLeaveResetService, monthlyLeaveGrantService, birthdayLeaveGrantService,
                holidayService, leaveReminderService);
    }

    @Test
    @DisplayName("생일 == 기산일인 사원 — 리셋이 먼저 돌아야 그 해 반차가 살아남는다")
    void runDailyJobs_생일과기산일동일_리셋후생일반차() {
        // 리셋은 bonus_days를 갈아 끼운다. 생일 반차가 먼저 지급되면 그날 바로 증발한다 (docs/09 §1)
        given(annualLeaveResetService.findTargetIds(TODAY)).willReturn(List.of(1L));
        given(annualLeaveResetService.reset(1L, TODAY)).willReturn(1);
        given(monthlyLeaveGrantService.findTargetIds(TODAY)).willReturn(List.of());
        given(birthdayLeaveGrantService.findTargetIds(TODAY)).willReturn(List.of(1L));
        given(birthdayLeaveGrantService.grant(1L, TODAY)).willReturn(true);
        given(leaveReminderService.findTargetIds(TODAY)).willReturn(List.of());

        leaveScheduler.runDailyJobs();

        InOrder inOrder = inOrder(annualLeaveResetService, monthlyLeaveGrantService,
                birthdayLeaveGrantService, leaveReminderService);
        inOrder.verify(annualLeaveResetService).reset(1L, TODAY);
        inOrder.verify(monthlyLeaveGrantService).findTargetIds(TODAY);
        inOrder.verify(birthdayLeaveGrantService).grant(1L, TODAY);
        inOrder.verify(leaveReminderService).findTargetIds(TODAY);
    }

    @Test
    @DisplayName("한 사원이 실패해도 나머지는 계속 처리한다")
    void runDailyJobs_사원1명실패_나머지계속() {
        // @Version 낙관적 락 충돌로 1명이 터졌다고 전원이 롤백되면 안 된다 (docs/09 §7).
        // 실패한 사원은 다음 실행에서도 같은 조건에 걸리므로 재시도 큐 없이 복구된다
        given(annualLeaveResetService.findTargetIds(TODAY)).willReturn(List.of(1L, 2L, 3L));
        willThrow(new RuntimeException("낙관적 락 충돌"))
                .given(annualLeaveResetService).reset(2L, TODAY);
        given(annualLeaveResetService.reset(1L, TODAY)).willReturn(1);
        given(annualLeaveResetService.reset(3L, TODAY)).willReturn(1);
        given(monthlyLeaveGrantService.findTargetIds(TODAY)).willReturn(List.of());
        given(birthdayLeaveGrantService.findTargetIds(TODAY)).willReturn(List.of());

        leaveScheduler.runDailyJobs();

        verify(annualLeaveResetService).reset(3L, TODAY); // 2번에서 멈추지 않았다
    }

    @Test
    @DisplayName("공휴일 동기화가 실패해도 예외를 밖으로 던지지 않는다")
    void syncHolidaysForNewYear_외부API실패_예외전파없음() {
        // 외부 API 장애가 스케줄러 스레드를 죽이면 그다음 크론까지 영향을 받는다.
        // 조회 시점에 다시 시도되므로 여기서는 로그만 남기고 넘어간다
        willThrow(new RuntimeException("data.go.kr 응답 없음")).given(holidayService).syncYear(2026);

        leaveScheduler.syncHolidaysForNewYear();

        verify(holidayService).syncYear(2026);
    }
}
