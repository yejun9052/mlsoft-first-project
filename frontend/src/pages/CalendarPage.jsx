import { useMemo, useState } from 'react';
import dayjs from 'dayjs';
import toast from 'react-hot-toast';
import { Check, ChevronLeft, ChevronRight } from 'lucide-react';
import PageHeader from '../components/ui/PageHeader.jsx';
import LeaveApplyPanel from '../components/leave/LeaveApplyPanel.jsx';
import { LEAVE_TYPE_LABEL } from '../constants/status.js';
import { useCurrentUser } from '../hooks/useAuth.js';
import { useLeaveCalendar, useLeaveSummary } from '../hooks/useLeaves.js';
import { holidays } from '../mocks/data.js'; // TODO(holidays API): /api/holidays 생기면 이 mock 제거

// 요일 헤더 (일요일 시작)
const WEEKDAYS = ['일', '월', '화', '수', '목', '금', '토'];
// 한 셀에 최대 표시할 일정 수 (초과분은 '+N건 더'로 요약)
const MAX_VISIBLE = 3;

// 서브 승인자 후보 — 승인자 후보 API가 아직 없어(다음에 직접 설계 예정) 로컬 DB에 미리
// 넣어둔 실제 TEAM_LEADER 테스트 계정(id 2, 3)을 임시로 하드코딩. 실제 존재하는 유저라
// 신청 시 정상적으로 서브 승인자로 지정된다. API가 생기면 이 배열만 걷어내면 됨.
const TEST_APPROVER_CANDIDATES = [
  { id: 2, name: '테스트팀장1', departmentName: '미배정' },
  { id: 3, name: '테스트팀장2', departmentName: '미배정' },
];

// 연·월별 캘린더 셀 데이터(연차 + 공휴일)를 날짜별로 묶는다.
// leaves는 이미 날짜 단위로 펼쳐진 목록: { date, personName, type, mine }
function buildCalendarData(leaves, year, month) {
  const mm = String(month).padStart(2, '0');
  const prefix = `${year}-${mm}-`;
  const map = {};
  for (const lv of leaves) {
    if (!lv.date.startsWith(prefix)) continue;
    const day = Number(lv.date.slice(8, 10));
    (map[day] ??= { leaves: [], holiday: null }).leaves.push(lv);
  }
  for (const h of holidays) {
    if (!h.date.startsWith(prefix)) continue;
    const day = Number(h.date.slice(8, 10));
    (map[day] ??= { leaves: [], holiday: null }).holiday = h.name;
  }
  return map;
}

// 팀 캘린더 — 회사 전체 연차·공휴일을 큰 월간 그리드로 조회 (docs/05 §②·§2-5b)
// 날짜 셀을 클릭하면 드래그 가능한 신청 패널이 떠서, 캘린더를 보면서 날짜를 담아 신청한다.
export default function CalendarPage() {
  const { data: me } = useCurrentUser();
  const summaryQuery = useLeaveSummary();

  // 조회 기준 연·월 (초기값 = 오늘)
  const [year, setYear] = useState(dayjs().year());
  const [month, setMonth] = useState(dayjs().month() + 1); // 1-12

  const calendarQuery = useLeaveCalendar(year, month);

  // 신청 패널 — 선택 날짜(YYYY-MM-DD)는 페이지가 소유, 패널 닫으면 선택도 초기화
  const [panelOpen, setPanelOpen] = useState(false);
  const [selectedDates, setSelectedDates] = useState([]);

  // 월 그리드(주 단위 셀 배열)와 날짜별 데이터 계산 — 연·월 또는 캘린더 응답이 바뀔 때만 재계산
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

    // 서버 응답(신청 건 단위, dates 배열)을 날짜 단위 엔트리로 펼침
    const flatLeaves = (calendarQuery.data ?? []).flatMap((lv) =>
      lv.dates.map((date) => ({
        date,
        personName: lv.userName,
        type: lv.leaveType,
        mine: lv.userId === me?.id,
      })),
    );
    // 데이터 없는 달(로딩 중 포함)은 빈 맵을 반환 → 빈 캘린더로 정상 표시
    return { weeks: rows, calData: buildCalendarData(flatLeaves, year, month) };
  }, [year, month, calendarQuery.data, me?.id]);

  // 오늘 강조는 조회 중인 달이 실제 '오늘'의 달과 같을 때만 적용
  const today = dayjs();
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

  // 날짜 클릭 — 선택 토글 + 패널 오픈. 주말·공휴일은 신청 대상 제외 (주말 필터 원칙은 백엔드, UI는 안내만)
  function handleDayClick(dateStr, blocked) {
    if (blocked) {
      toast('주말·공휴일은 연차 신청 대상이 아니에요.', { icon: '⛱️' });
      return;
    }
    setSelectedDates((prev) =>
      prev.includes(dateStr) ? prev.filter((d) => d !== dateStr) : [...prev, dateStr].sort(),
    );
    setPanelOpen(true);
  }

  function closePanel() {
    setPanelOpen(false);
    setSelectedDates([]);
  }

  return (
    <div className="flex h-full flex-col">
      <PageHeader title="팀 캘린더" subtitle="회사 전체 연차 일정과 공휴일 · 날짜를 클릭하면 바로 신청">
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
        <div className="mb-3 flex items-center gap-4 text-[12px] text-ink-mute">
          <Legend className="border border-accent/40 bg-accent/25" label="내 연차" />
          <Legend className="bg-ok/18" label="동료 연차" />
          <Legend className="bg-danger/18" label="공휴일" />
          <Legend className="border border-accent/70 bg-accent/12" label="신청 선택" />
        </div>

        {/* 요일 헤더 */}
        <div className="grid grid-cols-7 gap-2 border-b border-white/6 pb-2">
          {WEEKDAYS.map((weekday, i) => (
            <div
              key={weekday}
              className={`text-center text-[13px] font-semibold ${
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
              return <div key={`pad-${idx}`} className="rounded-btn border border-transparent bg-navy-app/20" />;
            }
            const cell = calData[day];
            const isSunday = idx % 7 === 0;
            const isSaturday = idx % 7 === 6;
            const isToday = isTodayMonth && day === todayDate;
            const holiday = cell?.holiday;
            const leaves = cell?.leaves ?? [];
            const overflow = leaves.length - MAX_VISIBLE;
            const dateStr = `${year}-${String(month).padStart(2, '0')}-${String(day).padStart(2, '0')}`;
            const selected = selectedDates.includes(dateStr);
            return (
              <button
                key={dateStr}
                type="button"
                onClick={() => handleDayClick(dateStr, Boolean(holiday) || isSunday || isSaturday)}
                className={`flex flex-col gap-1.5 overflow-hidden rounded-btn border p-2 text-left transition-colors ${
                  selected
                    ? 'border-accent/70 bg-accent/12 hover:border-accent'
                    : holiday
                      ? 'border-danger/25 bg-danger/8 hover:border-danger/40'
                      : 'border-white/6 bg-navy-app/40 hover:border-accent/40 hover:bg-navy-app/60'
                }`}
              >
                {/* 날짜 숫자 + 공휴일명 / 선택 표시 */}
                <div className="flex items-center justify-between gap-1">
                  <span
                    className={`text-[14px] font-semibold ${
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
                    <span className="truncate text-[12px] font-medium text-danger/85">{holiday}</span>
                  )}
                  {selected && <Check size={14} className="shrink-0 text-accent-light" />}
                </div>

                {/* 연차 일정 pill — 이름 + 종류 표시 (내 연차=파랑 / 동료=초록, 마스킹 대상은 사유뿐) */}
                <div className="flex min-h-0 flex-col gap-1 overflow-hidden">
                  {leaves.slice(0, MAX_VISIBLE).map((leave) => (
                    <span
                      key={`${leave.personName}-${leave.type}`}
                      className={`flex items-center gap-1 rounded px-1.5 py-1 text-[12px] font-medium ${
                        leave.mine ? 'bg-accent/25 text-accent-light' : 'bg-ok/15 text-ok'
                      }`}
                    >
                      <span className="truncate">{leave.personName}</span>
                      <span className="shrink-0 opacity-75">{LEAVE_TYPE_LABEL[leave.type]}</span>
                    </span>
                  ))}
                  {overflow > 0 && <span className="text-[12px] text-ink-dim">+{overflow}건 더</span>}
                </div>
              </button>
            );
          })}
        </div>
      </section>

      {/* 신청 패널 — 닫을 때 언마운트해서 폼 상태 초기화 */}
      {panelOpen && (
        <LeaveApplyPanel
          dates={selectedDates}
          remainingDays={summaryQuery.data?.remainingDays ?? 0}
          approvers={TEST_APPROVER_CANDIDATES}
          onRemoveDate={(d) => setSelectedDates((prev) => prev.filter((x) => x !== d))}
          onClose={closePanel}
        />
      )}
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
