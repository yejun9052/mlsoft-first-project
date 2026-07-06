import { useMemo, useState } from 'react';
import dayjs from 'dayjs';
import toast from 'react-hot-toast';
import { ChevronLeft, ChevronRight } from 'lucide-react';
import PageHeader from '../components/ui/PageHeader.jsx';
import { TODAY, getCalendarData, LEAVE_TYPE_LABEL } from '../mocks/data.js';

// 요일 헤더 (일요일 시작)
const WEEKDAYS = ['일', '월', '화', '수', '목', '금', '토'];
// 한 셀에 최대 표시할 일정 수 (초과분은 '+N건 더'로 요약)
const MAX_VISIBLE = 3;

// 팀 캘린더 — 회사 전체 연차·공휴일을 큰 월간 그리드로 조회 (docs/05 §②·§2-5b)
export default function CalendarPage() {
  // 조회 기준 연·월 (초기값 = 데모 오늘)
  const [year, setYear] = useState(dayjs(TODAY).year());
  const [month, setMonth] = useState(dayjs(TODAY).month() + 1); // 1-12

  // 월 그리드(주 단위 셀 배열)와 날짜별 데이터 계산 — 연·월이 바뀔 때만 재계산
  const { weeks, calData } = useMemo(() => {
    const base = dayjs(`${year}-${String(month).padStart(2, '0')}-01`);
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
    // 데이터 없는 달은 빈 맵을 반환 → 빈 캘린더로 정상 표시
    return { weeks: rows, calData: getCalendarData(year, month) };
  }, [year, month]);

  // 오늘 강조는 조회 중인 달이 실제 '오늘'의 달과 같을 때만 적용
  const today = dayjs(TODAY);
  const isTodayMonth = year === today.year() && month === today.month() + 1;
  const todayDate = today.date();

  // 월 이동 — 1월/12월 경계에서 연도까지 넘김
  function shiftMonth(delta) {
    let nextMonth = month + delta;
    let nextYear = year;
    if (nextMonth < 1) {
      nextMonth = 12;
      nextYear -= 1;
    } else if (nextMonth > 12) {
      nextMonth = 1;
      nextYear += 1;
    }
    setYear(nextYear);
    setMonth(nextMonth);
  }

  return (
    <div className="flex h-full flex-col">
      <PageHeader title="팀 캘린더" subtitle="회사 전체 연차 일정과 공휴일">
        {/* 월 이동 컨트롤 */}
        <div className="flex items-center gap-1 rounded-btn bg-navy-card p-1 shadow-card">
          <button
            type="button"
            onClick={() => shiftMonth(-1)}
            className="rounded-btn p-1.5 text-ink-mute transition-colors hover:bg-white/6 hover:text-ink-body"
            aria-label="이전 달"
          >
            <ChevronLeft size={18} />
          </button>
          <span className="min-w-[100px] text-center text-[14px] font-semibold text-ink-hi">
            {year}년 {month}월
          </span>
          <button
            type="button"
            onClick={() => shiftMonth(1)}
            className="rounded-btn p-1.5 text-ink-mute transition-colors hover:bg-white/6 hover:text-ink-body"
            aria-label="다음 달"
          >
            <ChevronRight size={18} />
          </button>
        </div>
      </PageHeader>

      {/* 캘린더 카드 */}
      <section className="flex min-h-0 flex-1 flex-col rounded-card bg-navy-card p-5 shadow-card">
        {/* 범례 (사유는 마스킹 정책상 캘린더에 미표시 — 이름·종류만) */}
        <div className="mb-3 flex items-center gap-4 text-[11px] text-ink-mute">
          <Legend className="border border-accent/40 bg-accent/25" label="내 연차" />
          <Legend className="bg-ok/18" label="동료 연차" />
          <Legend className="bg-danger/18" label="공휴일" />
        </div>

        {/* 요일 헤더 */}
        <div className="grid grid-cols-7 gap-2 border-b border-white/6 pb-2">
          {WEEKDAYS.map((weekday, i) => (
            <div
              key={weekday}
              className={`text-center text-[12px] font-semibold ${
                i === 0 ? 'text-danger/80' : 'text-ink-dim'
              }`}
            >
              {weekday}
            </div>
          ))}
        </div>

        {/* 날짜 그리드 (auto-rows-fr + flex-1 로 카드 높이를 꽉 채움) */}
        <div className="mt-2 grid min-h-0 flex-1 auto-rows-fr grid-cols-7 gap-2">
          {weeks.flat().map((day, idx) => {
            if (day === null) {
              return <div key={idx} className="rounded-btn border border-transparent bg-navy-app/20" />;
            }
            const cell = calData[day];
            const isSunday = idx % 7 === 0;
            const isToday = isTodayMonth && day === todayDate;
            const holiday = cell?.holiday;
            const leaves = cell?.leaves ?? [];
            const overflow = leaves.length - MAX_VISIBLE;
            return (
              <button
                key={idx}
                type="button"
                onClick={() => toast('해당 날짜로 연차 신청 (준비 중)', { icon: '🗓️' })}
                className={`flex flex-col gap-1.5 overflow-hidden rounded-btn border p-2 text-left transition-colors ${
                  holiday
                    ? 'border-danger/25 bg-danger/8 hover:border-danger/40'
                    : 'border-white/6 bg-navy-app/40 hover:border-accent/40 hover:bg-navy-app/60'
                }`}
              >
                {/* 날짜 숫자 + 공휴일명 */}
                <div className="flex items-center justify-between gap-1">
                  <span
                    className={`text-[13px] font-semibold ${
                      isToday
                        ? 'inline-flex h-6 w-6 items-center justify-center rounded-full bg-accent text-white'
                        : holiday || isSunday
                          ? 'text-danger/90'
                          : 'text-ink-body'
                    }`}
                  >
                    {day}
                  </span>
                  {holiday && (
                    <span className="truncate text-[11px] font-medium text-danger/85">{holiday}</span>
                  )}
                </div>

                {/* 연차 일정 pill — 이름 + 종류 표시 (내 연차=파랑 / 동료=초록, 마스킹 대상은 사유뿐) */}
                <div className="flex min-h-0 flex-col gap-1 overflow-hidden">
                  {leaves.slice(0, MAX_VISIBLE).map((leave, i) => (
                    <span
                      key={i}
                      className={`flex items-center gap-1 rounded px-1.5 py-0.5 text-[11px] font-medium ${
                        leave.mine ? 'bg-accent/25 text-accent-light' : 'bg-ok/15 text-ok'
                      }`}
                    >
                      <span className="truncate">{leave.personName}</span>
                      <span className="shrink-0 opacity-75">{LEAVE_TYPE_LABEL[leave.type]}</span>
                    </span>
                  ))}
                  {overflow > 0 && <span className="text-[11px] text-ink-dim">+{overflow}건 더</span>}
                </div>
              </button>
            );
          })}
        </div>
      </section>
    </div>
  );
}

// 범례 항목 — 색 스와치 + 라벨
function Legend({ className, label }) {
  return (
    <span className="flex items-center gap-1.5">
      <span className={`h-3 w-3 rounded ${className}`} />
      {label}
    </span>
  );
}
