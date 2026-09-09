import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';
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
  useApplyLeave: vi.fn(() => ({ mutateAsync: vi.fn(), isPending: false })),
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

const REAL_MATCH_MEDIA = window.matchMedia;

function renderPage() {
  const router = createMemoryRouter(
    [{ path: '/calendar', element: <CalendarPage /> }],
    { initialEntries: ['/calendar'] },
  );
  return render(<RouterProvider router={router} />);
}

describe('CalendarPage 하루 상세 모달', () => {
  beforeEach(() => {
    // 고정 fixture 날짜(2026-08-20)가 현재 월 그리드에 항상 보이도록 시간을 고정한다.
    vi.useFakeTimers();
    vi.setSystemTime(new Date('2026-08-20T12:00:00+09:00'));
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

  afterEach(() => {
    vi.useRealTimers();
    window.matchMedia = REAL_MATCH_MEDIA;
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

    expect(screen.getByText('...더보기')).toBeInTheDocument();
  });

  // 손잡이가 보이는 것만으로는 부족하다. 셀이 상한을 안 지키면 **넘친 것까지 다 그려 놓고
  // "...더보기"도 함께 띄우는 상태가 되는데, 그때 상세 모달 경로를 확인한다
  it('셀에는 상한(2건)까지만 그리고 나머지는 손잡이 뒤에 둔다', () => {
    renderPage();

    // 모달을 열기 전 — 이 날짜의 항목은 연차 3 + 일정 1로 넷이다
    expect(screen.queryByRole('dialog')).not.toBeInTheDocument();
    expect(screen.getAllByText(/^(홍길동|김동료|이동료)$/)).toHaveLength(2);
  });

  it('초과 손잡이를 누르면 그날의 연차·개인 일정·공휴일이 모두 보인다', () => {
    renderPage();

    fireEvent.click(screen.getByText('...더보기'));

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

    fireEvent.click(screen.getByText('...더보기'));

    const dialog = screen.getByRole('dialog', { name: /2026년 8월 20일/ });
    expect(within(dialog).queryByText('비공개')).not.toBeInTheDocument();
    expect(within(dialog).queryByText('사유 없음')).not.toBeInTheDocument();
    expect(within(dialog).getByText('병원 예약')).toBeInTheDocument();
  });

  it('초과 손잡이는 상세만 열고 등록 패널을 열지 않는다', () => {
    renderPage();

    fireEvent.click(screen.getByText('...더보기'));

    expect(screen.getByRole('dialog', { name: /2026년 8월 20일/ })).toBeInTheDocument();
    expect(screen.queryByText('연차·일정 등록')).not.toBeInTheDocument();
  });

  it('모바일에서는 선택한 날짜의 일정과 추가 버튼을 하단에 보여준다', () => {
    window.matchMedia = vi.fn().mockImplementation(() => ({
      matches: true,
      addEventListener: vi.fn(),
      removeEventListener: vi.fn(),
    }));

    renderPage();

    fireEvent.click(screen.getByRole('button', { name: `${DATE} 등록 날짜 선택` }));

    const summary = screen.getByTestId('mobile-calendar-day-summary');
    expect(within(summary).getByText('출장')).toBeInTheDocument();
    expect(within(summary).getByText('오전 9시 고객사 미팅')).toBeInTheDocument();
    expect(
      within(summary).getByRole('button', { name: '8월 20일에 추가' }),
    ).toBeInTheDocument();
    expect(screen.queryByText('...더보기')).not.toBeInTheDocument();

    fireEvent.click(within(summary).getByRole('button', { name: '8월 20일에 추가' }));
    expect(screen.getByRole('dialog', { name: '캘린더 등록 패널' })).toBeInTheDocument();
  });

  it('모바일에서 날짜를 더 선택한 뒤 선택 완료하면 신청 패널에 선택 날짜를 유지한다', () => {
    window.matchMedia = vi.fn().mockImplementation(() => ({
      matches: true,
      addEventListener: vi.fn(),
      removeEventListener: vi.fn(),
    }));

    renderPage();

    fireEvent.click(screen.getByRole('button', { name: `${DATE} 등록 날짜 선택` }));
    fireEvent.click(screen.getByRole('button', { name: '8월 20일에 추가' }));
    expect(screen.getByRole('dialog', { name: '캘린더 등록 패널' })).toBeInTheDocument();

    fireEvent.click(screen.getByRole('button', { name: '날짜 더 선택하기' }));
    expect(screen.queryByRole('dialog', { name: '캘린더 등록 패널' })).not.toBeInTheDocument();
    expect(screen.getByTestId('mobile-date-selection-toolbar')).toBeInTheDocument();
    expect(screen.getByText(/1일 선택됨/)).toBeInTheDocument();

    fireEvent.click(screen.getByRole('button', { name: '2026-08-21 등록 날짜 선택' }));
    expect(screen.getByText(/2일 선택됨/)).toBeInTheDocument();

    fireEvent.click(screen.getByRole('button', { name: '적용' }));
    const panel = screen.getByRole('dialog', { name: '캘린더 등록 패널' });
    expect(within(panel).getByText('8/20 (목)')).toBeInTheDocument();
    expect(within(panel).getByText('8/21 (금)')).toBeInTheDocument();
  });

  it('날짜 선택 모드에서 고른 날짜는 하단 바에 칩으로 쌓이고 칩의 ×로 뺄 수 있다', () => {
    window.matchMedia = vi.fn().mockImplementation(() => ({
      matches: true,
      addEventListener: vi.fn(),
      removeEventListener: vi.fn(),
    }));

    renderPage();

    fireEvent.click(screen.getByRole('button', { name: `${DATE} 등록 날짜 선택` }));
    fireEvent.click(screen.getByRole('button', { name: '8월 20일에 추가' }));
    fireEvent.click(screen.getByRole('button', { name: '날짜 더 선택하기' }));
    fireEvent.click(screen.getByRole('button', { name: '2026-08-21 등록 날짜 선택' }));

    const toolbar = screen.getByTestId('mobile-date-selection-toolbar');
    expect(within(toolbar).getByText('8/20 (목)')).toBeInTheDocument();
    expect(within(toolbar).getByText('8/21 (금)')).toBeInTheDocument();

    fireEvent.click(within(toolbar).getByRole('button', { name: '2026-08-21 제거' }));
    expect(within(toolbar).queryByText('8/21 (금)')).not.toBeInTheDocument();
    expect(screen.getByText(/1일 선택됨/)).toBeInTheDocument();
  });

  it('날짜 선택 모드에서 취소하면 들어가기 전 선택 상태로 신청 화면에 돌아온다', () => {
    window.matchMedia = vi.fn().mockImplementation(() => ({
      matches: true,
      addEventListener: vi.fn(),
      removeEventListener: vi.fn(),
    }));

    renderPage();

    fireEvent.click(screen.getByRole('button', { name: `${DATE} 등록 날짜 선택` }));
    fireEvent.click(screen.getByRole('button', { name: '8월 20일에 추가' }));
    fireEvent.click(screen.getByRole('button', { name: '날짜 더 선택하기' }));
    fireEvent.click(screen.getByRole('button', { name: '2026-08-21 등록 날짜 선택' }));
    expect(screen.getByText(/2일 선택됨/)).toBeInTheDocument();

    fireEvent.click(screen.getByRole('button', { name: '취소' }));

    expect(screen.queryByTestId('mobile-date-selection-toolbar')).not.toBeInTheDocument();
    const panel = screen.getByRole('dialog', { name: '캘린더 등록 패널' });
    expect(within(panel).getByText('8/20 (목)')).toBeInTheDocument();
    expect(within(panel).queryByText('8/21 (금)')).not.toBeInTheDocument();
  });
});

describe('CalendarPage 키보드 월 이동', () => {
  beforeEach(() => {
    vi.useFakeTimers();
    vi.setSystemTime(new Date('2026-08-20T12:00:00+09:00'));
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

  afterEach(() => {
    vi.useRealTimers();
    window.matchMedia = REAL_MATCH_MEDIA;
  });

  it('캘린더에 포커스된 상태에서 방향키로 이전·다음 달을 이동한다', () => {
    renderPage();

    const surface = screen.getByTestId('calendar-touch-surface');
    expect(screen.getByText('2026년 8월')).toBeInTheDocument();

    fireEvent.keyDown(surface, { key: 'ArrowRight' });
    expect(screen.getByText('2026년 9월')).toBeInTheDocument();

    fireEvent.keyDown(surface, { key: 'ArrowLeft' });
    expect(screen.getByText('2026년 8월')).toBeInTheDocument();
  });

  it('PageUp·PageDown 키도 방향키와 같은 방식으로 월을 이동한다', () => {
    renderPage();

    const surface = screen.getByTestId('calendar-touch-surface');

    fireEvent.keyDown(surface, { key: 'PageDown' });
    expect(screen.getByText('2026년 9월')).toBeInTheDocument();

    fireEvent.keyDown(surface, { key: 'PageUp' });
    expect(screen.getByText('2026년 8월')).toBeInTheDocument();
  });

  it('Home 키를 누르면 오늘이 있는 달로 돌아온다', () => {
    renderPage();

    const surface = screen.getByTestId('calendar-touch-surface');
    fireEvent.keyDown(surface, { key: 'ArrowRight' });
    fireEvent.keyDown(surface, { key: 'ArrowRight' });
    expect(screen.getByText('2026년 10월')).toBeInTheDocument();

    fireEvent.keyDown(surface, { key: 'Home' });
    expect(screen.getByText('2026년 8월')).toBeInTheDocument();
  });

  it('사유 입력 등에 포커스가 있을 때는 방향키로 월이 바뀌지 않는다', () => {
    renderPage();

    const searchInput = screen.getByLabelText('이름으로 일정 검색');
    fireEvent.keyDown(searchInput, { key: 'ArrowRight' });
    expect(screen.getByText('2026년 8월')).toBeInTheDocument();

    fireEvent.keyDown(searchInput, { key: 'ArrowLeft' });
    expect(screen.getByText('2026년 8월')).toBeInTheDocument();
  });

  it('Ctrl 등 조합키가 눌린 상태에서는 월이 바뀌지 않는다', () => {
    renderPage();

    const surface = screen.getByTestId('calendar-touch-surface');
    fireEvent.keyDown(surface, { key: 'ArrowRight', ctrlKey: true });
    expect(screen.getByText('2026년 8월')).toBeInTheDocument();
  });

  it('월 표시 요소는 aria-live 영역이라 월이 바뀌면 스크린 리더가 읽는다', () => {
    renderPage();

    const monthLabel = screen.getByText('2026년 8월');
    expect(monthLabel).toHaveAttribute('aria-live', 'polite');
  });
});
