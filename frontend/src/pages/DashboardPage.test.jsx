import { beforeEach, describe, expect, it, vi } from 'vitest';
import { fireEvent, render, screen } from '@testing-library/react';
import { RouterProvider, createMemoryRouter } from 'react-router-dom';
import DashboardPage from './DashboardPage.jsx';
import { useCurrentUser } from '../hooks/useAuth.js';
import { useLeaveSummary, useMyLeaves } from '../hooks/useLeaves.js';
import { useMySchedules } from '../hooks/useSchedules.js';
import { useMyWelfareRequests } from '../hooks/useWelfare.js';
import { useHolidays } from '../hooks/useHolidays.js';

vi.mock('../hooks/useAuth.js', () => ({ useCurrentUser: vi.fn() }));
vi.mock('../hooks/useLeaves.js', () => ({
  useLeaveSummary: vi.fn(),
  useMyLeaves: vi.fn(),
}));
vi.mock('../hooks/useSchedules.js', () => ({ useMySchedules: vi.fn() }));
vi.mock('../hooks/useWelfare.js', () => ({ useMyWelfareRequests: vi.fn() }));
vi.mock('../hooks/useHolidays.js', () => ({ useHolidays: vi.fn() }));

const SUMMARY = {
  baseDays: '15.0',
  bonusDays: '0.0',
  useDays: '3.0',
  pendingDays: '0.0',
  remainingDays: '12.0',
  nextResetDate: '2099-12-31',
};

function renderPage() {
  const router = createMemoryRouter(
    [
      { path: '/dashboard', element: <DashboardPage /> },
      { path: '/calendar', element: <div>캘린더</div> },
      { path: '/welfare', element: <div>복리후생</div> },
      { path: '/history', element: <div>이력</div> },
    ],
    { initialEntries: ['/dashboard'] },
  );

  return render(<RouterProvider router={router} />);
}

function setSchedules(content) {
  useMySchedules.mockReturnValue({ data: { content } });
}

describe('DashboardPage 다가오는 내 일정', () => {
  beforeEach(() => {
    vi.clearAllMocks();
    useCurrentUser.mockReturnValue({
      data: { id: 1, name: '홍길동', departmentName: '개발팀' },
      isError: false,
      refetch: vi.fn(),
    });
    useLeaveSummary.mockReturnValue({
      data: SUMMARY,
      isError: false,
      refetch: vi.fn(),
    });
    useMyLeaves.mockReturnValue({ data: { content: [] } });
    useMyWelfareRequests.mockReturnValue({ data: { content: [] } });
    useHolidays.mockReturnValue({ data: [] });
    setSchedules([]);
  });

  it('등록 순서와 관계없이 날짜가 가까운 일정부터 표시한다', () => {
    setSchedules([
      {
        id: 1,
        scheduleType: 'TRAINING',
        typeLabel: '교육·연수',
        dates: ['2099-05-20'],
        memo: null,
      },
      {
        id: 2,
        scheduleType: 'BUSINESS_TRIP',
        typeLabel: '출장',
        dates: ['2099-04-10'],
        memo: null,
      },
      {
        id: 3,
        scheduleType: 'FIELD_WORK',
        typeLabel: '외근',
        dates: ['2099-04-01'],
        memo: null,
      },
    ]);

    renderPage();

    const labels = screen.getAllByText(/^(외근|출장|교육·연수)$/);
    expect(labels.map((node) => node.textContent)).toEqual(['외근', '출장', '교육·연수']);
  });

  it('지난 날짜만 가진 일정은 표시하지 않는다', () => {
    setSchedules([
      {
        id: 1,
        scheduleType: 'FIELD_WORK',
        typeLabel: '과거 외근',
        dates: ['2000-01-01'],
        memo: '지난 일정 메모',
      },
      {
        id: 2,
        scheduleType: 'BUSINESS_TRIP',
        typeLabel: '예정 출장',
        dates: ['2099-01-01'],
        memo: null,
      },
    ]);

    renderPage();

    expect(screen.queryByText('과거 외근')).not.toBeInTheDocument();
    expect(screen.getByText('예정 출장')).toBeInTheDocument();
  });

  // 공휴일은 개인 일정과 **다른 경로**로 합쳐진다. 지난 날짜 제외를 한쪽에만 걸면
  // 개인 일정은 걸러지는데 지난 공휴일이 목록 맨 위를 계속 차지한다
  it('지난 공휴일은 표시하지 않는다', () => {
    // 대시보드는 연말에 다음 해 첫 일정을 놓치지 않으려고 useHolidays를 **올해와 내년 두 번** 부른다.
    // mockReturnValue로 한 값을 주면 같은 공휴일이 두 벌 와서 조회가 모호해진다
    useHolidays.mockImplementation((year) => ({
      data: year === new Date().getFullYear()
        ? [
            { date: '2020-01-01', name: '지난 신정' },
            { date: '2099-01-01', name: '다가오는 신정' },
          ]
        : [],
    }));

    renderPage();

    expect(screen.queryByText('지난 신정')).not.toBeInTheDocument();
    expect(screen.getByText('다가오는 신정')).toBeInTheDocument();
  });

  // 상한이 없으면 대시보드 오른쪽 스택이 일정 수만큼 늘어난다 — 카드가 이미 많은 화면이다
  it('다가오는 것이 많아도 상한(5건)까지만 표시한다', () => {
    setSchedules(
      Array.from({ length: 8 }, (unused, index) => ({
        id: index + 1,
        scheduleType: 'BUSINESS_TRIP',
        typeLabel: '출장',
        dates: [`2099-04-0${index + 1}`],
        memo: null,
      })),
    );

    renderPage();

    expect(screen.getAllByText('출장')).toHaveLength(5);
  });

  it('다가오는 일정과 공휴일이 없으면 EmptyState를 표시한다', () => {
    renderPage();

    expect(
      screen.getByText('다가오는 개인 일정이나 공휴일이 없습니다.'),
    ).toBeInTheDocument();
  });

  it('메모는 호버 전에는 없고 일정 행에 호버했을 때만 보인다', () => {
    setSchedules([
      {
        id: 1,
        scheduleType: 'BUSINESS_TRIP',
        typeLabel: '출장',
        dates: ['2099-01-01'],
        memo: '오전 9시 고객사 로비 집결',
      },
    ]);

    renderPage();

    expect(screen.queryByText('오전 9시 고객사 로비 집결')).not.toBeInTheDocument();

    const row = screen.getByText('출장').closest('li');
    fireEvent.mouseEnter(row);

    expect(screen.getByRole('tooltip')).toHaveTextContent('오전 9시 고객사 로비 집결');

    fireEvent.mouseLeave(row);

    expect(screen.queryByText('오전 9시 고객사 로비 집결')).not.toBeInTheDocument();
  });
});
