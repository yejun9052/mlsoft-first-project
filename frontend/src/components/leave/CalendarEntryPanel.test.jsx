import { beforeEach, describe, expect, it, vi } from 'vitest';
import { render, screen } from '@testing-library/react';
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
      blockedDates={[]}
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
});
