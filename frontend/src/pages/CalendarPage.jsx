import { useEffect, useMemo, useRef, useState } from 'react';
import dayjs from 'dayjs';
import { CalendarDays, Check, ChevronLeft, ChevronRight, Search, X } from 'lucide-react';
import PageHeader from '../components/ui/PageHeader.jsx';
import Card from '../components/ui/Card.jsx';
import IconButton from '../components/ui/IconButton.jsx';
import Legend from '../components/ui/Legend.jsx';
import TextInput from '../components/ui/TextInput.jsx';
import Select from '../components/ui/Select.jsx';
import Modal from '../components/ui/Modal.jsx';
import CalendarEntryPanel from '../components/leave/CalendarEntryPanel.jsx';
import { LEAVE_TYPE_LABEL, SCHEDULE_TYPE_LABEL } from '../constants/status.js';
import { useCurrentUser } from '../hooks/useAuth.js';
import useMediaQuery from '../hooks/useMediaQuery.js';
import { useLeaveCalendar, useLeaveSummary } from '../hooks/useLeaves.js';
import { useScheduleCalendar } from '../hooks/useSchedules.js';
import { useDepartments } from '../hooks/useDepartments.js';
import { useHolidays } from '../hooks/useHolidays.js';

// 요일 헤더 (일요일 시작)
const WEEKDAYS = ['일', '월', '화', '수', '목', '금', '토'];
// 하루 셀에서 직접 보여줄 일정 수. 초과 일정은 상세 모달에서 확인한다.
const MAX_VISIBLE = 2;
// 여러 다일 일정이 같은 주에 겹칠 때 화면에 유지할 막대 레인 수.
const MAX_BAR_LANES = 3;
// 연속 막대는 셀 안의 단일 pill과 같은 크기로 그린다 — 실측 pill: 26px 높이, 셀 위에서 36px.
// 레인 사이는 pill gap 4px.
const SPAN_BAR_HEIGHT = 26;
const SPAN_LANE_PITCH = SPAN_BAR_HEIGHT + 4;
const SPAN_TOP_OFFSET = 36;
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

const SCHEDULE_DOT_CLASS = {
  FIELD_WORK: 'bg-amber-300',
  BUSINESS_TRIP: 'bg-violet-300',
  REMOTE: 'bg-emerald-300',
  TRAINING: 'bg-sky-300',
};

function entryTone(entry) {
  if (entry.kind === 'HOLIDAY') {
    return 'bg-danger/18 text-danger';
  }
  if (entry.kind === 'SCHEDULE') {
    return SCHEDULE_PILL_CLASS[entry.typeKey] ?? 'bg-white/10 text-ink-body';
  }
  return entry.mine ? 'bg-accent/25 text-accent-light' : 'bg-ok/15 text-ok';
}

function entryDotTone(entry) {
  if (entry.kind === 'HOLIDAY') {
    return 'bg-danger';
  }
  if (entry.kind === 'SCHEDULE') {
    return SCHEDULE_DOT_CLASS[entry.typeKey] ?? 'bg-ink-mute';
  }
  return entry.mine ? 'bg-accent-cyan' : 'bg-ok';
}

function entryKindLabel(entry) {
  if (entry.kind === 'HOLIDAY') return '공휴일';
  if (entry.kind === 'SCHEDULE') return '개인 일정';
  return '연차';
}

// 같은 이름이 이어지는 공휴일(추석 3일 등)을 한 묶음으로 만든다. 묶음의 eventId는
// 첫날 기준이라 셀 pill과 연속 막대가 같은 키로 만나고, 하루짜리는 그대로 pill로 남는다.
function groupHolidayRuns(holidays) {
  const sorted = [...holidays].sort((left, right) => left.date.localeCompare(right.date));
  const runs = [];
  sorted.forEach((holiday) => {
    const last = runs[runs.length - 1];
    const consecutive =
      last &&
      last.name === holiday.name &&
      dayjs(holiday.date).diff(dayjs(last.dates[last.dates.length - 1]), 'day') === 1;
    if (consecutive) {
      last.dates.push(holiday.date);
    } else {
      runs.push({ name: holiday.name, dates: [holiday.date] });
    }
  });
  return runs;
}

// 연·월별 캘린더 셀 데이터(연차 + 개인 일정 + 공휴일)를 날짜별로 묶는다.
// entries는 이미 날짜 단위로 펼쳐진 목록: { date, personName, label, kind, mine, typeKey }
function buildCalendarData(entries, holidays, year, month) {
  const mm = String(month).padStart(2, '0');
  const prefix = `${year}-${mm}-`;
  const map = {};
  for (const entry of entries) {
    if (!entry.date.startsWith(prefix)) continue;
    const day = Number(entry.date.slice(8, 10));
    (map[day] ??= { entries: [] }).entries.push(entry);
  }
  for (const run of groupHolidayRuns(holidays)) {
    const eventId = `H${run.dates[0]}`;
    for (const date of run.dates) {
      if (!date.startsWith(prefix)) continue;
      const day = Number(date.slice(8, 10));
      const cell = (map[day] ??= { entries: [] });
      if (!cell.entries.some((entry) => entry.kind === 'HOLIDAY')) {
        cell.entries.push({
          date,
          eventId,
          eventDateKey: `${eventId}:${date}`,
          key: `H${date}`,
          personName: '',
          label: run.name,
          kind: 'HOLIDAY',
          typeKey: 'HOLIDAY',
          mine: false,
          detail: null,
        });
      }
    }
  }
  return map;
}

// 연속된 날짜를 차지하는 하나의 연차/일정을 주별 가로 막대 구간으로 만든다.
// 주가 바뀌는 경우에만 화면상 구간이 나뉘며, 각 날짜에는 별도의 pill을 그리지 않는다.
function getCalendarCellDate(year, month, index) {
  const firstDay = dayjs(`${year}-${String(month).padStart(2, '0')}-01`);
  return firstDay.add(index - firstDay.day(), 'day');
}

function buildCalendarSpanData(records, weeks, year, month) {
  const prefix = String(year) + '-' + String(month).padStart(2, '0') + '-';
  const datePosition = new Map();

  weeks.forEach((week, weekIndex) => {
    week.forEach((day, dayIndex) => {
      if (day === null) return;
      const date = prefix + String(day).padStart(2, '0');
      datePosition.set(date, { weekIndex, dayIndex });
    });
  });

  const segmentsByWeek = weeks.map(() => []);
  const multiDayEventKeys = new Set();

  records.forEach((record) => {
    const sortedDates = [...new Set(record.dates ?? [])].sort();
    let run = [];

    const flushRun = () => {
      const visibleDates = run.filter((date) => datePosition.has(date));
      if (visibleDates.length === 0 || run.length < 2) {
        run = [];
        return;
      }

      const datesByWeek = new Map();
      visibleDates.forEach((date) => {
        const position = datePosition.get(date);
        const weekDates = datesByWeek.get(position.weekIndex) ?? [];
        weekDates.push({ date, ...position });
        datesByWeek.set(position.weekIndex, weekDates);
      });

      datesByWeek.forEach((weekDates, weekIndex) => {
        const firstDate = weekDates[0].date;
        const lastDate = weekDates[weekDates.length - 1].date;
        const firstVisibleIndex = visibleDates.indexOf(firstDate);
        const lastVisibleIndex = visibleDates.indexOf(lastDate);
        const firstRunIndex = run.indexOf(firstDate);
        const lastRunIndex = run.indexOf(lastDate);

        segmentsByWeek[weekIndex].push({
          id: [record.id, weekIndex, firstDate].join('-'),
          entry: { ...record.entry, key: record.id },
          dates: weekDates.map(({ date }) => date),
          startColumn: Math.min(...weekDates.map(({ dayIndex }) => dayIndex)),
          endColumn: Math.max(...weekDates.map(({ dayIndex }) => dayIndex)),
          rangeStart: run[0],
          rangeEnd: run[run.length - 1],
          continuesBefore: firstRunIndex > 0 || firstVisibleIndex > 0,
          continuesAfter:
            lastRunIndex < run.length - 1 || lastVisibleIndex < visibleDates.length - 1,
        });
      });

      run = [];
    };

    sortedDates.forEach((date, index) => {
      const previous = sortedDates[index - 1];
      if (previous && dayjs(date).diff(dayjs(previous), 'day') !== 1) {
        flushRun();
      }
      run.push(date);
    });
    flushRun();
  });

  segmentsByWeek.forEach((segments, weekIndex) => {
    const lanes = [];
    segments.sort((left, right) => left.startColumn - right.startColumn || left.endColumn - right.endColumn);

    segments.forEach((segment) => {
      let lane = 0;
      while (
        (lanes[lane] ?? []).some(
          (placed) =>
            placed.startColumn <= segment.endColumn && segment.startColumn <= placed.endColumn,
        )
      ) {
        lane += 1;
      }
      (lanes[lane] ??= []).push(segment);
      segment.lane = lane;
    });

    segmentsByWeek[weekIndex] = segments.filter((segment) => {
      if (segment.lane >= MAX_BAR_LANES) return false;
      segment.dates.forEach((date) => {
        multiDayEventKeys.add(segment.entry.key + ':' + date);
      });
      return true;
    });
  });

  return { segmentsByWeek, multiDayEventKeys };
}

function CalendarDayDetail({ detail, onClose }) {
  const formattedDate = dayjs(detail.date).format('YYYY년 M월 D일 (ddd)');

  return (
    <Modal title={`${formattedDate} 일정`} onClose={onClose} maxWidth={560}>
      <div className="space-y-3">
        {detail.entries.map((entry) => (
          <article
            key={entry.key}
            className="rounded-btn border border-white/[0.12] bg-navy-app/45 px-4 py-3"
          >
            <div className="flex flex-wrap items-center gap-2">
              <span className={`rounded-badge px-2.5 py-1 text-[11px] font-semibold ${entryTone(entry)}`}>
                {entryKindLabel(entry)}
              </span>
              {entry.kind !== 'HOLIDAY' && (
                <span className="text-[14px] font-semibold text-ink-hi">{entry.personName}</span>
              )}
              {entry.kind !== 'HOLIDAY' && entry.mine && (
                <span className="text-[11px] font-semibold text-accent-light">나</span>
              )}
              <span className="text-[12px] text-ink-mute">{entry.label}</span>
            </div>

            {entry.detail && (
              <p className="mt-2 whitespace-pre-wrap break-words text-[13px] leading-5 text-ink-body">
                {entry.detail}
              </p>
            )}
          </article>
        ))}
      </div>
    </Modal>
  );
}

function MobileCalendarDaySummary({ date, cell, onAdd }) {
  const dateObject = dayjs(date);
  const entries = cell?.entries ?? [];
  const weekday = WEEKDAYS[dateObject.day()];

  return (
    <section
      data-testid="mobile-calendar-day-summary"
      className="mobile-calendar-day-summary"
      aria-label={`${date} 선택한 날짜 상세`}
    >
      <div className="mobile-calendar-day-summary-header">
        <div>
          <div className="flex items-baseline gap-2">
            <strong className="text-[30px] font-bold leading-none tracking-[-0.05em] text-ink-hi">
              {dateObject.date()}
            </strong>
            <span className="text-[14px] font-semibold text-ink-body">{weekday}요일</span>
          </div>
          <p className="mt-1 text-[12px] text-ink-mute">
            {dateObject.format('YYYY년 M월')}
          </p>
        </div>
        <button
          type="button"
          onClick={onAdd}
          className="rounded-badge bg-accent-cyan/12 px-3 py-2 text-[12px] font-semibold text-accent-cyan ring-1 ring-inset ring-accent-cyan/30"
        >
          + 추가
        </button>
      </div>

      <div className="mobile-calendar-day-summary-list">
        {entries.length > 0 ? (
          entries.map((entry) => (
            <article key={entry.key} className="mobile-calendar-day-summary-item">
              <span
                aria-hidden="true"
                className={`mobile-calendar-summary-dot ${entryDotTone(entry)}`}
              />
              <div className="min-w-0 flex-1">
                <div className="flex min-w-0 items-center gap-2">
                  <strong className="truncate text-[13px] font-semibold text-ink-hi">
                    {entry.label}
                  </strong>
                  {entry.kind !== 'HOLIDAY' && (
                    <span className="truncate text-[12px] text-ink-mute">
                      {entry.personName}
                      {entry.mine ? ' · 나' : ''}
                    </span>
                  )}
                </div>
                {entry.mine && entry.detail && (
                  <p className="mt-0.5 truncate text-[11px] text-ink-mute">{entry.detail}</p>
                )}
              </div>
            </article>
          ))
        ) : (
          <p className="mobile-calendar-day-summary-empty">등록된 일정이 없습니다.</p>
        )}
      </div>

      <button
        type="button"
        onClick={onAdd}
        className="mobile-calendar-day-summary-add"
      >
        <span>{dateObject.format('M월 D일')}에 추가</span>
        <span aria-hidden="true" className="text-[28px] font-light leading-none">
          +
        </span>
      </button>
    </section>
  );
}

// 캘린더 — 회사 전체 연차·개인 일정·공휴일을 큰 월간 그리드로 조회 (docs/05 §②·§2-5b)
// 날짜 셀을 클릭하면 드래그 가능한 등록 패널이 떠서, 캘린더를 보면서 날짜를 담아 등록한다.
export default function CalendarPage() {
  const { data: me } = useCurrentUser();
  const summaryQuery = useLeaveSummary();
  const isMobile = useMediaQuery('(max-width: 639px)');

  // 조회 기준 연·월 (초기값 = 오늘)
  const [year, setYear] = useState(dayjs().year());
  const [month, setMonth] = useState(dayjs().month() + 1);

  // 검색 — 이름은 디바운스해서 서버로 보낸다 (입력값과 질의값을 분리)
  const [nameInput, setNameInput] = useState('');
  const [nameQuery, setNameQuery] = useState('');
  const [departmentId, setDepartmentId] = useState('');
  const [mobileFiltersOpen, setMobileFiltersOpen] = useState(false);
  const [mobileFocusedDate, setMobileFocusedDate] = useState(() => dayjs().format('YYYY-MM-DD'));
  const touchStartRef = useRef(null);

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
  // 선택한 날짜가 보고 있는 해를 벗어나면(다음 회차 예약이 그렇다) 그 해 공휴일도 함께 받아야
  // 패널이 공휴일을 판정할 수 있다. 신청 범위가 다음 1회차까지라 해는 최대 두 개다.
  const selectedOtherYear = useMemo(() => {
    const other = selectedDates
      .map((d) => Number(d.slice(0, 4)))
      .find((y) => Number.isFinite(y) && y !== year);
    return other ?? null;
  }, [selectedDates, year]);
  const otherYearHolidaysQuery = useHolidays(selectedOtherYear);
  const holidayDates = useMemo(
    () => [
      ...holidays.map((holiday) => holiday.date),
      ...(otherYearHolidaysQuery.data ?? []).map((holiday) => holiday.date),
    ],
    [holidays, otherYearHolidaysQuery.data],
  );
  const [mobileDateSelectionMode, setMobileDateSelectionMode] = useState(false);
  // 날짜 선택 모드에 들어가기 전 선택 상태 — 취소하면 여기로 되돌린다.
  const dateSelectionSnapshotRef = useRef([]);
  const [dayDetail, setDayDetail] = useState(null);

  // 월 그리드(주 단위 셀 배열)와 날짜별 데이터 계산
  const { weeks, calData, calendarSpans, multiDayEventKeys } = useMemo(() => {
    const base = dayjs(`${year}-${String(month).padStart(2, '0')}-01`);
    const daysInMonth = base.daysInMonth();
    const startOffset = base.day();
    const totalCells = Math.ceil((startOffset + daysInMonth) / 7) * 7;
    const cells = [];

    for (let i = 0; i < totalCells; i += 1) {
      const dayNum = i - startOffset + 1;
      cells.push(dayNum >= 1 && dayNum <= daysInMonth ? dayNum : null);
    }

    const rows = [];
    for (let i = 0; i < cells.length; i += 7) {
      rows.push(cells.slice(i, i + 7));
    }

    // 서버가 null로 마스킹한 사유·메모는 그대로 보존한다. 화면이 대신 값을 만들지 않는다.
    const spanRecords = [
      ...(calendarQuery.data ?? []).map((leave) => ({
        id: ['L', leave.id].join(''),
        dates: leave.dates ?? [],
        entry: {
          personName: leave.userName,
          label: LEAVE_TYPE_LABEL[leave.leaveType] ?? leave.leaveType,
          kind: 'LEAVE',
          typeKey: leave.leaveType,
          mine: leave.userId === me?.id,
          detail: leave.reason,
        },
      })),
      ...(scheduleQuery.data ?? []).map((schedule) => ({
        id: ['S', schedule.id].join(''),
        dates: schedule.dates ?? [],
        entry: {
          personName: schedule.userName,
          label:
            schedule.typeLabel ??
            SCHEDULE_TYPE_LABEL[schedule.scheduleType] ??
            schedule.scheduleType,
          kind: 'SCHEDULE',
          typeKey: schedule.scheduleType,
          mine: schedule.userId === me?.id,
          detail: schedule.memo,
        },
      })),
      // 연속 공휴일(추석 등)도 하나의 막대로 — 하루짜리는 buildCalendarSpanData가 걸러 pill로 남긴다.
      ...groupHolidayRuns(holidays).map((run) => ({
        id: `H${run.dates[0]}`,
        dates: run.dates,
        entry: {
          personName: '',
          label: run.name,
          kind: 'HOLIDAY',
          typeKey: 'HOLIDAY',
          mine: false,
          detail: null,
        },
      })),
    ];

    const flatLeaves = (calendarQuery.data ?? []).flatMap((leave) =>
      leave.dates.map((date) => ({
        date,
        eventId: ['L', leave.id].join(''),
        eventDateKey: ['L', leave.id].join('') + ':' + date,
        key: `L${leave.id}-${date}`,
        personName: leave.userName,
        label: LEAVE_TYPE_LABEL[leave.leaveType] ?? leave.leaveType,
        kind: 'LEAVE',
        typeKey: leave.leaveType,
        mine: leave.userId === me?.id,
        detail: leave.reason,
      })),
    );

    const flatSchedules = (scheduleQuery.data ?? []).flatMap((schedule) =>
      schedule.dates.map((date) => ({
        date,
        eventId: ['S', schedule.id].join(''),
        eventDateKey: ['S', schedule.id].join('') + ':' + date,
        key: `S${schedule.id}-${date}`,
        personName: schedule.userName,
        label:
          schedule.typeLabel ??
          SCHEDULE_TYPE_LABEL[schedule.scheduleType] ??
          schedule.scheduleType,
        kind: 'SCHEDULE',
        typeKey: schedule.scheduleType,
        mine: schedule.userId === me?.id,
        detail: schedule.memo,
      })),
    );

    // 공휴일 — 연차로 신청할 수 없는 날짜다. 개인 일정은 등록할 수 있으므로 셀 클릭을 막지 않고
    // 패널이 모드에 따라 판단하게 넘긴다. **주말은 여기서 세지 않는다** — 패널이 날짜에서 직접
    // 판정한다. 이 목록이 보이는 달에 묶여 있어, 달을 넘기면 앞서 고른 주말이 빠지던 결함 때문이다.
    const spanData = buildCalendarSpanData(spanRecords, rows, year, month);

    return {
      weeks: rows,
      calData: buildCalendarData([...flatLeaves, ...flatSchedules], holidays, year, month),
      calendarSpans: spanData.segmentsByWeek,
      multiDayEventKeys: spanData.multiDayEventKeys,
    };
  }, [year, month, calendarQuery.data, scheduleQuery.data, holidays, me?.id]);

  // 오늘 강조는 조회 중인 달이 실제 '오늘'의 달과 같을 때만 적용
  const today = dayjs();
  const isTodayMonth = year === today.year() && month === today.month() + 1;
  const todayDate = today.date();
  const currentMonthPrefix = `${year}-${String(month).padStart(2, '0')}-`;
  const mobileFocusedDateForView = mobileFocusedDate.startsWith(currentMonthPrefix)
    ? mobileFocusedDate
    : `${currentMonthPrefix}01`;
  const mobileFocusedDay = Number(mobileFocusedDateForView.slice(8, 10));
  const mobileFocusedCell = calData[mobileFocusedDay];

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

  function goToToday() {
    const now = dayjs();
    setYear(now.year());
    setMonth(now.month() + 1);
    setMobileFocusedDate(now.format('YYYY-MM-DD'));
  }

  // 입력 중인 요소인지 — 신청 사유 등을 쓰다가 방향키로 달이 넘어가면 안 된다
  function isEditableTarget(target) {
    if (!target) return false;
    const tagName = target.tagName;
    if (tagName === 'INPUT' || tagName === 'TEXTAREA' || tagName === 'SELECT') return true;
    return Boolean(target.isContentEditable);
  }

  // 캘린더 컨테이너 전용 키보드 월 이동. document 전역 리스너로 만들지 않는다 — 다른 화면으로 새는 것을 막기 위해서다.
  function handleCalendarKeyDown(event) {
    if (event.ctrlKey || event.altKey || event.metaKey) return;
    if (panelOpen || dayDetail) return;
    if (isEditableTarget(event.target)) return;

    switch (event.key) {
      case 'ArrowLeft':
      case 'PageUp':
        event.preventDefault();
        shiftMonth(-1);
        break;
      case 'ArrowRight':
      case 'PageDown':
        event.preventDefault();
        shiftMonth(1);
        break;
      case 'Home':
        event.preventDefault();
        goToToday();
        break;
      default:
        break;
    }
  }

  function handleCalendarTouchStart(event) {
    const touch = event.touches[0];
    if (!touch) return;
    touchStartRef.current = { x: touch.clientX, y: touch.clientY };
  }

  function handleCalendarTouchEnd(event) {
    const start = touchStartRef.current;
    touchStartRef.current = null;
    const touch = event.changedTouches[0];
    if (!start || !touch) return;

    const deltaX = touch.clientX - start.x;
    const deltaY = touch.clientY - start.y;
    const horizontalDistance = Math.abs(deltaX);
    const verticalDistance = Math.abs(deltaY);
    if (horizontalDistance <= verticalDistance || horizontalDistance < 12) return;
    event.preventDefault();
    if (horizontalDistance < 50) return;
    shiftMonth(deltaX < 0 ? 1 : -1);
  }

  // 날짜 셀 본문은 계속 등록 전용이다. 상세는 초과 손잡이에서만 열어 두 동작이 충돌하지 않게 한다.
  function toggleSelectedDate(dateStr) {
    setSelectedDates((previous) =>
      previous.includes(dateStr)
        ? previous.filter((date) => date !== dateStr)
        : [...previous, dateStr].sort(),
    );
  }

  function handleDayClick(dateStr) {
    if (isMobile) {
      setMobileFocusedDate(dateStr);

      if (mobileDateSelectionMode) {
        toggleSelectedDate(dateStr);
        return;
      }

      setSelectedDates([]);
      setPanelOpen(false);
      return;
    }

    toggleSelectedDate(dateStr);
    setPanelOpen(true);
  }

  function openMobileEntry() {
    setSelectedDates([mobileFocusedDateForView]);
    setMobileDateSelectionMode(false);
    setPanelOpen(true);
  }

  function openMobileDateSelection() {
    if (!isMobile) return;
    dateSelectionSnapshotRef.current = selectedDates;
    setSelectedDates((previous) =>
      previous.length > 0 ? previous : [mobileFocusedDateForView],
    );
    setPanelOpen(false);
    setMobileDateSelectionMode(true);
  }

  function completeMobileDateSelection() {
    if (selectedDates.length === 0) return;
    setMobileDateSelectionMode(false);
    setPanelOpen(true);
  }

  // 취소 — 날짜 선택 모드에 들어오면서 고르거나 뺀 것을 전부 무르고 신청 화면으로 돌아간다.
  function cancelMobileDateSelection() {
    setSelectedDates(dateSelectionSnapshotRef.current);
    setMobileDateSelectionMode(false);
    setPanelOpen(true);
  }

  function showDayDetail(date, cell) {
    setDayDetail({
      date,
      entries: cell?.entries ?? [],
    });
  }

  function openDayDetail(event, date, cell) {
    event.stopPropagation();
    showDayDetail(date, cell);
  }

  function closePanel() {
    setPanelOpen(false);
    setSelectedDates([]);
    setMobileDateSelectionMode(false);
  }

  function clearSearch() {
    setNameInput('');
    setNameQuery('');
    setDepartmentId('');
  }

  const departments = departmentsQuery.data ?? [];

  return (
    <div className="flex h-full flex-col">
      <div className="mb-4 flex items-center justify-between px-1 sm:hidden">
        <span
          aria-live="polite"
          aria-label={`${year}년 ${month}월`}
          className="text-[28px] font-bold tracking-[-0.04em] text-ink-hi"
        >
          {month}월
        </span>
        <div className="flex items-center gap-1">
          <IconButton
            Icon={Search}
            label="일정 검색"
            size="md"
            tone="muted"
            onClick={() => setMobileFiltersOpen((previous) => !previous)}
          />
          <IconButton
            Icon={CalendarDays}
            label="오늘로 이동"
            size="md"
            tone="muted"
            onClick={goToToday}
          />
        </div>
      </div>

      <PageHeader
        className="hidden sm:block"
        title="캘린더"
        subtitle="회사 전체 연차·일정과 공휴일 · 날짜를 클릭하면 바로 등록"
      />

      {/* 월 이동은 캘린더의 주 탐색이다. 헤더 보조 영역에서 분리해 위치·크기·대비를 함께 높인다. */}
      <div className="mb-4 hidden items-center justify-center gap-3 rounded-card border border-white/[0.12] bg-navy-card px-4 py-3 shadow-card sm:flex">
        <IconButton
          Icon={ChevronLeft}
          label="이전 달"
          size="lg"
          tone="accent"
          onClick={() => shiftMonth(-1)}
        />
        <span
          aria-live="polite"
          className="min-w-[132px] text-center text-[17px] font-bold text-ink-hi tabular-nums"
        >
          {year}년 {month}월
        </span>
        <IconButton
          Icon={ChevronRight}
          label="다음 달"
          size="lg"
          tone="accent"
          onClick={() => shiftMonth(1)}
        />
      </div>

      <div
        className={[
          'mb-3 flex-wrap items-center gap-2 sm:flex',
          mobileFiltersOpen ? 'flex' : 'hidden',
        ].join(' ')}
      >
        <div className="relative min-w-[220px] flex-1 sm:max-w-[300px]">
          <Search
            size={15}
            className="pointer-events-none absolute left-3 top-1/2 -translate-y-1/2 text-ink-faint"
          />
          <TextInput
            value={nameInput}
            onChange={(event) => setNameInput(event.target.value)}
            placeholder="이름으로 검색 (예: 박민수)"
            aria-label="이름으로 일정 검색"
            className="pl-9"
          />
        </div>

        <div className="min-w-[160px]">
          <Select
            value={departmentId}
            onChange={(event) => setDepartmentId(event.target.value)}
            aria-label="부서로 일정 필터"
          >
            <option value="">전체 부서</option>
            {departments.map((department) => (
              <option key={department.id} value={department.id}>
                {department.name}
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

      {isMobile && mobileDateSelectionMode && (
        <div
          data-testid="mobile-date-selection-toolbar"
          className="mobile-date-selection-toolbar"
          aria-label="모바일 날짜 선택"
        >
          <div className="mobile-date-selection-toolbar-header">
            <div className="min-w-0">
              <p className="text-[13px] font-semibold text-ink-hi">날짜 선택</p>
              <p aria-live="polite" className="mt-0.5 text-[11px] text-ink-mute">
                {selectedDates.length}일 선택됨 · 날짜를 눌러 추가하거나 해제하세요
              </p>
            </div>
            <div className="flex shrink-0 items-center gap-2">
              <button
                type="button"
                onClick={cancelMobileDateSelection}
                className="rounded-badge bg-navy-app/60 px-3 py-2 text-[12px] font-semibold text-ink-mute ring-1 ring-inset ring-white/[0.14] transition-colors hover:text-ink-body"
              >
                취소
              </button>
              <button
                type="button"
                onClick={completeMobileDateSelection}
                disabled={selectedDates.length === 0}
                className="rounded-badge bg-accent-cyan px-3 py-2 text-[12px] font-bold text-navy-app transition-opacity disabled:cursor-not-allowed disabled:opacity-45"
              >
                적용
              </button>
            </div>
          </div>

          {selectedDates.length > 0 && (
            <div className="mobile-date-selection-chips" aria-label="선택한 날짜 목록">
              {selectedDates.map((date) => (
                <span key={date} className="mobile-date-selection-chip">
                  {dayjs(date).format('M/D')} ({WEEKDAYS[dayjs(date).day()]})
                  <button
                    type="button"
                    onClick={() => toggleSelectedDate(date)}
                    aria-label={`${date} 제거`}
                    className="mobile-date-selection-chip-remove"
                  >
                    <X size={11} />
                  </button>
                </span>
              ))}
            </div>
          )}
        </div>
      )}

      <Card fill padding="tight" className="mobile-calendar-card">
        <div className="mobile-calendar-legend mb-3 flex flex-wrap items-center gap-x-4 gap-y-2 text-[12px] text-ink-mute">
          <Legend swatch="bg-accent/25 text-accent-light" label="내 연차" />
          <Legend swatch="bg-ok/18" label="동료 연차" />
          <Legend swatch="bg-amber-400/18" label="외근" />
          <Legend swatch="bg-violet-400/18" label="출장" />
          <Legend swatch="bg-emerald-400/16" label="재택" />
          <Legend swatch="bg-sky-400/18" label="교육" />
          <Legend swatch="bg-danger/18" label="공휴일" />
          <Legend
            swatch="border-2 border-accent-cyan bg-accent-cyan/12"
            label="등록 선택"
            Icon={Check}
          />
        </div>

        <div className="calendar-weekday-row grid grid-cols-7 gap-2 border-b border-white/[0.12] pb-2">
          {WEEKDAYS.map((weekday, index) => (
            <div
              key={weekday}
              className={`text-center text-[13px] font-semibold ${
                index === 0 ? 'text-danger/80' : 'text-ink-faint'
              }`}
            >
              {weekday}
            </div>
          ))}
        </div>

        <div
          data-testid="calendar-touch-surface"
          className="calendar-touch-surface relative mt-2 min-h-0 flex-1 rounded-btn outline-none focus-visible:ring-2 focus-visible:ring-accent-cyan/30"
          role="group"
          aria-label="월간 캘린더. 방향키 또는 Page Up/Down 키로 월을 이동하고, Home 키로 오늘이 있는 달로 이동합니다."
          tabIndex={0}
          onTouchStart={handleCalendarTouchStart}
          onTouchEnd={handleCalendarTouchEnd}
          onTouchCancel={() => {
            touchStartRef.current = null;
          }}
          onKeyDown={handleCalendarKeyDown}
        >
          <div className="mobile-calendar-grid grid h-full min-h-0 auto-rows-fr grid-cols-7 gap-2">
          {weeks.flat().map((day, index) => {
            if (day === null) {
              const adjacentDate = getCalendarCellDate(year, month, index);
              return (
                <div
                  key={`pad-${index}`}
                  className="calendar-pad-cell rounded-btn border border-transparent bg-navy-app/20"
                >
                  <span className="mobile-calendar-adjacent-day">{adjacentDate.date()}</span>
                </div>
              );
            }

            const cell = calData[day];
            const isSunday = index % 7 === 0;
            const isToday = isTodayMonth && day === todayDate;
            const entries = cell?.entries ?? [];
            const holiday = entries.find((entry) => entry.kind === 'HOLIDAY')?.label;
            const weekIndex = Math.floor(index / 7);
            const weekSpans = calendarSpans[weekIndex] ?? [];
            const columnIndex = index % 7;
            const spanLaneCount = weekSpans.reduce(
              (maxLane, span) =>
                span.startColumn <= columnIndex && columnIndex <= span.endColumn
                  ? Math.max(maxLane, span.lane + 1)
                  : maxLane,
              0,
            );
            const visibleEntries = entries.filter(
              (entry) => !multiDayEventKeys.has(entry.eventDateKey),
            );
            const overflow = Math.max(entries.length - MAX_VISIBLE, 0);
            const dateStr = `${year}-${String(month).padStart(2, '0')}-${String(day).padStart(2, '0')}`;
            const selected = selectedDates.includes(dateStr);
            const mobileFocused = isMobile && mobileFocusedDateForView === dateStr;

            return (
              <button
                key={dateStr}
                type="button"
                onClick={() => handleDayClick(dateStr)}
                aria-label={`${dateStr} 등록 날짜 선택`}
                aria-pressed={isMobile && mobileDateSelectionMode ? selected : undefined}
                className={`calendar-day-cell relative flex flex-col gap-1.5 overflow-hidden rounded-btn border p-2 text-left transition-all duration-150 ${
                  overflow > 0 ? 'calendar-day-cell-has-overflow' : ''
                } ${mobileFocused ? 'mobile-calendar-focused' : ''} ${
                  selected
                    ? 'border-accent-cyan/60 bg-accent-cyan/10 hover:border-accent-cyan'
                    : holiday
                      ? 'border-danger/25 bg-danger/8 hover:border-danger/40'
                      : 'border-white/[0.10] bg-navy-app/35 hover:border-accent-cyan/35 hover:bg-navy-app/60'
                }`}
              >
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
                  {selected && <Check size={14} className="shrink-0 text-accent-light" />}
                </div>

                <div
                  className="flex min-h-0 flex-col gap-1 overflow-hidden"
                  style={
                    spanLaneCount > 0
                      ? { paddingTop: String(spanLaneCount * SPAN_LANE_PITCH) + 'px' }
                      : undefined
                  }
                >
                  {visibleEntries.slice(0, MAX_VISIBLE).map((entry) => (
                    <span
                      key={entry.key}
                      className={`calendar-entry-pill flex items-center gap-1 rounded px-1.5 py-1 text-[12px] font-medium ${entryTone(entry)} ${
                        entry.mine ? 'ring-1 ring-inset ring-white/25' : ''
                      }`}
                    >
                      <span className="truncate">{entry.personName}</span>
                      <span className="shrink-0 opacity-75">{entry.label}</span>
                    </span>
                  ))}

                </div>
                <div
                  className="mobile-calendar-entry-dots"
                  aria-label={visibleEntries.map((entry) => entry.label).join(', ')}
                >
                  {visibleEntries.slice(0, 4).map((entry) => (
                    <span
                      key={`dot-${entry.key}`}
                      title={entry.kind === 'HOLIDAY' ? entry.label : `${entry.personName} · ${entry.label}`}
                      className={`mobile-calendar-entry-dot ${entryDotTone(entry)}`}
                    />
                  ))}
                </div>
                {!isMobile && overflow > 0 && (
                  <span
                    role="button"
                    tabIndex={0}
                    aria-label={`${dateStr} 일정 ${entries.length}개 모두 보기`}
                    onClick={(event) => openDayDetail(event, dateStr, cell)}
                    onKeyDown={(event) => {
                      if (event.key === 'Enter' || event.key === ' ') {
                        event.preventDefault();
                        openDayDetail(event, dateStr, cell);
                      }
                    }}
                    className="calendar-overflow-trigger flex items-center gap-1 rounded-badge px-1.5 py-0.5 text-[12px] font-semibold text-accent-light transition-colors hover:bg-accent/15 hover:text-ink-hi"
                  >
                    <CalendarDays size={12} />
                    ...더보기
                  </span>
                )}
              </button>
            );
          })}
          </div>

          <div
            aria-label="연속 일정"
            className="calendar-span-overlay pointer-events-none absolute inset-0 z-10 grid grid-cols-7 gap-2"
            style={{
              gridTemplateRows: 'repeat(' + weeks.length + ', minmax(0, 1fr))',
            }}
          >
            {calendarSpans.flatMap((spans, weekIndex) =>
              spans.map((segment) => {
                const entry = segment.entry;
                const label = entry.personName
                  ? entry.personName + ' · ' + entry.label
                  : entry.label;
                const range = segment.rangeStart + ' ~ ' + segment.rangeEnd;
                // 막대는 셀 안 pill과 같은 안쪽 여백을 두되, 주가 바뀌며 이어지는 끝은 벽에 붙여 계속됨을 보인다.
                const edges = [
                  segment.continuesBefore ? 'calendar-span-bar-continues-before' : 'rounded-l',
                  segment.continuesAfter ? 'calendar-span-bar-continues-after' : 'rounded-r',
                ].join(' ');

                return (
                  <span
                    key={segment.id}
                    role="img"
                    aria-label={label + ' ' + range}
                    title={label + ' (' + range + ')'}
                    style={{
                      gridColumn:
                        String(segment.startColumn + 1) +
                        ' / ' +
                        String(segment.endColumn + 2),
                      gridRow: weekIndex + 1,
                      '--calendar-span-top': String(SPAN_TOP_OFFSET + segment.lane * SPAN_LANE_PITCH) + 'px',
                      '--calendar-span-lane': String(segment.lane),
                      height: String(SPAN_BAR_HEIGHT) + 'px',
                    }}
                    className={[
                      'calendar-span-bar flex min-w-0 items-center gap-1 overflow-hidden px-1.5 text-[12px] font-medium',
                      entryTone(entry),
                      edges,
                      entry.mine ? 'ring-1 ring-inset ring-white/25' : '',
                    ]
                      .filter(Boolean)
                      .join(' ')}
                  >
                    {segment.continuesBefore && (
                      <span aria-hidden="true" className="mr-1 shrink-0 opacity-75">
                        ↔
                      </span>
                    )}
                    <span className="truncate">{label}</span>
                  </span>
                );
              }),
            )}
          </div>
        </div>

        {isFiltered && Object.keys(calData).length === 0 && (
          <p className="mt-3 rounded-btn bg-navy-app/50 px-3 py-2.5 text-center text-[13px] text-ink-mute">
            {nameQuery ? `'${nameQuery}'` : '선택한 부서'}의 {month}월 일정이 없습니다.
          </p>
        )}
      </Card>

      {isMobile && !mobileDateSelectionMode && (
        <MobileCalendarDaySummary
          date={mobileFocusedDateForView}
          cell={mobileFocusedCell}
          onAdd={openMobileEntry}
        />
      )}

      {panelOpen && (
        <CalendarEntryPanel
          dates={selectedDates}
          holidayDates={holidayDates}
          remainingDays={summaryQuery.data?.remainingDays ?? 0}
          nextResetDate={summaryQuery.data?.nextResetDate ?? null}
          nextCycleReservedDays={summaryQuery.data?.nextCycleReservedDays ?? 0}
          nextCycleAllowanceDays={summaryQuery.data?.nextCycleAllowanceDays ?? 0}
          nextCycleReservationEnabled={summaryQuery.data?.nextCycleReservationEnabled ?? true}
          onRemoveDate={(date) =>
            setSelectedDates((previous) => previous.filter((item) => item !== date))
          }
          onClose={closePanel}
          onSubmitted={() => {
            setSelectedDates([]);
            setMobileDateSelectionMode(false);
          }}
          onOpenDatePicker={isMobile ? openMobileDateSelection : undefined}
        />
      )}

      {dayDetail && (
        <CalendarDayDetail detail={dayDetail} onClose={() => setDayDetail(null)} />
      )}
    </div>
  );
}
