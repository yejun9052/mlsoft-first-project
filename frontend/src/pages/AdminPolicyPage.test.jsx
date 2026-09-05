import { beforeEach, describe, expect, it, vi } from 'vitest';
import { fireEvent, render, screen } from '@testing-library/react';
import { createMemoryRouter, RouterProvider } from 'react-router-dom';
import AdminPolicyPage from './AdminPolicyPage.jsx';
import {
  useLeavePolicies,
  useLeavePolicyConfigs,
  useResetHistories,
  useUpdateLeavePolicy,
  useUpdateLeavePolicyConfig,
} from '../hooks/usePolicies.js';
import { useHolidays, useSyncHolidays } from '../hooks/useHolidays.js';

vi.mock('react-hot-toast', () => ({
  default: { success: vi.fn(), error: vi.fn() },
}));

vi.mock('../hooks/usePolicies.js', () => ({
  useLeavePolicies: vi.fn(),
  useLeavePolicyConfigs: vi.fn(),
  useResetHistories: vi.fn(),
  useUpdateLeavePolicy: vi.fn(),
  useUpdateLeavePolicyConfig: vi.fn(),
}));

vi.mock('../hooks/useHolidays.js', () => ({
  useHolidays: vi.fn(),
  useSyncHolidays: vi.fn(),
}));

const CURRENT_YEAR = new Date().getFullYear();
const syncMutate = vi.fn();

function query(data) {
  return {
    data,
    isLoading: false,
    isError: false,
    refetch: vi.fn(),
  };
}

function renderPage() {
  useLeavePolicies.mockReturnValue(query([]));
  useLeavePolicyConfigs.mockReturnValue(query([]));
  useResetHistories.mockReturnValue(
    query({ content: [], page: { totalPages: 1, totalElements: 0 } }),
  );
  useUpdateLeavePolicy.mockReturnValue({ mutate: vi.fn(), isPending: false });
  useUpdateLeavePolicyConfig.mockReturnValue({ mutateAsync: vi.fn(), isPending: false });
  useHolidays.mockImplementation((year) =>
    query(year === CURRENT_YEAR ? [{ date: `${CURRENT_YEAR}-01-01`, name: '신정' }] : []),
  );
  useSyncHolidays.mockReturnValue({ mutate: syncMutate, isPending: false });

  const router = createMemoryRouter(
    [{ path: '/', element: <AdminPolicyPage /> }],
    { initialEntries: ['/'] },
  );
  return render(<RouterProvider router={router} />);
}

beforeEach(() => {
  vi.clearAllMocks();
  syncMutate.mockReset();
});

describe('AdminPolicyPage 공휴일 동기화', () => {
  it('공휴일 섹션과 현재 연도 적재 건수를 보여준다', () => {
    renderPage();

    expect(screen.getByRole('heading', { name: '공휴일' })).toBeInTheDocument();
    expect(screen.getByLabelText('공휴일 조회 연도')).toHaveValue(String(CURRENT_YEAR));
    expect(screen.getByText('현재 적재 건수')).toBeInTheDocument();
    expect(screen.getByText('1건')).toBeInTheDocument();
  });

  it('선택한 연도로 동기화 API를 호출한다', () => {
    renderPage();

    const targetYear = CURRENT_YEAR + 1;
    fireEvent.change(screen.getByLabelText('공휴일 조회 연도'), {
      target: { value: String(targetYear) },
    });
    fireEvent.click(screen.getByRole('button', { name: '동기화' }));

    expect(syncMutate).toHaveBeenCalledWith(
      { year: targetYear },
      expect.objectContaining({ onSuccess: expect.any(Function) }),
    );
  });

  it('동기화 성공 결과의 적재 건수를 표시한다', () => {
    syncMutate.mockImplementation((_variables, options) => {
      options.onSuccess({ year: CURRENT_YEAR, count: 22 });
    });
    renderPage();

    fireEvent.click(screen.getByRole('button', { name: '동기화' }));

    expect(screen.getByText('22건 적재')).toBeInTheDocument();
  });
});
