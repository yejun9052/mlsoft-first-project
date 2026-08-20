import { beforeEach, describe, expect, it, vi } from 'vitest';
import { fireEvent, render, screen, within } from '@testing-library/react';
import { RouterProvider, createMemoryRouter } from 'react-router-dom';
import CalendarPage from './CalendarPage.jsx';
import { useCurrentUser } from '../hooks/useAuth.js';
import { useLeaveCalendar, useLeaveSummary } from '../hooks/useLeaves.js';
import { useScheduleCalendar } from '../hooks/useSchedules.js';
import { useDepartments } from '../hooks/useDepartments.js';
import { useHolidays } from '../hooks/useHolidays.js';

vi.mock('../hooks/useAuth.js', () => ({ useCurrentUser: vi.fn() }));
vi.mock('../hooks/useLeaves.js', () => ({
  useLeaveCalendar: vi.fn(),
  useLeaveSummary: vi.fn(),
}));
vi.mock('../hooks/useSchedules.js', () => ({
  useScheduleCalendar: vi.fn(),
  useCreateSchedule: vi.fn(() => ({ mutate: vi.fn(), isPending: false })),
  useScheduleTypes: vi.fn(() => ({ data: [] })),
}));
vi.mock('../hooks/useDepartments.js', () => ({ useDepartments: vi.fn() }));
vi.mock('../hooks/useHolidays.js', () => ({ useHolidays: vi.fn() }));
vi.mock('../hooks/useUsers.js', () => ({
  useApprovers: vi.fn(() => ({ data: [] })),
}));
vi.mock('react-hot-toast', () => ({
  default: { success: vi.fn(), error: vi.fn() },
}));

const DATE = '2026-08-20';

const LEAVES = [
  {
    id: 1,
    userId: 1,
    userName: '홍길동',
    leaveType: 'ANNUAL',
    dates: [DATE],
    reason: '병원 예약',
  },
  {
    id: 2,
    userId: 2,
    userName: '김동료',
    leaveType: 'HALF_AM',
    dates: [DATE],
    reason: null,
  },
  {
    id: 3,
    userId: 3,
    userName: '이동료',
    leaveType: 'HALF_PM',
    dates: [DATE],
    reason: null,
  },
];

const SCHEDULES = [
  {
    id: 10,
    userId: 1,
    userName: '홍길동',
    scheduleType: 'BUSINESS_TRIP',
    typeLabel: '출장',
    dates: [DATE],
    memo: '오전 9시 고객사 미팅',
  },
];

function renderPage() {
  const router = createMemoryRouter(
    [{ path: '/calendar', element: <CalendarPage /> }],
    { initialEntries: ['/calendar'] },
  );
  return render(<RouterProvider router={router} />);
}

describe('CalendarPage 하루 상세 모달', () => {
  beforeEach(() => {
    vi.clearAllMocks();
    useCurrentUser.mockReturnValue({ data: { id: 1 } });
    useLeaveSummary.mockReturnValue({ data: { remainingDays: '10.0' } });
    useLeaveCalendar.mockReturnValue({ data: LEAVES });
    useScheduleCalendar.mockReturnValue({ data: SCHEDULES });
    useDepartments.mockReturnValue({ data: [] });
    useHolidays.mockReturnValue({
      data: [{ date: DATE, name: '테스트 공휴일' }],
    });
  });

  it('페이지 제목은 캘린더로 표시한다', () => {
    renderPage();

    expect(screen.getByRole('heading', { name: '캘린더' })).toBeInTheDocument();
    expect(screen.queryByRole('heading', { name: '팀 캘린더' })).not.toBeInTheDocument();
  });

  it('강조된 이전·다음 달 버튼으로 연도 경계를 포함해 월을 이동한다', () => {
    vi.useFakeTimers();
    vi.setSystemTime(new Date('2026-01-20T12:00:00+09:00'));

    renderPage();

    expect(screen.getByText('2026년 1월')).toBeInTheDocument();

    fireEvent.click(screen.getByRole('button', { name: '이전 달' }));
    expect(screen.getByText('2025년 12월')).toBeInTheDocument();

    fireEvent.click(screen.getByRole('button', { name: '다음 달' }));
    fireEvent.click(screen.getByRole('button', { name: '다음 달' }));
    expect(screen.getByText('2026년 2월')).toBeInTheDocument();

    vi.useRealTimers();
  });

  it('셀 표시 상한을 넘으면 초과 항목 수 손잡이가 보인다', () => {
    renderPage();

    expect(screen.getByText('+1개')).toBeInTheDocument();
  });

  // 손잡이가 보이는 것만으로는 부족하다. 셀이 상한을 안 지키면 **넘친 것까지 다 그려 놓고
  // "+1개"도 함께 띄우는** 상태가 되는데, 그때 셀 높이가 날마다 들쭉날쭉해진다
  it('셀에는 상한(3건)까지만 그리고 나머지는 손잡이 뒤에 둔다', () => {
    renderPage();

    // 모달을 열기 전 — 이 날짜의 항목은 연차 3 + 일정 1로 넷이다
    expect(screen.queryByRole('dialog')).not.toBeInTheDocument();
    expect(screen.getAllByText(/^(홍길동|김동료|이동료)$/)).toHaveLength(3);
  });

  it('초과 손잡이를 누르면 그날의 연차·개인 일정·공휴일이 모두 보인다', () => {
    renderPage();

    fireEvent.click(screen.getByText('+1개'));

    const dialog = screen.getByRole('dialog', { name: /2026년 8월 20일/ });
    expect(within(dialog).getByText('테스트 공휴일')).toBeInTheDocument();
    // 홍길동은 이 날짜에 **연차와 출장을 둘 다** 갖고 있다. 두 줄이 다 나와야
    // "연차·개인 일정이 모두 보인다"가 성립한다 — 한 줄만 나오면 한쪽이 빠진 것이다
    expect(within(dialog).getAllByText('홍길동')).toHaveLength(2);
    expect(within(dialog).getByText('김동료')).toBeInTheDocument();
    expect(within(dialog).getByText('이동료')).toBeInTheDocument();
    expect(within(dialog).getByText('출장')).toBeInTheDocument();
    expect(within(dialog).getByText('병원 예약')).toBeInTheDocument();
    expect(within(dialog).getByText('오전 9시 고객사 미팅')).toBeInTheDocument();
  });

  it('서버가 마스킹한 타인 사유는 화면에서 만들어 내지 않는다', () => {
    renderPage();

    fireEvent.click(screen.getByText('+1개'));

    const dialog = screen.getByRole('dialog', { name: /2026년 8월 20일/ });
    expect(within(dialog).queryByText('비공개')).not.toBeInTheDocument();
    expect(within(dialog).queryByText('사유 없음')).not.toBeInTheDocument();
    expect(within(dialog).getByText('병원 예약')).toBeInTheDocument();
  });

  it('초과 손잡이는 상세만 열고 등록 패널을 열지 않는다', () => {
    renderPage();

    fireEvent.click(screen.getByText('+1개'));

    expect(screen.getByRole('dialog', { name: /2026년 8월 20일/ })).toBeInTheDocument();
    expect(screen.queryByText('연차·일정 등록')).not.toBeInTheDocument();
  });
});
