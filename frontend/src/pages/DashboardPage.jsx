import dayjs from 'dayjs';
import { useMemo } from 'react';
import { Link, useNavigate } from 'react-router-dom';
import { CalendarPlus, Gift, ChevronRight, Loader2 } from 'lucide-react';
import PageHeader from '../components/ui/PageHeader.jsx';
import Card from '../components/ui/Card.jsx';
import Button from '../components/ui/Button.jsx';
import Stat from '../components/ui/Stat.jsx';
import StatStrip from '../components/ui/StatStrip.jsx';
import StatusBadge from '../components/ui/StatusBadge.jsx';
import { useCurrentUser } from '../hooks/useAuth.js';
import { useLeaveCalendar, useLeaveSummary, useMyLeaves } from '../hooks/useLeaves.js';
import { holidays } from '../mocks/data.js'; // TODO(holidays API): /api/holidays 생기면 이 mock 제거
import { LEAVE_TYPE_LABEL } from '../constants/status.js';

const TODAY = dayjs().format('YYYY-MM-DD');

// 날짜 목록 요약 — "7/20 (월)" 또는 "7/20 외 2일"
function formatDates(dates) {
  const first = dayjs(dates[0]);
  const head = `${first.format('M/D')} (${'일월화수목금토'[first.day()]})`;
  return dates.length > 1 ? `${head} 외 ${dates.length - 1}일` : head;
}

export default function DashboardPage() {
  const navigate = useNavigate();
  const { data: me } = useCurrentUser();
  const summaryQuery = useLeaveSummary();
  const myLeavesQuery = useMyLeaves();
  // 이번 달 캘린더만 조회 — 월 경계 근처(말일)에는 다음 달 일정이 "다가오는 일정"에서 누락될 수 있음
  // (TODO: 필요해지면 다음 달도 함께 조회해 병합)
  const calendarQuery = useLeaveCalendar(dayjs().year(), dayjs().month() + 1);

  const summary = summaryQuery.data;
  const myLeaves = myLeavesQuery.data?.content ?? [];
  const calendarLeaves = useMemo(() => calendarQuery.data ?? [], [calendarQuery.data]);

  // 다가오는 부재 일정 — 오늘 이후의 승인 연차(캘린더 API) + 공휴일(mock)을 날짜순으로 병합.
  // 캘린더 API는 신청 건 단위(dates 배열 포함)라 날짜별 항목으로 펼쳐서(flatMap) 다룬다.
  const upcomingEvents = useMemo(() => {
    const leaveEvents = calendarLeaves.flatMap((leave) =>
      leave.dates
        .filter((date) => date >= TODAY)
        .map((date) => ({
          date,
          kind: 'leave',
          label: leave.userName,
          sub: LEAVE_TYPE_LABEL[leave.leaveType],
          mine: leave.userId === me?.id,
        })),
    );
    const holidayEvents = holidays
      .filter((h) => h.date >= TODAY)
      .map((h) => ({ date: h.date, kind: 'holiday', label: h.name, sub: '공휴일', mine: false }));
    return [...leaveEvents, ...holidayEvents].sort((a, b) => a.date.localeCompare(b.date));
  }, [calendarLeaves, me?.id]);

  // 내 신청 중 결재 대기 건수 — 관리자 결재함이 아니라 '내' 대기 건 (검증 F2)
  // 신규 신청(PENDING)과 소급 취소 신청(CANCEL_PENDING) 모두 결재자 처리를 기다리는 건이라 합산.
  // TODO(welfare API): 복리후생 신청까지 합산하려면 그쪽 API가 생긴 뒤 더해야 함.
  const isAwaitingApproval = (r) => r.status === 'PENDING' || r.status === 'CANCEL_PENDING';
  const myPendingCount = myLeaves.filter(isAwaitingApproval).length;

  // 로딩 — 통계 계산에 필요한 요약·현재유저 데이터가 없으면 진행 중 표시
  if (!summary || !me) {
    return (
      <div className="flex h-full items-center justify-center gap-2 text-ink-mute">
        <Loader2 size={18} className="animate-spin" />
        <span className="text-[13px]">불러오는 중…</span>
      </div>
    );
  }

  // 소진 현황 게이지 — 총 부여(base+bonus) 대비 확정 사용 / 대기 선차감 비율
  const totalDays = Number(summary.baseDays) + Number(summary.bonusDays);
  const confirmedUsed = Number(summary.useDays) - Number(summary.pendingDays);
  const usedPct = (confirmedUsed / totalDays) * 100;
  const pendingPct = (Number(summary.pendingDays) / totalDays) * 100;
  // 미사용 이월 없이 소멸되는 정책이라 "소멸 예정" = 잔여와 동일 (백엔드에 별도 필드 없음)
  const expiringDays = summary.remainingDays;

  // 다음 기산일까지 남은 일수
  const resetDday = summary.nextResetDate ? dayjs(summary.nextResetDate).diff(dayjs(TODAY), 'day') : null;

  return (
    <div className="flex h-full flex-col">
      {/* 헤더 — 공용 PageHeader 사용 (페이지 간 일관성) */}
      <PageHeader
        title="내 연차 현황"
        subtitle={`${dayjs(TODAY).format('YYYY년 M월 D일')} · ${me.departmentName ?? '미배정'} ${me.name}님`}
      >
        <span className="rounded-badge bg-accent/12 px-3 py-1.5 text-[12px] font-semibold text-accent-light">
          {dayjs(TODAY).year()} 회계연도
        </span>
      </PageHeader>

      {/* 통계 스트립 + 빠른 신청 — 퀵액션은 실제 신청 플로우가 있는 페이지로 이동
          (연차·반차는 캘린더의 신청 패널, 경조사는 복리후생 페이지) */}
      <div className="mb-5 flex items-end justify-between gap-6 border-b border-white/6 pb-6">
        <StatStrip>
          <Stat label="잔여 연차" value={summary.remainingDays} unit="일" size="hero" />
          <Stat label="소멸 예정" value={expiringDays} unit="일" tone="text-warn" />
          <Stat label="내 결재 대기" value={myPendingCount} unit="건" />
          <Stat
            label="다음 기산일"
            value={resetDday !== null ? `D-${resetDday}` : '-'}
            tone={resetDday !== null ? 'text-warn' : undefined}
            caption={summary.nextResetDate ? dayjs(summary.nextResetDate).format('M월 D일') : undefined}
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

      {/* 본문 2단 — 좌: 최근 신청 내역 / 우: 소진 현황 + 다가오는 부재 (캘린더는 팀 캘린더 페이지 전담) */}
      <div className="grid min-h-0 flex-1 grid-cols-1 gap-5 lg:grid-cols-[1.5fr_1fr]">
        {/* 최근 신청 내역 */}
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
            <p className="py-4 text-[12px] text-ink-dim">신청 내역이 없습니다.</p>
          ) : (
            <ul className="divide-y divide-white/6">
              {myLeaves.map((r) => (
                <li key={r.id} className="flex items-center justify-between gap-3 py-4">
                  <div className="min-w-0">
                    <p className="truncate text-[15px] font-semibold text-ink-hi">
                      {formatDates(r.dates)}
                      <span className="ml-2 text-[14px] font-medium text-ink-mute">
                        {LEAVE_TYPE_LABEL[r.leaveType]}
                      </span>
                    </p>
                    <p className="mt-1 truncate text-[13px] text-ink-mute">
                      <span className="tabular-nums">{r.days}일</span> · {r.requestReason}
                    </p>
                  </div>
                  <div className="flex shrink-0 items-center gap-3">
                    <span className="text-[12px] text-ink-dim tabular-nums">
                      신청 {dayjs(r.createdAt).format('M/D')}
                    </span>
                    <StatusBadge status={r.status} />
                  </div>
                </li>
              ))}
            </ul>
          )}
        </Card>

        {/* 우측 스택 */}
        <div className="flex min-h-0 flex-col gap-5">
          {/* 연차 소진 현황 */}
          <Card title="연차 소진 현황">
            {/* 게이지 — 사용(파랑) / 대기 선차감(주황) / 잔여(회색) */}
            <div className="flex h-3.5 overflow-hidden rounded-full bg-white/8">
              <div className="bg-accent" style={{ width: `${usedPct}%` }} />
              <div className="bg-warn/80" style={{ width: `${pendingPct}%` }} />
            </div>
            <div className="mt-4 grid grid-cols-3 gap-2">
              <Stat swatch="bg-accent" label="사용" value={confirmedUsed} unit="일" />
              <Stat swatch="bg-warn/80" label="대기 차감" value={summary.pendingDays} unit="일" />
              <Stat swatch="bg-white/25" label="잔여" value={summary.remainingDays} unit="일" />
            </div>
            <p className="mt-4 border-t border-white/6 pt-3.5 text-center text-[12px] text-ink-mute">
              미사용분은 이월 없이 소멸됩니다.
            </p>
          </Card>

          {/* 다가오는 부재 일정 */}
          <Card title="다가오는 부재 일정" fill scroll>
            {upcomingEvents.length === 0 ? (
              <p className="py-4 text-[12px] text-ink-dim">예정된 일정이 없습니다.</p>
            ) : (
              <ul className="divide-y divide-white/6">
                {upcomingEvents.map((ev) => {
                  const dday = dayjs(ev.date).diff(dayjs(TODAY), 'day');
                  return (
                    <li key={`${ev.date}-${ev.kind}-${ev.label}`} className="flex items-center justify-between gap-3 py-3">
                      <div className="flex min-w-0 items-center gap-2.5">
                        <span
                          className={`h-2.5 w-2.5 shrink-0 rounded-full ${
                            ev.kind === 'holiday' ? 'bg-danger/70' : ev.mine ? 'bg-accent' : 'bg-ok/80'
                          }`}
                        />
                        <span className="truncate text-[14px] text-ink-body">
                          {ev.label}
                          {ev.mine && <span className="ml-1 text-[12px] text-accent-light">(나)</span>}
                        </span>
                        <span className="shrink-0 text-[12px] text-ink-dim">{ev.sub}</span>
                      </div>
                      <span className="shrink-0 text-[13px] text-ink-mute tabular-nums">
                        {dayjs(ev.date).format('M/D')}
                        <span className="ml-1.5 text-[12px] text-ink-dim">
                          {dday === 0 ? '오늘' : `D-${dday}`}
                        </span>
                      </span>
                    </li>
                  );
                })}
              </ul>
            )}
          </Card>
        </div>
      </div>
    </div>
  );
}
