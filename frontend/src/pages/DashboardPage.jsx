import dayjs from 'dayjs';
import { useMemo, useState } from 'react';
import { Link, useNavigate } from 'react-router-dom';
import { CalendarClock, CalendarPlus, ChevronRight, Gift, Loader2 } from 'lucide-react';
import PageHeader from '../components/ui/PageHeader.jsx';
import Card from '../components/ui/Card.jsx';
import Button from '../components/ui/Button.jsx';
import EmptyState from '../components/ui/EmptyState.jsx';
import Stat from '../components/ui/Stat.jsx';
import StatStrip from '../components/ui/StatStrip.jsx';
import StatusBadge from '../components/ui/StatusBadge.jsx';
import ErrorState from '../components/ui/ErrorState.jsx';
import { useCurrentUser } from '../hooks/useAuth.js';
import { useLeaveSummary, useMyLeaves } from '../hooks/useLeaves.js';
import { useMySchedules } from '../hooks/useSchedules.js';
import { useMyWelfareRequests } from '../hooks/useWelfare.js';
import { useHolidays } from '../hooks/useHolidays.js';
import { LEAVE_TYPE_LABEL, SCHEDULE_TYPE_LABEL } from '../constants/status.js';
import { formatNextCycleReservation, hasNextCycleReservation } from '../utils/leaveSummary.js';

const TODAY_D = dayjs();
const TODAY = TODAY_D.format('YYYY-MM-DD');
// 공휴일이 연속된 주에도 개인 일정이 함께 보일 여지를 두되 우측 카드를 길게 만들지 않는 상한이다.
const UPCOMING_EVENT_LIMIT = 5;

function formatDates(dates) {
  const first = dayjs(dates[0]);
  const head = `${first.format('M/D')} (${'일월화수목금토'[first.day()]})`;
  return dates.length > 1 ? `${head} 외 ${dates.length - 1}일` : head;
}

function UpcomingEventItem({ event }) {
  const [showDetail, setShowDetail] = useState(false);
  const dday = dayjs(event.date).diff(dayjs(TODAY), 'day');

  return (
    <li
      className="relative flex items-center justify-between gap-3 py-3"
      tabIndex={event.detail ? 0 : undefined}
      onMouseEnter={() => setShowDetail(true)}
      onMouseLeave={() => setShowDetail(false)}
      onFocus={() => setShowDetail(true)}
      onBlur={() => setShowDetail(false)}
    >
      <div className="flex min-w-0 items-center gap-2.5">
        <span
          className={`h-2.5 w-2.5 shrink-0 rounded-full ${
            event.kind === 'holiday' ? 'bg-danger/70' : 'bg-violet-400/80'
          }`}
        />
        <span className="truncate text-[14px] text-ink-body">{event.label}</span>
        <span className="shrink-0 text-[12px] text-ink-faint">{event.sub}</span>
      </div>

      <span className="shrink-0 text-[13px] text-ink-mute tabular-nums">
        {dayjs(event.date).format('M/D')}
        <span className="ml-1.5 text-[12px] text-ink-faint">
          {dday === 0 ? '오늘' : `D-${dday}`}
        </span>
      </span>

      {showDetail && event.detail && (
        <div
          role="tooltip"
          className="absolute bottom-full left-5 z-20 mb-1 max-w-[280px] rounded-btn border border-white/[0.15] bg-navy-card px-3 py-2 text-[12px] leading-5 text-ink-body shadow-card"
        >
          {event.detail}
        </div>
      )}
    </li>
  );
}

export default function DashboardPage() {
  const navigate = useNavigate();
  const meQuery = useCurrentUser();
  const { data: me } = meQuery;
  const summaryQuery = useLeaveSummary();
  const myLeavesQuery = useMyLeaves();
  // 상태 필터가 없는 목록은 한 페이지 안에서 세므로 기존 연차 조회 상한과 맞춘다.
  const myWelfareQuery = useMyWelfareRequests({ size: 100 });
  // 현재 API에는 날짜 범위 필터가 없다. 넉넉한 한 페이지를 받고 날짜별로 펼친 뒤 정렬한다.
  const mySchedulesQuery = useMySchedules({ size: 100 });

  const summary = summaryQuery.data;
  const myLeaves = myLeavesQuery.data?.content ?? [];
  const myWelfareRequests = myWelfareQuery.data?.content ?? [];
  // `?? []`는 매 렌더 새 배열을 만든다. 그대로 아래 useMemo의 의존성에 넣으면
  // 참조가 항상 달라 메모가 매번 무효가 된다 — 이 화면은 카드가 많아 그 비용이 그냥 쌓인다.
  const mySchedules = useMemo(
    () => mySchedulesQuery.data?.content ?? [],
    [mySchedulesQuery.data],
  );

  // 연말에 다음 해 첫 일정을 놓치지 않도록 올해와 다음 해 공휴일을 함께 받는다.
  // 실패해도 개인 일정은 계속 표시돼야 하므로 data가 없으면 해당 연도만 빈 배열로 취급한다.
  const holidayYear = TODAY_D.year();
  const holidaysQuery = useHolidays(holidayYear);
  const nextYearHolidaysQuery = useHolidays(holidayYear + 1);
  const holidays = useMemo(
    () => [...(holidaysQuery.data ?? []), ...(nextYearHolidaysQuery.data ?? [])],
    [holidaysQuery.data, nextYearHolidaysQuery.data],
  );

  // 본인 개인 일정과 공휴일만 날짜별로 펼친다. 연차는 휴무 신청 내역과 성격이 달라 섞지 않는다.
  const upcomingEvents = useMemo(() => {
    const scheduleEvents = mySchedules.flatMap((schedule) =>
      schedule.dates
        .filter((date) => date >= TODAY)
        .map((date) => ({
          date,
          kind: 'schedule',
          label:
            schedule.typeLabel ??
            SCHEDULE_TYPE_LABEL[schedule.scheduleType] ??
            schedule.scheduleType,
          sub: '내 일정',
          detail: schedule.memo,
          id: schedule.id,
        })),
    );

    const holidayEvents = holidays
      .filter((holiday) => holiday.date >= TODAY)
      .map((holiday) => ({
        date: holiday.date,
        kind: 'holiday',
        label: holiday.name,
        sub: '공휴일',
        detail: null,
        id: holiday.date,
      }));

    return [...scheduleEvents, ...holidayEvents]
      .sort((left, right) => {
        const dateOrder = left.date.localeCompare(right.date);
        if (dateOrder !== 0) return dateOrder;
        return left.kind.localeCompare(right.kind);
      })
      .slice(0, UPCOMING_EVENT_LIMIT);
  }, [mySchedules, holidays]);

  // 내 신청 중 결재 대기 건수 — 신규 신청과 소급 취소 신청을 모두 센다.
  // 연차와 복리후생을 함께 세어 사이드바 및 실제 신청 목록과 기준을 맞춘다.
  function isAwaitingApproval(request) {
    return request.status === 'PENDING' || request.status === 'CANCEL_PENDING';
  }

  const myPendingCount =
    myLeaves.filter(isAwaitingApproval).length +
    myWelfareRequests.filter(isAwaitingApproval).length;

  // 실패를 로딩과 구분한다. 부가 카드 쿼리 실패는 인터셉터가 알리고 핵심 현황은 계속 보여준다.
  if (summaryQuery.isError || meQuery.isError) {
    return (
      <ErrorState
        label="대시보드를 불러오지 못했습니다."
        onRetry={() => {
          summaryQuery.refetch();
          meQuery.refetch();
        }}
      />
    );
  }

  if (!summary || !me) {
    return (
      <div className="flex h-full items-center justify-center gap-2 text-ink-mute">
        <Loader2 size={18} className="animate-spin" />
        <span className="text-[13px]">불러오는 중…</span>
      </div>
    );
  }

  const totalDays = Number(summary.baseDays) + Number(summary.bonusDays);
  const confirmedUsed = Number(summary.useDays) - Number(summary.pendingDays);
  const usedPct = totalDays > 0 ? (confirmedUsed / totalDays) * 100 : 0;
  const pendingPct = totalDays > 0 ? (Number(summary.pendingDays) / totalDays) * 100 : 0;
  const expiringDays = summary.remainingDays;
  const resetDday = summary.nextResetDate
    ? dayjs(summary.nextResetDate).diff(dayjs(TODAY), 'day')
    : null;

  return (
    <div className="flex h-full flex-col">
      <PageHeader
        title="내 연차 현황"
        subtitle={`${dayjs(TODAY).format('YYYY년 M월 D일')} · ${me.departmentName ?? '미배정'} ${me.name}님`}
      >
        <span className="rounded-badge bg-accent-cyan/10 px-3 py-1.5 text-[12px] font-semibold text-accent-cyan ring-1 ring-inset ring-accent-cyan/25 tabular-nums">
          {dayjs(TODAY).year()} 회계연도
        </span>
      </PageHeader>

      <div className="mb-5 flex items-end justify-between gap-6 border-b border-white/[0.12] pb-6">
        <StatStrip>
          <Stat
            label="잔여 연차"
            value={summary.remainingDays}
            unit="일"
            size="hero"
            caption={
              hasNextCycleReservation(summary.nextCycleReservedDays)
                ? formatNextCycleReservation(summary.nextCycleReservedDays)
                : undefined
            }
          />
          <Stat label="소멸 예정" value={expiringDays} unit="일" tone="text-warn" />
          <Stat label="내 결재 대기" value={myPendingCount} unit="건" />
          <Stat
            label="다음 기산일"
            value={resetDday !== null ? `D-${resetDday}` : '-'}
            tone={resetDday !== null ? 'text-warn' : undefined}
            caption={
              summary.nextResetDate
                ? dayjs(summary.nextResetDate).format('M월 D일')
                : undefined
            }
          />
        </StatStrip>

        <div className="flex items-center gap-2">
          <Button variant="primary" Icon={CalendarPlus} onClick={() => navigate('/calendar')}>
            연차·반차 신청
          </Button>
          <Button variant="secondary" Icon={Gift} onClick={() => navigate('/welfare')}>
            경조사 신청
          </Button>
        </div>
      </div>

      <div className="grid min-h-0 flex-1 grid-cols-1 gap-5 lg:grid-cols-[1.5fr_1fr]">
        <Card
          title="최근 신청 내역"
          fill
          scroll
          right={
            <Link
              to="/history"
              className="flex items-center gap-0.5 text-[13px] font-medium text-ink-mute transition-colors hover:text-accent-light"
            >
              전체 보기 <ChevronRight size={14} />
            </Link>
          }
        >
          {myLeaves.length === 0 ? (
            <p className="py-4 text-[12px] text-ink-faint">신청 내역이 없습니다.</p>
          ) : (
            <ul className="divide-y divide-white/[0.10]">
              {myLeaves.map((request) => (
                <li key={request.id} className="flex items-center justify-between gap-3 py-4">
                  <div className="min-w-0">
                    <p className="truncate text-[15px] font-semibold text-ink-hi">
                      {formatDates(request.dates)}
                      <span className="ml-2 text-[14px] font-medium text-ink-mute">
                        {LEAVE_TYPE_LABEL[request.leaveType]}
                      </span>
                    </p>
                    <p className="mt-1 truncate text-[13px] text-ink-mute">
                      <span className="tabular-nums">{request.days}일</span> ·{' '}
                      {request.requestReason}
                    </p>
                  </div>
                  <div className="flex shrink-0 items-center gap-3">
                    <span className="text-[12px] text-ink-faint tabular-nums">
                      신청 {dayjs(request.createdAt).format('M/D')}
                    </span>
                    <StatusBadge status={request.status} />
                  </div>
                </li>
              ))}
            </ul>
          )}
        </Card>

        <div className="flex min-h-0 flex-col gap-5">
          <Card title="연차 소진 현황">
            <div className="flex h-3.5 overflow-hidden rounded-full border border-white/[0.10] bg-navy-app/60">
              <div className="bg-accent" style={{ width: `${usedPct}%` }} />
              <div className="bg-warn/80" style={{ width: `${pendingPct}%` }} />
            </div>
            <div className="mt-4 grid grid-cols-3 gap-2">
              <Stat swatch="bg-accent" label="사용" value={confirmedUsed} unit="일" />
              <Stat
                swatch="bg-warn/80"
                label="대기 차감"
                value={summary.pendingDays}
                unit="일"
              />
              <Stat
                swatch="bg-white/20"
                label="잔여"
                value={summary.remainingDays}
                unit="일"
              />
            </div>
            <p className="mt-4 border-t border-white/[0.12] pt-3.5 text-center text-[12px] text-ink-mute">
              미사용분은 이월 없이 소멸됩니다.
            </p>
          </Card>

          <Card title="다가오는 내 일정" fill scroll>
            {upcomingEvents.length === 0 ? (
              <EmptyState
                Icon={CalendarClock}
                label="다가오는 개인 일정이나 공휴일이 없습니다."
                className="py-8"
              />
            ) : (
              <ul className="divide-y divide-white/[0.10]">
                {upcomingEvents.map((event) => (
                  <UpcomingEventItem
                    key={`${event.date}-${event.kind}-${event.id}`}
                    event={event}
                  />
                ))}
              </ul>
            )}
          </Card>
        </div>
      </div>
    </div>
  );
}
