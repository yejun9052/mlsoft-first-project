import { beforeEach, describe, expect, it, vi } from 'vitest';
import { fireEvent, render, screen, within } from '@testing-library/react';
import { createMemoryRouter, RouterProvider } from 'react-router-dom';
import HistoryPage from './HistoryPage.jsx';
import { useCancelLeave, useLeaveSummary, useMyLeaves } from '../hooks/useLeaves.js';
import { useCurrentUser } from '../hooks/useAuth.js';

vi.mock('../hooks/useLeaves.js', () => ({
  useCancelLeave: vi.fn(),
  useLeaveSummary: vi.fn(),
  useMyLeaves: vi.fn(),
}));

vi.mock('../hooks/useAuth.js', () => ({
  useCurrentUser: vi.fn(),
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

// 기본 입사일은 모든 기존 테스트 신청(2026-08-01)보다 훨씬 앞선 값이다 — 이전 근속이 없는
// 사원의 기본 동작(구간 UI 없음)을 그대로 유지한다. 재입사 시나리오를 다루는 테스트만 이 값을
// 신청 날짜 사이로 넣어 이전 근속을 만든다.
function renderPage(rows, summaryOverrides = {}, hireDate = '2020-01-01') {
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
  useCurrentUser.mockReturnValue({ data: { hireDate } });

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

describe('HistoryPage 근속 구간', () => {
  const 현재신청사유 = '현재근속사유';
  const 이전신청사유 = '이전근속사유';

  function 신청(id, createdAt, reason, dates = ['2026-08-20']) {
    return {
      id,
      leaveType: 'ANNUAL',
      dates,
      days: '1.0',
      requestReason: reason,
      status: 'APPROVED',
      createdAt,
    };
  }

  beforeEach(() => {
    vi.clearAllMocks();
  });

  // 대부분의 사원은 재입사한 적이 없다 — 이 조건이 이 파도에서 가장 중요하다 (설계-초안 §6).
  it('이전 근속이 없으면 구간 UI가 렌더되지 않는다', () => {
    renderPage([신청(1, '2026-08-01T09:00:00', 현재신청사유)]);

    expect(screen.queryByText(/이전 근속/)).not.toBeInTheDocument();
  });

  it('구간이 있으면 현재 근속이 펼쳐지고 이전은 접혀 있다', () => {
    renderPage(
      [
        신청(1, '2026-08-01T09:00:00', 현재신청사유),
        신청(2, '2019-05-01T09:00:00', 이전신청사유, ['2019-05-02']),
      ],
      {},
      '2026-01-01',
    );

    // 현재 근속 — 별도 조작 없이 바로 보인다("펼쳐져 있다")
    expect(screen.getByText(현재신청사유)).toBeInTheDocument();
    // 이전 근속 — 토글 버튼만 보이고 내용은 접혀 있어 보이지 않는다
    expect(screen.getByRole('button', { name: /이전 근속/ })).toBeInTheDocument();
    expect(screen.queryByText(이전신청사유)).not.toBeInTheDocument();
  });

  it('이전 근속을 펼치면 그 기간의 신청만 보인다', () => {
    renderPage(
      [
        신청(1, '2026-08-01T09:00:00', 현재신청사유),
        신청(2, '2019-05-01T09:00:00', 이전신청사유, ['2019-05-02']),
      ],
      {},
      '2026-01-01',
    );

    fireEvent.click(screen.getByRole('button', { name: /이전 근속/ }));

    expect(screen.getByText(이전신청사유)).toBeInTheDocument();
    // 현재 근속 신청은 이전 근속 섹션에 섞이지 않는다 — 메인 표에만 한 번 나온다
    expect(screen.getAllByText(현재신청사유)).toHaveLength(1);
  });
});
