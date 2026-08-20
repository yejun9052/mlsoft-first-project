import { useMemo, useState } from 'react';

const WEEKDAY_LABELS = ['일', '월', '화', '수', '목', '금', '토'];
const MONTH_LABELS = ['1월', '2월', '3월', '4월', '5월', '6월', '7월', '8월', '9월', '10월', '11월', '12월'];

function toDateKey(date) {
  return [
    date.getFullYear(),
    String(date.getMonth() + 1).padStart(2, '0'),
    String(date.getDate()).padStart(2, '0'),
  ].join('-');
}

function buildCalendarDays(year) {
  const first = new Date(year, 0, 1);
  const last = new Date(year, 11, 31);
  const start = new Date(year, 0, 1 - first.getDay());
  const end = new Date(year, 11, 31 + (6 - last.getDay()));
  const days = [];

  for (const cursor = new Date(start); cursor <= end; cursor.setDate(cursor.getDate() + 1)) {
    days.push(new Date(cursor));
  }
  return days;
}

function getLevel(value, mode) {
  if (value <= 0) return 0;

  if (mode === 'team') {
    if (value >= 4) return 4;
    return Math.ceil(value);
  }

  if (value >= 2) return 4;
  if (value >= 1.5) return 3;
  if (value >= 1) return 2;
  return 1;
}

function getCellClass({ level, nonWorkingDay, outsideYear }) {
  if (outsideYear) return 'bg-transparent';
  if (level === 4) return 'bg-heatmap-4';
  if (level === 3) return 'bg-heatmap-3';
  if (level === 2) return 'bg-heatmap-2';
  if (level === 1) return 'bg-heatmap-1';
  if (nonWorkingDay) return 'bg-heatmap-rest';
  return 'bg-heatmap-empty';
}

function formatValue(value, mode) {
  if (mode === 'team') return `${value}명`;
  return `${Number(value).toFixed(Number(value) % 1 === 0 ? 0 : 1)}일`;
}

/**
 * 연 단위 연차 사용 히트맵.
 *
 * 호버와 키보드 포커스가 같은 상태를 사용해야 두 입력 방식에서 내용과 닫힘 동작이
 * 달라지지 않는다. 셀은 의미 있는 날짜 컨트롤이므로 div에 tabIndex를 붙이지 않고 button을 쓴다.
 */
export default function LeaveHeatmap({
  year,
  entries,
  holidays = [],
  mode = 'personal',
  loading = false,
  error = false,
  onRetry,
}) {
  const [activeDate, setActiveDate] = useState(null);

  const valueByDate = useMemo(
    () => new Map(entries.map((entry) => [entry.date, Number(entry.days ?? entry.memberCount ?? 0)])),
    [entries],
  );
  const holidayDates = useMemo(() => new Set(holidays.map((holiday) => holiday.date)), [holidays]);
  const days = useMemo(() => buildCalendarDays(year), [year]);

  if (loading) {
    return <p className="py-8 text-center text-[13px] text-ink-mute">연차 사용 기록을 불러오는 중입니다.</p>;
  }

  if (error) {
    return (
      <div className="flex flex-col items-center gap-3 py-8">
        <p className="text-[13px] text-ink-mute">연차 사용 기록을 불러오지 못했습니다.</p>
        <button
          type="button"
          className="rounded-btn border border-white/[0.14] px-3 py-2 text-[12px] text-ink-body"
          onClick={onRetry}
        >
          다시 시도
        </button>
      </div>
    );
  }

  const firstMonthByColumn = new Map();
  days.forEach((date, index) => {
    if (date.getFullYear() !== year || date.getDate() > 7) return;
    firstMonthByColumn.set(Math.floor(index / 7), MONTH_LABELS[date.getMonth()]);
  });

  return (
    <div className="overflow-x-auto pb-2">
      <div className="min-w-max">
        <div className="ml-7 grid h-5 grid-flow-col grid-rows-1 gap-[3px]">
          {Array.from({ length: days.length / 7 }, (_, column) => (
            <span key={column} className="w-3 text-[10px] text-ink-faint">
              {firstMonthByColumn.get(column) ?? ''}
            </span>
          ))}
        </div>

        <div className="flex gap-2">
          <div className="grid grid-rows-7 gap-[3px] pt-0 text-[10px] text-ink-faint">
            {WEEKDAY_LABELS.map((label, index) => (
              <span key={label} className="flex h-3 w-5 items-center">
                {index % 2 === 1 ? label : ''}
              </span>
            ))}
          </div>

          <div
            className="grid grid-flow-col grid-rows-7 gap-[3px]"
            aria-label={`${year}년 ${mode === 'team' ? '팀' : '개인'} 연차 사용 히트맵`}
          >
            {days.map((date) => {
              const dateKey = toDateKey(date);
              const outsideYear = date.getFullYear() !== year;
              const value = valueByDate.get(dateKey) ?? 0;
              const weekend = date.getDay() === 0 || date.getDay() === 6;
              const holiday = holidayDates.has(dateKey);
              const nonWorkingDay = weekend || holiday;
              const level = getLevel(value, mode);
              const tooltip = `${dateKey} · ${formatValue(value, mode)}`;

              return (
                <span key={dateKey} className="relative h-3 w-3">
                  <button
                    type="button"
                    aria-label={tooltip}
                    disabled={outsideYear}
                    className={`block h-3 w-3 rounded-heatmap outline-none ring-accent-light focus-visible:ring-2 ${getCellClass({
                      level,
                      nonWorkingDay,
                      outsideYear,
                    })}`}
                    onMouseEnter={() => setActiveDate(dateKey)}
                    onMouseLeave={() => setActiveDate(null)}
                    onFocus={() => setActiveDate(dateKey)}
                    onBlur={() => setActiveDate(null)}
                  />
                  {activeDate === dateKey && !outsideYear && (
                    <span
                      role="tooltip"
                      className="absolute bottom-full left-1/2 z-20 mb-2 w-max -translate-x-1/2 rounded-btn border border-white/[0.14] bg-navy-header px-2 py-1 text-[11px] text-ink-hi shadow-card"
                    >
                      {tooltip}
                    </span>
                  )}
                </span>
              );
            })}
          </div>
        </div>

        <div className="mt-3 flex items-center justify-end gap-1 text-[10px] text-ink-faint">
          <span>적음</span>
          <span className="h-3 w-3 rounded-heatmap bg-heatmap-empty" />
          <span className="h-3 w-3 rounded-heatmap bg-heatmap-1" />
          <span className="h-3 w-3 rounded-heatmap bg-heatmap-2" />
          <span className="h-3 w-3 rounded-heatmap bg-heatmap-3" />
          <span className="h-3 w-3 rounded-heatmap bg-heatmap-4" />
          <span>많음</span>
          <span className="ml-3 h-3 w-3 rounded-heatmap bg-heatmap-rest" />
          <span>주말·공휴일</span>
        </div>
      </div>
    </div>
  );
}
