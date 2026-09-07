import { useEffect, useMemo, useRef, useState } from 'react';
import dayjs from 'dayjs';
import toast from 'react-hot-toast';
import { CalendarDays, GripVertical, X } from 'lucide-react';
import { LEAVE_TYPE_LABEL, SCHEDULE_TYPE_LABEL } from '../../constants/status.js';
import { useApplyLeave } from '../../hooks/useLeaves.js';
import { useCreateSchedule, useScheduleTypes } from '../../hooks/useSchedules.js';
import { useApprovers } from '../../hooks/useUsers.js';
import Field from '../ui/Field.jsx';
import SegmentedControl from '../ui/SegmentedControl.jsx';
import Textarea from '../ui/Textarea.jsx';
import Select from '../ui/Select.jsx';
import Button from '../ui/Button.jsx';
import IconButton from '../ui/IconButton.jsx';
import { NEXT_CYCLE_RESERVATION_LABEL } from '../../utils/leaveSummary.js';

// 연차 계열 — 잔액을 차감하고 결재를 거친다 (WELFARE는 복리후생 페이지에서 별도 신청)
const LEAVE_TYPES = ['ANNUAL', 'HALF_AM', 'HALF_PM'];
// 개인 일정 계열 폴백 — 서버(/api/schedules/types)가 내려주는 목록을 우선 쓴다
const FALLBACK_SCHEDULE_TYPES = ['FIELD_WORK', 'BUSINESS_TRIP', 'REMOTE', 'TRAINING'];

const MODE_OPTIONS = [
  { value: 'LEAVE', label: '연차' },
  { value: 'SCHEDULE', label: '개인 일정' },
];
const LEAVE_TYPE_OPTIONS = LEAVE_TYPES.map((t) => ({ value: t, label: LEAVE_TYPE_LABEL[t] }));
const WEEKDAY_KO = ['일', '월', '화', '수', '목', '금', '토'];

// 마지막에 고른 모드·종류를 기억해 다음에 패널을 열 때 그대로 시작한다.
// 재택근무를 매주 등록하는 사람이 매번 두 번씩 클릭하지 않게 하려는 것.
const LAST_USED_KEY = 'mlsoft.calendarEntry.lastUsed';

function readLastUsed() {
  try {
    const raw = localStorage.getItem(LAST_USED_KEY);
    if (!raw) return null;
    const parsed = JSON.parse(raw);
    return parsed && typeof parsed.mode === 'string' ? parsed : null;
  } catch {
    // 값이 깨져 있어도 패널은 떠야 한다 — 기본값으로 시작
    return null;
  }
}

/**
 * 캘린더 등록 패널 — 연차(신청)와 개인 일정(등록)을 한 자리에서 처리한다.
 * 캘린더 위에 떠서 헤더를 잡고 드래그로 옮길 수 있는 논모달이라, 패널을 열어둔 채
 * 캘린더 날짜를 계속 클릭해 담는 흐름을 유지한다.
 *
 * **두 모드는 성격이 다르다.**
 * - 연차: 잔액 차감 + 팀장 결재 → 사유 필수, 서브 승인자 선택, 차감 요약, 주말·공휴일 불가
 * - 개인 일정: 차감 없음 + 결재 없음 → 메모 선택, 등록 즉시 확정, 주말·공휴일 허용
 *   (주말 출장·지난주 외근을 뒤늦게 기록하는 것이 정상 사용이다)
 *
 * 그래서 모드를 고르면 폼이 짧아진다. 개인 일정은 날짜 → 종류 → 등록 세 동작으로 끝난다.
 * 날짜 선택 상태는 부모(캘린더)가 소유하고, 이 패널은 모드·종류·메모만 관리한다.
 */
export default function CalendarEntryPanel({
  dates,
  blockedDates,
  remainingDays,
  nextResetDate,
  nextCycleReservedDays = 0,
  nextCycleAllowanceDays = 0,
  nextCycleReservationEnabled = true,
  onRemoveDate,
  onClose,
  onSubmitted,
  onOpenDatePicker,
}) {
  const lastUsed = useMemo(readLastUsed, []);
  const [mode, setMode] = useState(lastUsed?.mode === 'SCHEDULE' ? 'SCHEDULE' : 'LEAVE');
  const [leaveType, setLeaveType] = useState(
    LEAVE_TYPES.includes(lastUsed?.leaveType) ? lastUsed.leaveType : 'ANNUAL',
  );
  const [scheduleType, setScheduleType] = useState(lastUsed?.scheduleType ?? 'FIELD_WORK');
  const [reason, setReason] = useState('');
  const [subApproverId, setSubApproverId] = useState('');

  const applyLeaveMutation = useApplyLeave();
  const createScheduleMutation = useCreateSchedule();
  const typesQuery = useScheduleTypes();
  // 서브 승인자 후보는 서버가 재직 중 TEAM_LEADER·SYSTEM_ADMIN에서 본인을 빼고 내려준다 (리뷰 F-4)
  const approversQuery = useApprovers();
  const approvers = approversQuery.data ?? [];

  // 서버가 종류를 내려주면 그걸 쓴다 — 백엔드에 종류를 추가하면 화면이 자동으로 따라온다
  const scheduleTypeOptions = useMemo(() => {
    if (typesQuery.data?.length) {
      return typesQuery.data.map((t) => ({ value: t.value, label: t.label }));
    }
    return FALLBACK_SCHEDULE_TYPES.map((t) => ({ value: t, label: SCHEDULE_TYPE_LABEL[t] }));
  }, [typesQuery.data]);

  const isLeave = mode === 'LEAVE';
  const pending = isLeave ? applyLeaveMutation.isPending : createScheduleMutation.isPending;

  // 패널 위치 — 처음엔 우측 상단, 이후 드래그 값 유지
  const [pos, setPos] = useState(() => ({
    x: Math.max(16, window.innerWidth - 452),
    y: 108,
  }));
  const dragOffset = useRef(null);

  // 창이 좁아져도(윈도우 스냅·모니터 변경) 패널이 화면 밖에 고립되지 않게 재클램프
  useEffect(() => {
    function clampToViewport() {
      setPos((p) => ({
        x: Math.min(Math.max(8, p.x), window.innerWidth - 120),
        y: Math.min(Math.max(8, p.y), window.innerHeight - 60),
      }));
    }
    window.addEventListener('resize', clampToViewport);
    return () => window.removeEventListener('resize', clampToViewport);
  }, []);

  // Esc로 닫기 — 논모달이라 포커스 트랩이 없어 키보드 닫기 수단을 따로 제공
  useEffect(() => {
    function onKeyDown(e) {
      if (e.key === 'Escape') onClose();
    }
    window.addEventListener('keydown', onKeyDown);
    return () => window.removeEventListener('keydown', onKeyDown);
  }, [onClose]);

  function onDragStart(e) {
    dragOffset.current = { dx: e.clientX - pos.x, dy: e.clientY - pos.y };
    e.currentTarget.setPointerCapture(e.pointerId);
  }

  function onDragMove(e) {
    if (!dragOffset.current) return;
    setPos({
      x: Math.min(Math.max(8, e.clientX - dragOffset.current.dx), window.innerWidth - 120),
      y: Math.min(Math.max(8, e.clientY - dragOffset.current.dy), window.innerHeight - 60),
    });
  }

  function onDragEnd() {
    dragOffset.current = null;
  }

  // 선택 날짜를 회차 창(nextResetDate 기준 반열린 구간)으로 쪼갠다 — 경계일 당일은 다음 회차다.
  // nextResetDate가 없으면(온보딩 미확정 등, 방어적) 전부 현재 회차로 본다 — 설계 §9와 같은 규칙.
  const unitDays = leaveType === 'ANNUAL' ? 1 : 0.5;
  const currentCycleDates = !isLeave
    ? []
    : nextResetDate
      ? dates.filter((d) => d < nextResetDate)
      : dates;
  const nextCycleDates = !isLeave || !nextResetDate ? [] : dates.filter((d) => d >= nextResetDate);
  const currentDays = currentCycleDates.length * unitDays;
  const nextDays = nextCycleDates.length * unitDays;
  // 차감 일수 — 반차는 날짜당 0.5일. 개인 일정은 항상 0이다. 잔여에서는 현재 회차분만 뺀다.
  const days = currentDays + nextDays;
  const afterRemaining = Math.round((remainingDays - currentDays) * 10) / 10;

  // 다음 회차 예약 — 이미 예약된 일수 + 이번 신청분. 정책 키가 꺼져 있으면 제출 자체를 막는다.
  const hasNextCycleSelection = nextDays > 0;
  const nextCycleTotalReserved = Number(nextCycleReservedDays) + nextDays;
  const nextCycleBlocked = hasNextCycleSelection && !nextCycleReservationEnabled;

  // 연차는 주말·공휴일을 신청할 수 없다 (서버도 거부). 개인 일정은 허용하므로 모드에 따라 갈린다.
  const blockedSelected = isLeave ? dates.filter((d) => blockedDates.includes(d)) : [];
  const hasBlocked = blockedSelected.length > 0;

  function rememberChoice() {
    try {
      localStorage.setItem(LAST_USED_KEY, JSON.stringify({ mode, leaveType, scheduleType }));
    } catch {
      // 저장 실패는 기능에 영향이 없다 (사생활 모드 등) — 조용히 넘어간다
    }
  }

  async function handleSubmit() {
    if (dates.length === 0) {
      toast.error('캘린더에서 날짜를 먼저 선택해 주세요.');
      return;
    }
    if (isLeave && hasBlocked) {
      toast.error('주말·공휴일은 연차로 신청할 수 없어요. 날짜를 빼거나 개인 일정으로 등록하세요.');
      return;
    }
    if (isLeave && !reason.trim()) {
      toast.error('신청 사유를 입력해 주세요.');
      return;
    }
    if (isLeave && nextCycleBlocked) {
      toast.error('다음 회차 예약이 허용되지 않아요. 다음 기산일 이후 날짜를 빼고 다시 시도하세요.');
      return;
    }

    try {
      if (isLeave) {
        // 잔여 초과분은 당겨쓰기 설계(advance_max_days, 기본 5일)에 따라 백엔드가 검증
        await applyLeaveMutation.mutateAsync({
          leaveType,
          dates,
          reason,
          subApproverId: subApproverId || null,
        });
        rememberChoice();
        toast.success(`${LEAVE_TYPE_LABEL[leaveType]} ${days}일 신청이 접수되었습니다.`, { icon: '🗓️' });
        onClose();
        return;
      }

      await createScheduleMutation.mutateAsync({ scheduleType, dates, memo: reason });
      rememberChoice();
      const label = scheduleTypeOptions.find((o) => o.value === scheduleType)?.label ?? '일정';
      // 승인이 없으므로 "접수"가 아니라 "등록"이다 — 결재를 기다린다고 오해하지 않게
      toast.success(`${label} ${dates.length}일이 등록되었습니다.`, { icon: '📍' });
      // 개인 일정은 연속 등록이 잦다(이번 주 재택 → 다음 주 외근). 패널을 열어둔 채 날짜만 비운다.
      setReason('');
      onSubmitted();
    } catch {
      // 실패 toast는 api 인터셉터가 일괄 처리 — 패널은 열어둔 채 재시도할 수 있게 둔다
    }
  }

  return (
    <div
      role="dialog"
      aria-label="캘린더 등록 패널"
      className="calendar-entry-panel glass-strong glass-edge fixed z-50 w-[424px] overflow-hidden rounded-card border border-white/[0.15] shadow-card"
      style={{ left: pos.x, top: pos.y }}
    >
      {/* 드래그 핸들 헤더 */}
      <div
        onPointerDown={onDragStart}
        onPointerMove={onDragMove}
        onPointerUp={onDragEnd}
        onPointerCancel={onDragEnd}
        className="flex cursor-move touch-none select-none items-center justify-between gap-2 rounded-t-card border-b border-white/[0.12] bg-navy-app/40 px-4 py-3"
      >
        <div className="flex items-center gap-2">
          <GripVertical size={15} className="text-ink-dim" />
          <span className="text-[15px] font-semibold text-ink-hi">
            {isLeave ? '연차 신청' : '일정 등록'}
          </span>
          <span className="text-[12px] text-ink-faint">끌어서 이동</span>
        </div>
        <IconButton
          Icon={X}
          label="닫기"
          onClick={onClose}
          onPointerDown={(e) => e.stopPropagation()}
        />
      </div>

      <div className="flex flex-col gap-4 px-5 py-4">
        {/* 1단 — 성격이 다른 두 흐름을 먼저 가른다 (차감·결재 유무) */}
        <SegmentedControl options={MODE_OPTIONS} value={mode} onChange={setMode} />

        {/* 2단 — 세부 종류 */}
        {isLeave ? (
          <SegmentedControl options={LEAVE_TYPE_OPTIONS} value={leaveType} onChange={setLeaveType} />
        ) : (
          <div className="flex flex-wrap gap-1.5">
            {scheduleTypeOptions.map((opt) => (
              <button
                key={opt.value}
                type="button"
                onClick={() => setScheduleType(opt.value)}
                className={`rounded-badge px-3 py-1.5 text-[13px] font-medium transition-colors ${
                  scheduleType === opt.value
                    ? 'bg-accent-cyan/18 text-accent-cyan ring-1 ring-inset ring-accent-cyan/40'
                    : 'bg-navy-app/50 text-ink-mute ring-1 ring-inset ring-white/[0.10] hover:text-ink-body'
                }`}
              >
                {opt.label}
              </button>
            ))}
          </div>
        )}

        {/* 선택한 날짜 칩 — 캘린더 클릭으로 추가, ×로 제거 */}
        <Field label={`선택한 날짜 (${dates.length}일)`}>
          {dates.length === 0 ? (
            <p className="rounded-btn border border-dashed border-white/[0.12] px-3 py-3 text-[13px] text-ink-faint">
              캘린더에서 날짜를 클릭해 담으세요. 다시 클릭하면 빠집니다.
            </p>
          ) : (
            <div className="flex flex-wrap gap-1.5">
              {dates.map((d) => {
                const blocked = isLeave && blockedDates.includes(d);
                return (
                  <span
                    key={d}
                    className={`flex items-center gap-1 rounded-badge py-1.5 pl-3 pr-2 text-[13px] font-medium ring-1 ring-inset ${
                      blocked
                        ? 'bg-danger/12 text-danger ring-danger/30'
                        : 'bg-accent-cyan/12 text-accent-cyan ring-accent-cyan/25'
                    }`}
                  >
                    {dayjs(d).format('M/D')} ({WEEKDAY_KO[dayjs(d).day()]})
                    <button
                      type="button"
                      onClick={() => onRemoveDate(d)}
                      className="rounded p-0.5 transition-colors hover:bg-white/[0.14]"
                      aria-label={`${d} 제거`}
                    >
                      <X size={12} />
                    </button>
                  </span>
                );
              })}
            </div>
          )}
          {onOpenDatePicker && (
            <button
              type="button"
              onClick={onOpenDatePicker}
              aria-label="날짜 더 선택하기"
              className="mt-2 flex w-full items-center justify-center gap-2 rounded-btn bg-accent-cyan/10 px-3 py-2.5 text-[13px] font-semibold text-accent-cyan ring-1 ring-inset ring-accent-cyan/30 transition-colors hover:bg-accent-cyan/15"
            >
              <CalendarDays size={15} />
              날짜 더 선택하기
            </button>
          )}
        </Field>

        {/* 주말·공휴일 경고 — 연차 모드에서만. 개인 일정으로 바꾸면 그대로 등록할 수 있다 */}
        {hasBlocked && (
          <p className="rounded-btn bg-danger/10 px-3 py-2.5 text-[12px] text-danger ring-1 ring-inset ring-danger/25">
            주말·공휴일 {blockedSelected.length}일이 포함돼 있어 연차로는 신청할 수 없어요.
            해당 날짜를 빼거나, 위에서 <strong>개인 일정</strong>으로 바꿔 등록하세요.
          </p>
        )}

        {/* 사유(연차, 필수) / 메모(일정, 선택) */}
        <Field label={isLeave ? '신청 사유' : '메모 (선택)'}>
          <Textarea
            value={reason}
            onChange={(e) => setReason(e.target.value)}
            rows={2}
            placeholder={
              isLeave ? '사유를 입력하세요 (승인자에게만 표시)' : '방문처·업무 내용 등 (본인에게만 표시)'
            }
          />
        </Field>

        {/* 연차에만 있는 것 — 승인자. 개인 일정은 결재가 없어 이 블록이 통째로 빠진다 */}
        {isLeave && (
          <div>
            <p className="mb-2 rounded-btn bg-navy-app/50 px-3 py-2.5 text-[12px] text-ink-mute">
              담당 승인자는 부서장이 자동으로 배정됩니다.
            </p>
            <Field label="서브 승인자 (선택)">
              <Select
                value={subApproverId}
                onChange={(e) => setSubApproverId(e.target.value)}
                disabled={approversQuery.isLoading}
              >
                <option value="">
                  {approversQuery.isLoading ? '불러오는 중…' : '선택 안 함'}
                </option>
                {approvers.map((a) => (
                  <option key={a.id} value={a.id}>
                    {a.name}
                    {a.departmentName ? ` · ${a.departmentName}` : ''}
                  </option>
                ))}
              </Select>
            </Field>
          </div>
        )}

        {/* 요약 — 연차는 차감 예정, 개인 일정은 차감 없음을 분명히 말한다 */}
        {isLeave ? (
          <div className="flex flex-col gap-2">
            <div className="flex items-center justify-between rounded-btn bg-navy-app/50 px-3.5 py-3 text-[13px]">
              <span className="text-ink-mute">차감 예정</span>
              <span className="font-semibold text-ink-hi tabular-nums">
                <span className="text-[16px]">{currentDays}</span>일
                <span className="ml-2 font-normal text-ink-mute">
                  잔여 {remainingDays} →{' '}
                  <span className={afterRemaining < 0 ? 'text-warn' : ''}>{afterRemaining}일</span>
                </span>
              </span>
            </div>

            {/* 다음 회차분 — 아직 부여되지 않은 배정이라 잔여에서 빼지 않고 예약분으로만 보여준다 */}
            {hasNextCycleSelection && (
              <div className="rounded-btn bg-accent-cyan/10 px-3.5 py-3 text-[13px] ring-1 ring-inset ring-accent-cyan/20">
                <span className="font-semibold text-accent-cyan tabular-nums">
                  {`${NEXT_CYCLE_RESERVATION_LABEL} +${nextDays}일`}
                </span>
                <span className="ml-2 text-ink-mute">
                  {`(예약 가능 ${Number(nextCycleAllowanceDays)}일 중 ${nextCycleTotalReserved}일 사용)`}
                </span>
              </div>
            )}

            {nextCycleBlocked && (
              <p className="rounded-btn bg-danger/10 px-3 py-2.5 text-[12px] text-danger ring-1 ring-inset ring-danger/25">
                다음 기산일({nextResetDate ? dayjs(nextResetDate).format('M/D') : '-'}) 이후 날짜는
                지금 예약할 수 없어요. 해당 날짜를 빼주세요.
              </p>
            )}
          </div>
        ) : (
          <div className="flex items-center justify-between rounded-btn bg-navy-app/50 px-3.5 py-3 text-[13px]">
            <span className="text-ink-mute">연차 차감</span>
            <span className="font-semibold text-ok">없음 · 승인 없이 바로 등록</span>
          </div>
        )}

        <Button
          onClick={handleSubmit}
          loading={pending}
          disabled={isLeave && nextCycleBlocked}
          size="lg"
          className="w-full"
        >
          {pending ? (isLeave ? '신청 중…' : '등록 중…') : isLeave ? '신청하기' : '등록하기'}
        </Button>
      </div>
    </div>
  );
}
