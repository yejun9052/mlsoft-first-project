import { useMemo, useState } from 'react';
import dayjs from 'dayjs';
import toast from 'react-hot-toast';
import { CalendarPlus, Clock3, Gift, ChevronLeft, ChevronRight } from 'lucide-react';
import PageHeader from '../components/ui/PageHeader.jsx';
import {
  TODAY,
  getCurrentUser,
  leaveSummary,
  getCalendarData,
  myLeaveRequests,
  myWelfareRequests,
} from '../mocks/data.js';

// 요일 헤더 (일요일 시작)
const WEEKDAYS = ['일', '월', '화', '수', '목', '금', '토'];

// 통계 스트립 한 칸 (박스 없음 — 세로 구분선으로 분리)
function Stat({ label, value, unit, hero, tone }) {
  return (
    <div className="flex flex-col gap-1 pr-8">
      <span className="text-[12px] font-medium text-ink-mute">{label}</span>
      <span
        className={
          hero
            ? 'text-[46px] font-extrabold leading-none tracking-[-0.03em] text-accent-light'
            : `text-[28px] font-bold leading-none ${tone ?? 'text-ink-hi'}`
        }
      >
        {value}
        {unit && <span className="ml-1 text-[14px] font-medium text-ink-mute">{unit}</span>}
      </span>
    </div>
  );
}

// 빠른 신청 버튼 (mock — 실제 신청 폼 연동 전)
function QuickAction({ Icon, label, primary }) {
  return (
    <button
      type="button"
      onClick={() => toast('연차 신청 화면은 준비 중입니다.', { icon: '🗓️' })}
      className={`flex items-center gap-1.5 rounded-btn px-3.5 py-2.5 text-[13px] font-semibold transition-colors ${
        primary
          ? 'bg-accent text-white shadow-btn hover:bg-accent-dark'
          : 'bg-navy-btn2 text-ink-body hover:bg-white/8'
      }`}
    >
      <Icon size={15} />
      {label}
    </button>
  );
}

export default function DashboardPage() {
  const me = getCurrentUser();

  // 내 신청 중 결재 대기 건수 (연차 + 복리후생) — 관리자 결재함이 아니라 '내' 대기 건 (검증 F2)
  const myPendingCount =
    myLeaveRequests.filter((r) => r.status === 'PENDING').length +
    myWelfareRequests.filter((r) => r.status === 'PENDING').length;

  // 표시 월 — 기본은 데모 기준일의 달, ◀▶로 이동
  const [viewMonth, setViewMonth] = useState(() => ({
    year: dayjs(TODAY).year(),
    month: dayjs(TODAY).month() + 1,
  }));

  function shiftMonth(delta) {
    setViewMonth(({ year, month }) => {
      const next = dayjs(new Date(year, month - 1, 1)).add(delta, 'month');
      return { year: next.year(), month: next.month() + 1 };
    });
  }

  // 표시 월 캘린더 그리드 계산
  const { weeks, monthLabel, calData } = useMemo(() => {
    const base = dayjs(new Date(viewMonth.year, viewMonth.month - 1, 1));
    const daysInMonth = base.daysInMonth();
    const startOffset = base.day(); // 1일의 요일 (0=일)
    const totalCells = Math.ceil((startOffset + daysInMonth) / 7) * 7;
    const cells = [];
    for (let i = 0; i < totalCells; i += 1) {
      const dayNum = i - startOffset + 1;
      cells.push(dayNum >= 1 && dayNum <= daysInMonth ? dayNum : null);
    }
    const rows = [];
    for (let i = 0; i < cells.length; i += 7) rows.push(cells.slice(i, i + 7));
    return {
      weeks: rows,
      monthLabel: base.format('YYYY년 M월'),
      calData: getCalendarData(viewMonth.year, viewMonth.month),
    };
  }, [viewMonth]);

  // 오늘 강조는 기준일이 속한 달에서만 (다른 달의 같은 일자 오강조 방지)
  const isTodayMonth =
    viewMonth.year === dayjs(TODAY).year() && viewMonth.month === dayjs(TODAY).month() + 1;
  const todayNum = dayjs(TODAY).date();

  return (
    <div className="flex h-full flex-col">
      {/* 헤더 — 공용 PageHeader 사용 (페이지 간 일관성) */}
      <PageHeader
        title="내 연차 현황"
        subtitle={`${dayjs(TODAY).format('YYYY년 M월 D일')} · ${me.departmentName} ${me.name}님`}
      >
        <span className="rounded-badge bg-accent/12 px-3 py-1.5 text-[12px] font-semibold text-accent-light">
          {dayjs(TODAY).year()} 회계연도
        </span>
      </PageHeader>

      {/* 통계 스트립 (박스 없음) + 빠른 신청 */}
      <div className="mb-5 flex items-end justify-between gap-6 border-b border-white/6 pb-6">
        <div className="flex items-end divide-x divide-white/8">
          <Stat label="잔여 연차" value={leaveSummary.remainingDays} unit="일" hero />
          <div className="pl-8">
            <Stat label="소멸 예정" value={leaveSummary.expiringDays} unit="일" tone="text-warn" />
          </div>
          <div className="pl-8">
            <Stat label="내 결재 대기" value={myPendingCount} unit="건" tone="text-ink-hi" />
          </div>
        </div>
        <div className="flex items-center gap-2">
          <QuickAction Icon={CalendarPlus} label="연차 신청" primary />
          <QuickAction Icon={Clock3} label="반차" />
          <QuickAction Icon={Gift} label="경조사" />
        </div>
      </div>

      {/* 팀 연차 캘린더 카드 */}
      <section className="flex min-h-0 flex-1 flex-col rounded-card bg-navy-card p-5 shadow-card">
        <div className="mb-4 flex items-center justify-between">
          <h2 className="text-[15px] font-semibold text-ink-hi">팀 연차 캘린더</h2>
          <div className="flex items-center gap-3 text-ink-mute">
            <button
              type="button"
              onClick={() => shiftMonth(-1)}
              className="rounded-btn p-1 hover:bg-white/5"
              aria-label="이전 달"
            >
              <ChevronLeft size={16} />
            </button>
            <span className="text-[13px] font-semibold text-ink-body">{monthLabel}</span>
            <button
              type="button"
              onClick={() => shiftMonth(1)}
              className="rounded-btn p-1 hover:bg-white/5"
              aria-label="다음 달"
            >
              <ChevronRight size={16} />
            </button>
          </div>
        </div>

        {/* 범례 */}
        <div className="mb-3 flex items-center gap-4 text-[11px] text-ink-mute">
          <Legend className="bg-accent/25 border border-accent/40" label="내 연차" />
          <Legend className="bg-ok/18" label="동료 연차" />
          <Legend className="bg-danger/18" label="공휴일" />
        </div>

        {/* 요일 헤더 */}
        <div className="grid grid-cols-7 gap-1.5 pb-2">
          {WEEKDAYS.map((d, i) => (
            <div
              key={d}
              className={`text-center text-[11px] font-semibold ${
                i === 0 ? 'text-danger/80' : 'text-ink-dim'
              }`}
            >
              {d}
            </div>
          ))}
        </div>

        {/* 날짜 그리드 */}
        <div className="grid min-h-0 flex-1 auto-rows-fr grid-cols-7 gap-1.5">
          {weeks.flat().map((day, idx) => {
            if (day === null) return <div key={idx} />;
            const cell = calData[day];
            const isSunday = idx % 7 === 0;
            const isToday = isTodayMonth && day === todayNum;
            const holiday = cell?.holiday;
            const leaves = cell?.leaves ?? [];
            return (
              <div
                key={idx}
                className={`flex flex-col gap-1 rounded-btn border p-1.5 transition-colors ${
                  holiday
                    ? 'border-danger/25 bg-danger/8'
                    : 'border-white/6 bg-navy-app/40 hover:border-white/12'
                }`}
              >
                <span
                  className={`text-[11px] font-semibold ${
                    isToday
                      ? 'inline-flex h-5 w-5 items-center justify-center rounded-full bg-accent text-white'
                      : holiday || isSunday
                        ? 'text-danger/90'
                        : 'text-ink-mute'
                  }`}
                >
                  {day}
                </span>
                <div className="flex flex-col gap-0.5 overflow-hidden">
                  {holiday && (
                    <span className="truncate text-[10px] font-medium text-danger/90">{holiday}</span>
                  )}
                  {leaves.slice(0, 2).map((lv, i) => (
                    <span
                      key={i}
                      className={`truncate rounded px-1 py-0.5 text-[10px] font-medium ${
                        lv.mine ? 'bg-accent/25 text-accent-light' : 'bg-ok/15 text-ok'
                      }`}
                    >
                      {lv.personName}
                    </span>
                  ))}
                  {leaves.length > 2 && (
                    <span className="text-[10px] text-ink-dim">+{leaves.length - 2}</span>
                  )}
                </div>
              </div>
            );
          })}
        </div>
      </section>
    </div>
  );
}

function Legend({ className, label }) {
  return (
    <span className="flex items-center gap-1.5">
      <span className={`h-3 w-3 rounded ${className}`} />
      {label}
    </span>
  );
}
