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
});
