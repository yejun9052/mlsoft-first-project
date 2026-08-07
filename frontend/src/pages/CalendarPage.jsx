import { useEffect, useMemo, useState } from 'react';
import dayjs from 'dayjs';
import { Check, ChevronLeft, ChevronRight, Search, X } from 'lucide-react';
import PageHeader from '../components/ui/PageHeader.jsx';
import Card from '../components/ui/Card.jsx';
import IconButton from '../components/ui/IconButton.jsx';
import Legend from '../components/ui/Legend.jsx';
import TextInput from '../components/ui/TextInput.jsx';
import Select from '../components/ui/Select.jsx';
import CalendarEntryPanel from '../components/leave/CalendarEntryPanel.jsx';
import { LEAVE_TYPE_LABEL, SCHEDULE_TYPE_LABEL } from '../constants/status.js';
import { useCurrentUser } from '../hooks/useAuth.js';
import { useLeaveCalendar, useLeaveSummary } from '../hooks/useLeaves.js';
import { useScheduleCalendar } from '../hooks/useSchedules.js';
import { useDepartments } from '../hooks/useDepartments.js';
import { useHolidays } from '../hooks/useHolidays.js';

// 요일 헤더 (일요일 시작)
const WEEKDAYS = ['일', '월', '화', '수', '목', '금', '토'];
// 한 셀에 최대 표시할 일정 수 (초과분은 '+N건 더'로 요약)
const MAX_VISIBLE = 3;
// 검색어 디바운스 — 한 글자마다 요청을 날리지 않게
const SEARCH_DEBOUNCE_MS = 300;

// 개인 일정 종류별 pill 색 — 연차(파랑·초록)와 겹치지 않게 골랐다.
// 한 칸에 둘이 같이 뜨므로 색이 겹치면 "쉬는 사람"과 "일하는 사람"이 헷갈린다.
const SCHEDULE_PILL_CLASS = {
  FIELD_WORK: 'bg-amber-400/18 text-amber-300',
  BUSINESS_TRIP: 'bg-violet-400/18 text-violet-300',
  REMOTE: 'bg-emerald-400/16 text-emerald-300',
  TRAINING: 'bg-sky-400/18 text-sky-300',
};

// 연·월별 캘린더 셀 데이터(연차 + 개인 일정 + 공휴일)를 날짜별로 묶는다.
// entries는 이미 날짜 단위로 펼쳐진 목록: { date, personName, label, kind, mine, typeKey }
function buildCalendarData(entries, holidays, year, month) {
  const mm = String(month).padStart(2, '0');
  const prefix = `${year}-${mm}-`;
  const map = {};
  for (const entry of entries) {
    if (!entry.date.startsWith(prefix)) continue;
    const day = Number(entry.date.slice(8, 10));
    (map[day] ??= { entries: [], holiday: null }).entries.push(entry);
  }
  for (const h of holidays) {
    if (!h.date.startsWith(prefix)) continue;
    const day = Number(h.date.slice(8, 10));
    (map[day] ??= { entries: [], holiday: null }).holiday = h.name;
  }
  return map;
}

// 팀 캘린더 — 회사 전체 연차·개인 일정·공휴일을 큰 월간 그리드로 조회 (docs/05 §②·§2-5b)
// 날짜 셀을 클릭하면 드래그 가능한 등록 패널이 떠서, 캘린더를 보면서 날짜를 담아 등록한다.
export default function CalendarPage() {
  const { data: me } = useCurrentUser();
  const summaryQuery = useLeaveSummary();

  // 조회 기준 연·월 (초기값 = 오늘)
  const [year, setYear] = useState(dayjs().year());
  const [month, setMonth] = useState(dayjs().month() + 1); // 1-12

  // 검색 — 이름은 디바운스해서 서버로 보낸다 (입력값과 질의값을 분리)
  const [nameInput, setNameInput] = useState('');
  const [nameQuery, setNameQuery] = useState('');
  const [departmentId, setDepartmentId] = useState('');

  useEffect(() => {
    const timer = setTimeout(() => setNameQuery(nameInput.trim()), SEARCH_DEBOUNCE_MS);
    return () => clearTimeout(timer);
  }, [nameInput]);

  const filters = { keyword: nameQuery, departmentId };
  const calendarQuery = useLeaveCalendar(year, month, filters);
  const scheduleQuery = useScheduleCalendar(year, month, filters);
  const departmentsQuery = useDepartments();
  // 공휴일은 연 단위로 받는다 — 월을 넘겨도 재조회하지 않게. 실패하면 표시만 빠진다
  const holidaysQuery = useHolidays(year);
  const holidays = useMemo(() => holidaysQuery.data ?? [], [holidaysQuery.data]);

  const isFiltered = Boolean(nameQuery) || Boolean(departmentId);

  // 등록 패널 — 선택 날짜(YYYY-MM-DD)는 페이지가 소유, 패널 닫으면 선택도 초기화
  const [panelOpen, setPanelOpen] = useState(false);
  const [selectedDates, setSelectedDates] = useState([]);

  // 월 그리드(주 단위 셀 배열)와 날짜별 데이터 계산
  const { weeks, calData, blockedDates } = useMemo(() => {
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

    // 서버 응답(건 단위, dates 배열)을 날짜 단위 엔트리로 펼쳐 두 소스를 합친다
    const flatLeaves = (calendarQuery.data ?? []).flatMap((lv) =>
      lv.dates.map((date) => ({
        date,
        key: `L${lv.id}-${date}`,
        personName: lv.userName,
        label: LEAVE_TYPE_LABEL[lv.leaveType] ?? lv.leaveType,
        kind: 'LEAVE',
        typeKey: lv.leaveType,
        mine: lv.userId === me?.id,
      })),
    );
    const flatSchedules = (scheduleQuery.data ?? []).flatMap((sc) =>
      sc.dates.map((date) => ({
        date,
        key: `S${sc.id}-${date}`,
        personName: sc.userName,
        // 서버가 typeLabel을 함께 내려주므로 프론트 상수는 폴백으로만 쓴다
        label: sc.typeLabel ?? SCHEDULE_TYPE_LABEL[sc.scheduleType] ?? sc.scheduleType,
        kind: 'SCHEDULE',
        typeKey: sc.scheduleType,
        mine: sc.userId === me?.id,
      })),
    );

    // 주말·공휴일 — 연차로 신청할 수 없는 날짜. 개인 일정은 등록할 수 있으므로
    // 셀 클릭을 막지 않고 패널이 모드에 따라 판단하게 넘긴다.
    const blocked = [];
    for (let d = 1; d <= daysInMonth; d += 1) {
      const dateStr = `${year}-${String(month).padStart(2, '0')}-${String(d).padStart(2, '0')}`;
      const weekday = dayjs(dateStr).day();
      if (weekday === 0 || weekday === 6 || holidays.some((h) => h.date === dateStr)) {
        blocked.push(dateStr);
      }
    }

    return {
      weeks: rows,
      calData: buildCalendarData([...flatLeaves, ...flatSchedules], holidays, year, month),
      blockedDates: blocked,
    };
  }, [year, month, calendarQuery.data, scheduleQuery.data, holidays, me?.id]);

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

  // 날짜 클릭 — 선택 토글 + 패널 오픈.
  // 주말·공휴일도 담을 수 있다 (개인 일정은 등록 가능). 연차로 신청할 수 없다는 판단은 패널이 한다.
  function handleDayClick(dateStr) {
    setSelectedDates((prev) =>
      prev.includes(dateStr) ? prev.filter((d) => d !== dateStr) : [...prev, dateStr].sort(),
    );
    setPanelOpen(true);
  }

  function closePanel() {
    setPanelOpen(false);
    setSelectedDates([]);
  }

  function clearSearch() {
    setNameInput('');
    setNameQuery('');
    setDepartmentId('');
  }

  const departments = departmentsQuery.data ?? [];

  return (
    <div className="flex h-full flex-col">
      <PageHeader
        title="팀 캘린더"
        subtitle="회사 전체 연차·일정과 공휴일 · 날짜를 클릭하면 바로 등록"
      >
        {/* 월 이동 컨트롤 */}
        <div className="flex items-center gap-1 rounded-btn bg-navy-card p-1 shadow-card">
          <IconButton Icon={ChevronLeft} label="이전 달" onClick={() => shiftMonth(-1)} />
          <span className="min-w-[100px] text-center text-[14px] font-semibold text-ink-hi">
            {year}년 {month}월
          </span>
          <IconButton Icon={ChevronRight} label="다음 달" onClick={() => shiftMonth(1)} />
        </div>
      </PageHeader>

      {/* 검색 바 — 이름으로 한 사람만 보거나 부서로 좁힌다 */}
      <div className="mb-3 flex flex-wrap items-center gap-2">
        <div className="relative min-w-[220px] flex-1 sm:max-w-[300px]">
          <Search
            size={15}
            className="pointer-events-none absolute left-3 top-1/2 -translate-y-1/2 text-ink-faint"
          />
          <TextInput
            value={nameInput}
            onChange={(e) => setNameInput(e.target.value)}
            placeholder="이름으로 검색 (예: 박민수)"
            aria-label="이름으로 일정 검색"
            className="pl-9"
          />
        </div>
        <div className="min-w-[160px]">
          <Select
            value={departmentId}
            onChange={(e) => setDepartmentId(e.target.value)}
            aria-label="부서로 일정 필터"
          >
            <option value="">전체 부서</option>
            {departments.map((d) => (
              <option key={d.id} value={d.id}>
                {d.name}
              </option>
            ))}
          </Select>
        </div>
        {isFiltered && (
          <button
            type="button"
            onClick={clearSearch}
            className="flex items-center gap-1 rounded-badge bg-navy-app/60 px-3 py-2 text-[13px] text-ink-mute ring-1 ring-inset ring-white/[0.10] transition-colors hover:text-ink-body"
          >
            <X size={13} />
            필터 해제
          </button>
        )}
      </div>

      {/* 캘린더 카드 */}
      <Card fill padding="tight">
        {/* 범례 (사유·메모는 마스킹 정책상 캘린더에 미표시 — 이름·종류만) */}
        <div className="mb-3 flex flex-wrap items-center gap-x-4 gap-y-2 text-[12px] text-ink-mute">
          <Legend swatch="bg-accent/25 text-accent-light" label="내 연차" />
          <Legend swatch="bg-ok/18" label="동료 연차" />
          <Legend swatch="bg-amber-400/18" label="외근" />
          <Legend swatch="bg-violet-400/18" label="출장" />
          <Legend swatch="bg-emerald-400/16" label="재택" />
          <Legend swatch="bg-sky-400/18" label="교육" />
          <Legend swatch="bg-danger/18" label="공휴일" />
          <Legend swatch="border-2 border-accent-cyan bg-accent-cyan/12" label="등록 선택" Icon={Check} />
        </div>

        {/* 요일 헤더 */}
        <div className="grid grid-cols-7 gap-2 border-b border-white/[0.12] pb-2">
          {WEEKDAYS.map((weekday, i) => (
            <div
              key={weekday}
              className={`text-center text-[13px] font-semibold ${
                i === 0 ? 'text-danger/80' : 'text-ink-faint'
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
              return (
                <div key={`pad-${idx}`} className="rounded-btn border border-transparent bg-navy-app/20" />
              );
            }
            const cell = calData[day];
            const isSunday = idx % 7 === 0;
            const isToday = isTodayMonth && day === todayDate;
            const holiday = cell?.holiday;
            const entries = cell?.entries ?? [];
            const overflow = entries.length - MAX_VISIBLE;
            const dateStr = `${year}-${String(month).padStart(2, '0')}-${String(day).padStart(2, '0')}`;
            const selected = selectedDates.includes(dateStr);
            return (
              <button
                key={dateStr}
                type="button"
                onClick={() => handleDayClick(dateStr)}
                className={`flex flex-col gap-1.5 overflow-hidden rounded-btn border p-2 text-left transition-all duration-150 ${
                  selected
                    ? 'border-accent-cyan/60 bg-accent-cyan/10 hover:border-accent-cyan'
                    : holiday
                      ? 'border-danger/25 bg-danger/8 hover:border-danger/40'
                      : 'border-white/[0.10] bg-navy-app/35 hover:border-accent-cyan/35 hover:bg-navy-app/60'
                }`}
              >
                {/* 날짜 숫자 + 공휴일명 / 선택 표시 */}
                <div className="flex items-center justify-between gap-1">
                  <span
                    className={`text-[14px] font-semibold ${
                      isToday
                        ? 'inline-flex h-6 w-6 items-center justify-center rounded-full bg-accent text-navy-app shadow-btn'
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

                {/* 일정 pill — 이름 + 종류 (연차: 내것=파랑/동료=초록, 개인 일정: 종류별 색) */}
                <div className="flex min-h-0 flex-col gap-1 overflow-hidden">
                  {entries.slice(0, MAX_VISIBLE).map((entry) => (
                    <span
                      key={entry.key}
                      className={`flex items-center gap-1 rounded px-1.5 py-1 text-[12px] font-medium ${
                        entry.kind === 'SCHEDULE'
                          ? (SCHEDULE_PILL_CLASS[entry.typeKey] ?? 'bg-white/10 text-ink-body')
                          : entry.mine
                            ? 'bg-accent/25 text-accent-light'
                            : 'bg-ok/15 text-ok'
                      } ${entry.mine ? 'ring-1 ring-inset ring-white/25' : ''}`}
                    >
                      <span className="truncate">{entry.personName}</span>
                      <span className="shrink-0 opacity-75">{entry.label}</span>
                    </span>
                  ))}
                  {overflow > 0 && <span className="text-[12px] text-ink-faint">+{overflow}건 더</span>}
                </div>
              </button>
            );
          })}
        </div>

        {/* 검색 결과 없음 — 빈 캘린더와 "검색어에 안 맞음"을 구분해 준다 (리뷰 F-6과 같은 취지) */}
        {isFiltered && Object.keys(calData).length === 0 && (
          <p className="mt-3 rounded-btn bg-navy-app/50 px-3 py-2.5 text-center text-[13px] text-ink-mute">
            {nameQuery ? `'${nameQuery}'` : '선택한 부서'}의 {month}월 일정이 없습니다.
          </p>
        )}
      </Card>

      {/* 등록 패널 — 닫을 때 언마운트해서 폼 상태 초기화 */}
      {panelOpen && (
        <CalendarEntryPanel
          dates={selectedDates}
          blockedDates={blockedDates}
          remainingDays={summaryQuery.data?.remainingDays ?? 0}
          onRemoveDate={(d) => setSelectedDates((prev) => prev.filter((x) => x !== d))}
          onClose={closePanel}
          onSubmitted={() => setSelectedDates([])}
        />
      )}
    </div>
  );
}
