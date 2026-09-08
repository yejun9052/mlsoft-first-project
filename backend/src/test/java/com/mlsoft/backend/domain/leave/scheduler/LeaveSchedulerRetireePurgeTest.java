package com.mlsoft.backend.domain.leave.scheduler;

import com.mlsoft.backend.domain.email.service.LeaveReminderService;
import com.mlsoft.backend.domain.holiday.service.HolidayService;
import com.mlsoft.backend.domain.leave.service.AnnualLeaveResetService;
import com.mlsoft.backend.domain.leave.service.BirthdayLeaveGrantService;
import com.mlsoft.backend.domain.leave.service.MonthlyLeaveGrantService;
import com.mlsoft.backend.domain.user.service.RetireePurgeService;
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
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

/** 자동 퇴직자 파기 잡의 모드 가드·실행 순서·사원별 실패 격리를 검증한다. */
@ExtendWith(MockitoExtension.class)
class LeaveSchedulerRetireePurgeTest {

    private static final ZoneId KST = ZoneId.of("Asia/Seoul");
    private static final Instant FIXED_INSTANT = Instant.parse("2026-02-28T15:10:00Z");
    private static final LocalDate TODAY = LocalDate.of(2026, 3, 1);

    @Mock
    private AnnualLeaveResetService annualLeaveResetService;
    @Mock
    private MonthlyLeaveGrantService monthlyLeaveGrantService;
    @Mock
    private BirthdayLeaveGrantService birthdayLeaveGrantService;
    @Mock
    private HolidayService holidayService;
    @Mock
    private LeaveReminderService leaveReminderService;
    @Mock
    private RetireePurgeService retireePurgeService;

    private LeaveScheduler leaveScheduler;

    @BeforeEach
    void setUp() {
        leaveScheduler = new LeaveScheduler(
                Clock.fixed(FIXED_INSTANT, KST),
                annualLeaveResetService,
                monthlyLeaveGrantService,
                birthdayLeaveGrantService,
                holidayService,
                leaveReminderService,
                retireePurgeService);
        given(annualLeaveResetService.findTargetIds(TODAY)).willReturn(List.of());
        given(monthlyLeaveGrantService.findTargetIds(TODAY)).willReturn(List.of());
        given(birthdayLeaveGrantService.findTargetIds(TODAY)).willReturn(List.of());
        given(leaveReminderService.findTargetIds(TODAY)).willReturn(List.of());
    }

    @Test
    @DisplayName("MANUAL 모드면 자동 파기·예고·결과 메일을 모두 건너뛴다")
    void manualMode_skipsEverything() {
        given(retireePurgeService.isAutoMode()).willReturn(false);

        leaveScheduler.runDailyJobs();

        verify(retireePurgeService, never()).findNoticeTargetIds(TODAY);
        verify(retireePurgeService, never()).sendNotice(org.mockito.ArgumentMatchers.anyLong(), org.mockito.ArgumentMatchers.eq(TODAY));
        verify(retireePurgeService, never()).findTargetIds(TODAY);
        verify(retireePurgeService, never()).purge(org.mockito.ArgumentMatchers.anyLong(), org.mockito.ArgumentMatchers.eq(TODAY));
        verify(retireePurgeService, never()).publishResult(org.mockito.ArgumentMatchers.anyInt(), org.mockito.ArgumentMatchers.eq(TODAY));
    }

    @Test
    @DisplayName("AUTO 모드면 네 잡 뒤에 예고·파기를 실행하고 결과 메일은 마지막에 보낸다")
    void autoMode_runsAsFifthJob() {
        given(retireePurgeService.isAutoMode()).willReturn(true);
        given(retireePurgeService.findNoticeTargetIds(TODAY)).willReturn(List.of(10L));
        given(retireePurgeService.sendNotice(10L, TODAY)).willReturn(1);
        given(retireePurgeService.findTargetIds(TODAY)).willReturn(List.of(20L));
        given(retireePurgeService.purge(20L, TODAY)).willReturn(1);

        leaveScheduler.runDailyJobs();

        InOrder order = inOrder(annualLeaveResetService, monthlyLeaveGrantService,
                birthdayLeaveGrantService, leaveReminderService, retireePurgeService);
        order.verify(leaveReminderService).findTargetIds(TODAY);
        order.verify(retireePurgeService).findNoticeTargetIds(TODAY);
        order.verify(retireePurgeService).sendNotice(10L, TODAY);
        order.verify(retireePurgeService).publishNotice(List.of(10L), TODAY);
        order.verify(retireePurgeService).findTargetIds(TODAY);
        order.verify(retireePurgeService).purge(20L, TODAY);
        order.verify(retireePurgeService).publishResult(1, TODAY);
    }

    @Test
    @DisplayName("자동 파기 한 명 실패는 나머지와 결과 메일을 막지 않는다")
    void onePurgeFailure_doesNotStopOthers() {
        given(retireePurgeService.isAutoMode()).willReturn(true);
        given(retireePurgeService.findNoticeTargetIds(TODAY)).willReturn(List.of());
        given(retireePurgeService.findTargetIds(TODAY)).willReturn(List.of(1L, 2L, 3L));
        given(retireePurgeService.purge(1L, TODAY)).willReturn(1);
        willThrow(new RuntimeException("낙관적 락 충돌"))
                .given(retireePurgeService).purge(2L, TODAY);
        given(retireePurgeService.purge(3L, TODAY)).willReturn(1);

        leaveScheduler.runDailyJobs();

        verify(retireePurgeService).purge(3L, TODAY);
        verify(retireePurgeService).publishResult(2, TODAY);
    }
}
