import { beforeEach, describe, expect, it, vi } from 'vitest';
import { fireEvent, render, screen, within } from '@testing-library/react';
import { createMemoryRouter, RouterProvider } from 'react-router-dom';
import HistoryPage from './HistoryPage.jsx';
import { useCancelLeave, useLeaveSummary, useMyLeaves } from '../hooks/useLeaves.js';

vi.mock('../hooks/useLeaves.js', () => ({
  useCancelLeave: vi.fn(),
  useLeaveSummary: vi.fn(),
  useMyLeaves: vi.fn(),
}));

vi.mock('../components/schedule/MySchedulesTable.jsx', () => ({
  default: function MockMySchedulesTable() {
    return <div>개인 일정 테스트 대역</div>;
  },
}));

vi.mock('react-hot-toast', () => ({
  default: { success: vi.fn(), error: vi.fn() },
}));

const 긴사유 = '고객사와 중요한 계약 협의를 진행하기 위해 장거리 출장을 다녀옵니다.';
const 짧은사유 = '병원 방문';

function leave(id, requestReason) {
  return {
    id,
    leaveType: 'ANNUAL',
    dates: ['2026-08-20'],
    days: '1.0',
    requestReason,
    status: 'APPROVED',
    createdAt: '2026-08-01T09:00:00',
  };
}

function renderPage(rows, summaryOverrides = {}) {
  useMyLeaves.mockReturnValue({
    data: { content: rows },
    isLoading: false,
    isError: false,
    refetch: vi.fn(),
  });
  useLeaveSummary.mockReturnValue({
    data: {
      baseDays: '15.0',
      bonusDays: '0.0',
      useDays: '2.0',
      pendingDays: '0.0',
      remainingDays: '13.0',
      ...summaryOverrides,
    },
    isError: false,
    refetch: vi.fn(),
  });
  useCancelLeave.mockReturnValue({ mutate: vi.fn(), isPending: false });

  const router = createMemoryRouter(
    [{ path: '/history', element: <HistoryPage /> }],
    { initialEntries: ['/history'] },
  );
  return render(<RouterProvider router={router} />);
}

describe('HistoryPage 신청 사유 상세', () => {
  beforeEach(() => {
    vi.clearAllMocks();
  });

  it('20자를 넘는 사유는 말줄임하고 상세 보기 버튼으로 만든다', () => {
    renderPage([leave(1, 긴사유)]);

    expect(screen.getByText(`${긴사유.slice(0, 20)}…`)).toBeInTheDocument();
    expect(screen.queryByText(긴사유)).not.toBeInTheDocument();
    expect(screen.getByRole('button', { name: `사유 상세 보기: ${긴사유}` })).toBeInTheDocument();
  });

  it('긴 사유를 누르면 기존 모달에 잘리지 않은 전문을 보여준다', () => {
    renderPage([leave(1, 긴사유)]);

    fireEvent.click(screen.getByRole('button', { name: `사유 상세 보기: ${긴사유}` }));

    const dialog = screen.getByRole('dialog', { name: '신청 사유' });
    expect(within(dialog).getByText(긴사유)).toBeInTheDocument();
  });

  it('20자 이하 사유는 클릭 대상이 아니다', () => {
    renderPage([leave(1, 짧은사유)]);

    expect(screen.getByText(짧은사유)).toBeInTheDocument();
    expect(screen.queryByRole('button', { name: /사유 상세 보기/ })).not.toBeInTheDocument();
  });
});

describe('HistoryPage 통계 스트립 — 다음 회차 예약 표시', () => {
  beforeEach(() => {
    vi.clearAllMocks();
  });

  it('다음 회차 예약이 0이면 통계 스트립에 다음 회차 예약 항목이 없다', () => {
    renderPage([leave(1, 짧은사유)]);

    expect(screen.queryByText(/다음 회차 예약/)).not.toBeInTheDocument();
  });

  it('다음 회차 예약이 있으면 통계 스트립에 일수가 보인다', () => {
    renderPage([leave(1, 짧은사유)], { nextCycleReservedDays: '5.0' });

    expect(screen.getByText('다음 회차 예약')).toBeInTheDocument();
  });
});
