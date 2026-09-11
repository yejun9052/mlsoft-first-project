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

const REAL_MATCH_MEDIA = window.matchMedia;

describe('CalendarPage multi-day event bars', () => {
  beforeEach(() => {
    vi.useFakeTimers();
    vi.setSystemTime(new Date('2026-08-24T12:00:00+09:00'));
    useCurrentUser.mockReturnValue({ data: { id: 1 } });
    useLeaveSummary.mockReturnValue({ data: { remainingDays: '10.0' } });
    useLeaveCalendar.mockReturnValue({ data: [] });
    useScheduleCalendar.mockReturnValue({
      data: [
        {
          id: 20,
          userId: 1,
          userName: 'Alice',
          scheduleType: 'BUSINESS_TRIP',
          typeLabel: 'Trip',
          dates: ['2026-08-20', '2026-08-21', '2026-08-22'],
          memo: 'Three-day trip',
        },
      ],
    });
    useDepartments.mockReturnValue({ data: [] });
    useHolidays.mockReturnValue({ data: [] });
  });

  afterEach(() => {
    vi.useRealTimers();
    window.matchMedia = REAL_MATCH_MEDIA;
  });

  it('renders one continuous bar instead of one pill per date', () => {
    const router = createMemoryRouter(
      [{ path: '/calendar', element: <CalendarPage /> }],
      { initialEntries: ['/calendar'] },
    );

    render(<RouterProvider router={router} />);

    expect(
      screen.getByRole('img', { name: /Alice · Trip 2026-08-20 ~ 2026-08-22/ }),
    ).toBeInTheDocument();
    expect(screen.getAllByText('Alice · Trip')).toHaveLength(1);
    expect(screen.queryAllByText('Alice', { exact: true })).toHaveLength(0);
    expect(screen.queryByText('...더보기')).not.toBeInTheDocument();
  });

  it('keeps the detail entry point when overlapping bars exceed the visible lanes', () => {
    useScheduleCalendar.mockReturnValue({
      data: [1, 2, 3, 4].map((id) => ({
        id,
        userId: id,
        userName: 'User ' + id,
        scheduleType: 'BUSINESS_TRIP',
        typeLabel: 'Trip',
        dates: ['2026-08-20', '2026-08-21'],
        memo: 'Trip ' + id,
      })),
    });

    const router = createMemoryRouter(
      [{ path: '/calendar', element: <CalendarPage /> }],
      { initialEntries: ['/calendar'] },
    );
    render(<RouterProvider router={router} />);

    const detailButtons = screen.getAllByText('...더보기');
    expect(detailButtons.length).toBeGreaterThan(0);
    fireEvent.click(detailButtons[0]);

    const dialog = screen.getByRole('dialog');
    expect(within(dialog).getByText('User 1')).toBeInTheDocument();
    expect(within(dialog).getByText('User 4')).toBeInTheDocument();
  });

  it('does not bridge non-consecutive dates into one bar', () => {
    useScheduleCalendar.mockReturnValue({
      data: [
        {
          id: 30,
          userId: 30,
          userName: 'Gap',
          scheduleType: 'BUSINESS_TRIP',
          typeLabel: 'Trip',
          dates: ['2026-08-20', '2026-08-22'],
          memo: 'Separated dates',
        },
      ],
    });

    const router = createMemoryRouter(
      [{ path: '/calendar', element: <CalendarPage /> }],
      { initialEntries: ['/calendar'] },
    );
    render(<RouterProvider router={router} />);

    expect(screen.queryByRole('img')).not.toBeInTheDocument();
    expect(screen.getAllByText('Gap')).toHaveLength(2);
  });

  it('keeps a continuation bar when only one date is visible in the month', () => {
    useScheduleCalendar.mockReturnValue({
      data: [
        {
          id: 40,
          userId: 40,
          userName: 'Boundary',
          scheduleType: 'BUSINESS_TRIP',
          typeLabel: 'Trip',
          dates: ['2026-07-31', '2026-08-01'],
          memo: 'Month boundary',
        },
      ],
    });

    const router = createMemoryRouter(
      [{ path: '/calendar', element: <CalendarPage /> }],
      { initialEntries: ['/calendar'] },
    );
    render(<RouterProvider router={router} />);

    expect(
      screen.getByRole('img', {
        name: /Boundary · Trip 2026-07-31 ~ 2026-08-01/,
      }),
    ).toBeInTheDocument();
  });

  it('moves to the next month after a horizontal swipe', () => {
    const router = createMemoryRouter(
      [{ path: '/calendar', element: <CalendarPage /> }],
      { initialEntries: ['/calendar'] },
    );
    render(<RouterProvider router={router} />);

    const surface = screen.getByTestId('calendar-touch-surface');
    fireEvent.touchStart(surface, {
      touches: [{ clientX: 220, clientY: 100 }],
    });
    fireEvent.touchEnd(surface, {
      changedTouches: [{ clientX: 80, clientY: 100 }],
    });

    expect(screen.getByText('2026년 9월')).toBeInTheDocument();
  });

  it('moves to the previous month on a right swipe and ignores a vertical gesture', () => {
    const router = createMemoryRouter(
      [{ path: '/calendar', element: <CalendarPage /> }],
      { initialEntries: ['/calendar'] },
    );
    render(<RouterProvider router={router} />);

    const surface = screen.getByTestId('calendar-touch-surface');
    fireEvent.touchStart(surface, {
      touches: [{ clientX: 80, clientY: 100 }],
    });
    fireEvent.touchEnd(surface, {
      changedTouches: [{ clientX: 220, clientY: 100 }],
    });
    expect(screen.getByText('2026년 7월')).toBeInTheDocument();

    fireEvent.touchStart(surface, {
      touches: [{ clientX: 120, clientY: 100 }],
    });
    fireEvent.touchEnd(surface, {
      changedTouches: [{ clientX: 150, clientY: 220 }],
    });
    expect(screen.getByText('2026년 7월')).toBeInTheDocument();
  });

  it('draws consecutive holidays with the same name as one bar', () => {
    useHolidays.mockReturnValue({
      data: [
        { date: '2026-08-24', name: 'Chuseok' },
        { date: '2026-08-25', name: 'Chuseok' },
        { date: '2026-08-26', name: 'Chuseok' },
        { date: '2026-08-28', name: 'Single holiday' },
      ],
    });
    const router = createMemoryRouter(
      [{ path: '/calendar', element: <CalendarPage /> }],
      { initialEntries: ['/calendar'] },
    );

    render(<RouterProvider router={router} />);

    // 이름이 같은 연속 3일은 막대 하나, 그 날짜 셀에는 pill을 따로 그리지 않는다
    expect(screen.getByRole('img', { name: /Chuseok 2026-08-24 ~ 2026-08-26/ })).toBeInTheDocument();
    expect(screen.getAllByText('Chuseok')).toHaveLength(1);
    // 하루짜리 공휴일은 그대로 셀 안 pill
    expect(screen.queryByRole('img', { name: /Single holiday/ })).not.toBeInTheDocument();
    expect(screen.getByText('Single holiday').closest('.calendar-entry-pill')).not.toBeNull();
  });

  it('keeps the pill inset on free ends and touches the wall only where a bar continues', () => {
    useScheduleCalendar.mockReturnValue({
      data: [
        {
          id: 21,
          userId: 1,
          userName: 'Alice',
          scheduleType: 'BUSINESS_TRIP',
          typeLabel: 'Trip',
          // 2026-08-29(토) → 08-30(일)로 주가 바뀐다
          dates: ['2026-08-28', '2026-08-29', '2026-08-30'],
          memo: null,
        },
      ],
    });
    const router = createMemoryRouter(
      [{ path: '/calendar', element: <CalendarPage /> }],
      { initialEntries: ['/calendar'] },
    );

    render(<RouterProvider router={router} />);

    const bars = screen.getAllByRole('img', { name: /Alice · Trip 2026-08-28 ~ 2026-08-30/ });
    expect(bars).toHaveLength(2);
    const [first, second] = bars;
    // 첫 주 조각: 시작은 pill처럼 둥글고 안쪽 여백, 끝은 다음 주로 이어지므로 벽에 붙는다
    expect(first.className).toContain('rounded-l');
    expect(first.className).toContain('calendar-span-bar-continues-after');
    expect(first.className).not.toContain('calendar-span-bar-continues-before');
    // 둘째 주 조각: 시작이 벽에 붙고 끝은 둥글다
    expect(second.className).toContain('calendar-span-bar-continues-before');
    expect(second.className).toContain('rounded-r');
    // 막대 높이는 단일 pill과 같은 26px
    expect(first.style.height).toBe('26px');
  });

  it('renders a holiday as the same calendar entry type in the mobile day summary', () => {
    window.matchMedia = vi.fn().mockImplementation(() => ({
      matches: true,
      addEventListener: vi.fn(),
      removeEventListener: vi.fn(),
    }));
    useHolidays.mockReturnValue({
      data: [{ date: '2026-08-20', name: 'Test holiday' }],
    });

    const router = createMemoryRouter(
      [{ path: '/calendar', element: <CalendarPage /> }],
      { initialEntries: ['/calendar'] },
    );
    render(<RouterProvider router={router} />);

    fireEvent.click(screen.getByRole('button', { name: /2026-08-20/ }));

    const holidayCell = screen.getByRole('button', { name: /2026-08-20/ });
    expect(holidayCell.querySelectorAll('.mobile-calendar-entry-dot')).toHaveLength(1);

    const summary = screen.getByTestId('mobile-calendar-day-summary');
    expect(within(summary).getByText('Test holiday')).toBeInTheDocument();
    expect(within(summary).getByText('Test holiday').closest('article')).toBeInTheDocument();
  });
});
