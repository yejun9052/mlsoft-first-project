import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';
import { fireEvent, render, screen } from '@testing-library/react';
import CalendarEntryPanel from './CalendarEntryPanel.jsx';
import { useApplyLeave } from '../../hooks/useLeaves.js';
import { useCreateSchedule, useScheduleTypes } from '../../hooks/useSchedules.js';
import { useApprovers } from '../../hooks/useUsers.js';

vi.mock('../../hooks/useLeaves.js', () => ({ useApplyLeave: vi.fn() }));
vi.mock('../../hooks/useSchedules.js', () => ({
  useCreateSchedule: vi.fn(),
  useScheduleTypes: vi.fn(),
}));
vi.mock('../../hooks/useUsers.js', () => ({ useApprovers: vi.fn() }));
vi.mock('react-hot-toast', () => ({ default: { success: vi.fn(), error: vi.fn() } }));

const NEXT_RESET_DATE = '2026-09-10';

function renderPanel(props) {
  return render(
    <CalendarEntryPanel
      dates={[]}
      holidayDates={[]}
      remainingDays={12}
      onRemoveDate={vi.fn()}
      onClose={vi.fn()}
      onSubmitted={vi.fn()}
      {...props}
    />,
  );
}

describe('CalendarEntryPanel 다음 회차 예약 분리', () => {
  beforeEach(() => {
    vi.clearAllMocks();
    localStorage.clear();
    useApplyLeave.mockReturnValue({ mutateAsync: vi.fn(), isPending: false });
    useCreateSchedule.mockReturnValue({ mutateAsync: vi.fn(), isPending: false });
    useScheduleTypes.mockReturnValue({ data: [] });
    useApprovers.mockReturnValue({ data: [] });
  });

  it('현재 회차 날짜만 골랐을 때 afterRemaining이 기존과 같다(회차분 없음)', () => {
    renderPanel({
      dates: ['2026-08-20'],
      remainingDays: 12,
      nextResetDate: NEXT_RESET_DATE,
    });

    // 연차 1일 신청 → 잔여 12 - 1 = 11
    expect(screen.getByText('차감 예정').closest('div')).toHaveTextContent('11일');
    expect(screen.queryByText(/다음 회차 예약/)).not.toBeInTheDocument();
  });

  it('기산일을 걸쳐 고르면 현재 회차분만 잔여에서 빠지고 다음 회차분은 따로 표시된다', () => {
    renderPanel({
      // 2026-09-09는 현재 회차, 2026-09-10(경계일 당일)·2026-09-11은 다음 회차
      dates: ['2026-09-09', NEXT_RESET_DATE, '2026-09-11'],
      remainingDays: 12,
      nextResetDate: NEXT_RESET_DATE,
      nextCycleReservedDays: 1,
      nextCycleAllowanceDays: 15,
    });

    // 현재 회차 1일만 차감 → 잔여 12 - 1 = 11
    expect(screen.getByText('차감 예정').closest('div')).toHaveTextContent('11일');
    // 다음 회차 2일 신규 + 기존 예약 1일 = 3일
    expect(screen.getByText('다음 회차 예약 +2일')).toBeInTheDocument();
    expect(screen.getByText('(예약 가능 15일 중 3일 사용)')).toBeInTheDocument();
  });

  it('nextCycleReservedDays가 0(다음 회차 날짜 미선택)이면 다음 회차 줄이 없다', () => {
    renderPanel({
      dates: ['2026-08-20'],
      remainingDays: 12,
      nextResetDate: NEXT_RESET_DATE,
      nextCycleReservedDays: 0,
    });

    expect(screen.queryByText(/다음 회차 예약/)).not.toBeInTheDocument();
  });

  it('nextCycleReservationEnabled가 false면 다음 회차 날짜 선택 시 신청 버튼을 비활성화한다', () => {
    renderPanel({
      dates: [NEXT_RESET_DATE],
      remainingDays: 12,
      nextResetDate: NEXT_RESET_DATE,
      nextCycleReservationEnabled: false,
    });

    expect(screen.getByRole('button', { name: '신청하기' })).toBeDisabled();
  });

  it('다음 회차 날짜가 없으면 정책이 꺼져 있어도 신청 버튼은 활성 상태다', () => {
    renderPanel({
      dates: ['2026-08-20'],
      remainingDays: 12,
      nextResetDate: NEXT_RESET_DATE,
      nextCycleReservationEnabled: false,
    });

    expect(screen.getByRole('button', { name: '신청하기' })).not.toBeDisabled();
  });

  it('다른 달의 주말이 섞여 있어도 경고가 뜨고 신청 버튼이 잠긴다', () => {
    // 깨지면: 달을 넘겨 날짜를 추가하는 순간 앞서 고른 주말이 "막힌 날짜"에서 빠져
    //         경고가 사라지고 신청이 열린다 — 제출한 뒤에야 서버가 거부한다 (B-1)
    renderPanel({ dates: ['2026-10-10', '2026-12-10'], holidayDates: [] });

    expect(screen.getByText(/주말·공휴일 1일이 포함/)).toBeInTheDocument();
    expect(screen.getByRole('button', { name: '신청하기' })).toBeDisabled();
  });

  it('공휴일은 넘겨받은 목록으로 판정한다', () => {
    renderPanel({ dates: ['2026-12-25'], holidayDates: ['2026-12-25'] });

    expect(screen.getByText(/주말·공휴일 1일이 포함/)).toBeInTheDocument();
    expect(screen.getByRole('button', { name: '신청하기' })).toBeDisabled();
  });
});

describe('CalendarEntryPanel 모바일 시트 확장', () => {
  const REAL_MATCH_MEDIA = window.matchMedia;

  beforeEach(() => {
    vi.clearAllMocks();
    localStorage.clear();
    useApplyLeave.mockReturnValue({ mutateAsync: vi.fn(), isPending: false });
    useCreateSchedule.mockReturnValue({ mutateAsync: vi.fn(), isPending: false });
    useScheduleTypes.mockReturnValue({ data: [] });
    useApprovers.mockReturnValue({ data: [] });
    window.matchMedia = vi.fn().mockImplementation(() => ({
      matches: true,
      addEventListener: vi.fn(),
      removeEventListener: vi.fn(),
    }));
  });

  afterEach(() => {
    window.matchMedia = REAL_MATCH_MEDIA;
  });

  // 키보드가 실제로 올라오는지는 jsdom에서 관측할 수 없다. 대신 키보드를 띄우는 유일한 신호인
  // "텍스트 입력칸 포커스"로 판정하므로, 그 포커스 이벤트가 확장 클래스를 붙이는지로 검증한다.
  it('사유 입력칸에 포커스가 가면 시트가 전체 화면 클래스를 얻는다', () => {
    renderPanel({ dates: ['2026-08-20'], remainingDays: 12 });

    const panel = screen.getByRole('dialog', { name: '캘린더 등록 패널' });
    expect(panel.className).not.toMatch(/calendar-entry-panel-expanded/);

    fireEvent.focus(screen.getByPlaceholderText('사유를 입력하세요 (승인자에게만 표시)'));

    expect(panel.className).toMatch(/calendar-entry-panel-expanded/);
  });

  it('데스크톱(모바일이 아님)에서는 입력칸 포커스로 확장하지 않는다', () => {
    window.matchMedia = vi.fn().mockImplementation(() => ({
      matches: false,
      addEventListener: vi.fn(),
      removeEventListener: vi.fn(),
    }));
    renderPanel({ dates: ['2026-08-20'], remainingDays: 12 });

    fireEvent.focus(screen.getByPlaceholderText('사유를 입력하세요 (승인자에게만 표시)'));

    const panel = screen.getByRole('dialog', { name: '캘린더 등록 패널' });
    expect(panel.className).not.toMatch(/calendar-entry-panel-expanded/);
  });

  it('핸들을 위로 끌면 전체 화면으로, 다시 아래로 끌면 원래 높이로 돌아온다', () => {
    renderPanel({ dates: ['2026-08-20'], remainingDays: 12 });

    const panel = screen.getByRole('dialog', { name: '캘린더 등록 패널' });
    const handle = screen.getByTestId('calendar-entry-panel-handle');

    fireEvent.pointerDown(handle, { clientY: 400, pointerId: 1 });
    fireEvent.pointerUp(handle, { clientY: 320, pointerId: 1 });
    expect(panel.className).toMatch(/calendar-entry-panel-expanded/);

    fireEvent.pointerDown(handle, { clientY: 320, pointerId: 1 });
    fireEvent.pointerUp(handle, { clientY: 400, pointerId: 1 });
    expect(panel.className).not.toMatch(/calendar-entry-panel-expanded/);
  });
});
