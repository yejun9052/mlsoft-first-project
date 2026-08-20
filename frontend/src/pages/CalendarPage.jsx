import { useEffect, useMemo, useState } from 'react';
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
import { useLeaveCalendar, useLeaveSummary } from '../hooks/useLeaves.js';
import { useScheduleCalendar } from '../hooks/useSchedules.js';
import { useDepartments } from '../hooks/useDepartments.js';
import { useHolidays } from '../hooks/useHolidays.js';

// 요일 헤더 (일요일 시작)
const WEEKDAYS = ['일', '월', '화', '수', '목', '금', '토'];
// 6주짜리 달에서도 행 높이가 흔들리지 않으면서 이름과 종류를 읽을 수 있는 상한이다.
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

function entryTone(entry) {
  if (entry.kind === 'SCHEDULE') {
    return SCHEDULE_PILL_CLASS[entry.typeKey] ?? 'bg-white/10 text-ink-body';
  }
  return entry.mine ? 'bg-accent/25 text-accent-light' : 'bg-ok/15 text-ok';
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
    (map[day] ??= { entries: [], holiday: null }).entries.push(entry);
  }
  for (const holiday of holidays) {
    if (!holiday.date.startsWith(prefix)) continue;
    const day = Number(holiday.date.slice(8, 10));
    (map[day] ??= { entries: [], holiday: null }).holiday = holiday.name;
  }
  return map;
}

function CalendarDayDetail({ detail, onClose }) {
  const formattedDate = dayjs(detail.date).format('YYYY년 M월 D일 (ddd)');

  return (
    <Modal title={`${formattedDate} 일정`} onClose={onClose} maxWidth={560}>
      <div className="space-y-3">
        {detail.holiday && (
          <div className="rounded-btn border border-danger/25 bg-danger/8 px-4 py-3">
            <div className="flex items-center gap-2">
              <span className="rounded-badge bg-danger/18 px-2.5 py-1 text-[11px] font-semibold text-danger">
                공휴일
              </span>
              <span className="text-[14px] font-semibold text-ink-hi">{detail.holiday}</span>
            </div>
          </div>
        )}

        {detail.entries.map((entry) => (
          <article
            key={entry.key}
            className="rounded-btn border border-white/[0.12] bg-navy-app/45 px-4 py-3"
          >
            <div className="flex flex-wrap items-center gap-2">
              <span className={`rounded-badge px-2.5 py-1 text-[11px] font-semibold ${entryTone(entry)}`}>
                {entry.kind === 'LEAVE' ? '연차' : '개인 일정'}
              </span>
              <span className="text-[14px] font-semibold text-ink-hi">{entry.personName}</span>
              {entry.mine && (
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

// 캘린더 — 회사 전체 연차·개인 일정·공휴일을 큰 월간 그리드로 조회 (docs/05 §②·§2-5b)
// 날짜 셀을 클릭하면 드래그 가능한 등록 패널이 떠서, 캘린더를 보면서 날짜를 담아 등록한다.
export default function CalendarPage() {
  const { data: me } = useCurrentUser();
  const summaryQuery = useLeaveSummary();

  // 조회 기준 연·월 (초기값 = 오늘)
  const [year, setYear] = useState(dayjs().year());
  const [month, setMonth] = useState(dayjs().month() + 1);

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
  const [dayDetail, setDayDetail] = useState(null);

  // 월 그리드(주 단위 셀 배열)와 날짜별 데이터 계산
  const { weeks, calData, blockedDates } = useMemo(() => {
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
    const flatLeaves = (calendarQuery.data ?? []).flatMap((leave) =>
      leave.dates.map((date) => ({
        date,
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

    // 주말·공휴일 — 연차로 신청할 수 없는 날짜. 개인 일정은 등록할 수 있으므로
    // 셀 클릭을 막지 않고 패널이 모드에 따라 판단하게 넘긴다.
    const blocked = [];
    for (let day = 1; day <= daysInMonth; day += 1) {
      const dateStr = `${year}-${String(month).padStart(2, '0')}-${String(day).padStart(2, '0')}`;
      const weekday = dayjs(dateStr).day();
      if (weekday === 0 || weekday === 6 || holidays.some((holiday) => holiday.date === dateStr)) {
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

  // 날짜 셀 본문은 계속 등록 전용이다. 상세는 초과 손잡이에서만 열어 두 동작이 충돌하지 않게 한다.
  function handleDayClick(dateStr) {
    setSelectedDates((previous) =>
      previous.includes(dateStr)
        ? previous.filter((date) => date !== dateStr)
        : [...previous, dateStr].sort(),
    );
    setPanelOpen(true);
  }

  function openDayDetail(event, date, cell) {
    event.stopPropagation();
    setDayDetail({
      date,
      holiday: cell?.holiday ?? null,
      entries: cell?.entries ?? [],
    });
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
        title="캘린더"
        subtitle="회사 전체 연차·일정과 공휴일 · 날짜를 클릭하면 바로 등록"
      />

      {/* 월 이동은 캘린더의 주 탐색이다. 헤더 보조 영역에서 분리해 위치·크기·대비를 함께 높인다. */}
      <div className="mb-4 flex items-center justify-center gap-3 rounded-card border border-white/[0.12] bg-navy-card px-4 py-3 shadow-card">
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

      <div className="mb-3 flex flex-wrap items-center gap-2">
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

      <Card fill padding="tight">
        <div className="mb-3 flex flex-wrap items-center gap-x-4 gap-y-2 text-[12px] text-ink-mute">
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

        <div className="grid grid-cols-7 gap-2 border-b border-white/[0.12] pb-2">
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

        <div className="mt-2 grid min-h-0 flex-1 auto-rows-fr grid-cols-7 gap-2">
          {weeks.flat().map((day, index) => {
            if (day === null) {
              return (
                <div
                  key={`pad-${index}`}
                  className="rounded-btn border border-transparent bg-navy-app/20"
                />
              );
            }

            const cell = calData[day];
            const isSunday = index % 7 === 0;
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
                aria-label={`${dateStr} 등록 날짜 선택`}
                className={`flex flex-col gap-1.5 overflow-hidden rounded-btn border p-2 text-left transition-all duration-150 ${
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
                  {holiday && (
                    <span className="truncate text-[12px] font-medium text-danger/85">
                      {holiday}
                    </span>
                  )}
                  {selected && <Check size={14} className="shrink-0 text-accent-light" />}
                </div>

                <div className="flex min-h-0 flex-col gap-1 overflow-hidden">
                  {entries.slice(0, MAX_VISIBLE).map((entry) => (
                    <span
                      key={entry.key}
                      className={`flex items-center gap-1 rounded px-1.5 py-1 text-[12px] font-medium ${entryTone(entry)} ${
                        entry.mine ? 'ring-1 ring-inset ring-white/25' : ''
                      }`}
                    >
                      <span className="truncate">{entry.personName}</span>
                      <span className="shrink-0 opacity-75">{entry.label}</span>
                    </span>
                  ))}

                  {overflow > 0 && (
                    <span
                      role="button"
                      tabIndex={0}
                      aria-label={`${dateStr} 일정 ${entries.length + (holiday ? 1 : 0)}개 모두 보기`}
                      onClick={(event) => openDayDetail(event, dateStr, cell)}
                      onKeyDown={(event) => {
                        if (event.key === 'Enter' || event.key === ' ') {
                          event.preventDefault();
                          openDayDetail(event, dateStr, cell);
                        }
                      }}
                      className="flex items-center gap-1 rounded-badge px-1.5 py-0.5 text-[12px] font-semibold text-accent-light transition-colors hover:bg-accent/15 hover:text-ink-hi"
                    >
                      <CalendarDays size={12} />
                      +{overflow}개
                    </span>
                  )}
                </div>
              </button>
            );
          })}
        </div>

        {isFiltered && Object.keys(calData).length === 0 && (
          <p className="mt-3 rounded-btn bg-navy-app/50 px-3 py-2.5 text-center text-[13px] text-ink-mute">
            {nameQuery ? `'${nameQuery}'` : '선택한 부서'}의 {month}월 일정이 없습니다.
          </p>
        )}
      </Card>

      {panelOpen && (
        <CalendarEntryPanel
          dates={selectedDates}
          blockedDates={blockedDates}
          remainingDays={summaryQuery.data?.remainingDays ?? 0}
          onRemoveDate={(date) =>
            setSelectedDates((previous) => previous.filter((item) => item !== date))
          }
          onClose={closePanel}
          onSubmitted={() => setSelectedDates([])}
        />
      )}

      {dayDetail && (
        <CalendarDayDetail detail={dayDetail} onClose={() => setDayDetail(null)} />
      )}
    </div>
  );
}
