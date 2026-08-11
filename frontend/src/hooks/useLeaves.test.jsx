import { beforeEach, describe, expect, it, vi } from 'vitest';
import { act, renderHook, waitFor } from '@testing-library/react';
import {
  QueryClient,
  QueryClientProvider,
} from '@tanstack/react-query';
import {
  usePendingApprovals,
  useProcessApproval,
  useProcessCancelApproval,
} from './useLeaves.js';
import {
  getPendingApprovals,
  processApproval,
  processCancelApproval,
} from '../api/leaves.js';

vi.mock('../api/leaves.js', () => ({
  applyLeave: vi.fn(),
  cancelLeave: vi.fn(),
  getAllLeaves: vi.fn(),
  getCalendar: vi.fn(),
  getMyLeaves: vi.fn(),
  getMySummary: vi.fn(),
  getPendingApprovals: vi.fn(),
  getTeamLeaves: vi.fn(),
  processApproval: vi.fn(),
  processCancelApproval: vi.fn(),
}));

function createHarness() {
  const queryClient = new QueryClient({
    defaultOptions: {
      queries: { retry: false },
      mutations: { retry: false },
    },
  });

  function Wrapper({ children }) {
    return (
      <QueryClientProvider client={queryClient}>
        {children}
      </QueryClientProvider>
    );
  }

  return { queryClient, Wrapper };
}

beforeEach(() => {
  vi.clearAllMocks();
});

describe('useLeaves 캐시 키와 무효화', () => {
  it('page가 같아도 size가 다르면 대기 목록을 별도 조회한다', async () => {
    getPendingApprovals.mockResolvedValue({
      content: [],
      page: { totalElements: 0 },
    });
    const { Wrapper } = createHarness();

    const first = renderHook(
      () => usePendingApprovals({ page: 0, size: 1 }),
      { wrapper: Wrapper },
    );
    const second = renderHook(
      () => usePendingApprovals({ page: 0, size: 50 }),
      { wrapper: Wrapper },
    );

    await waitFor(() => expect(first.result.current.isSuccess).toBe(true));
    await waitFor(() => expect(second.result.current.isSuccess).toBe(true));

    expect(getPendingApprovals).toHaveBeenCalledWith({ page: 0, size: 1 });
    expect(getPendingApprovals).toHaveBeenCalledWith({ page: 0, size: 50 });
    expect(getPendingApprovals).toHaveBeenCalledTimes(2);
  });

  it('일반 연차 승인 성공 시 연차와 연차 이력을 무효화한다', async () => {
    processApproval.mockResolvedValue({ id: 10 });
    const { queryClient, Wrapper } = createHarness();
    const invalidateSpy = vi.spyOn(queryClient, 'invalidateQueries');

    const { result } = renderHook(() => useProcessApproval(), {
      wrapper: Wrapper,
    });

    await act(async () => {
      await result.current.mutateAsync({
        id: 10,
        approved: true,
        comment: '승인',
      });
    });

    expect(processApproval).toHaveBeenCalledWith(10, {
      approved: true,
      comment: '승인',
    });
    expect(invalidateSpy).toHaveBeenCalledWith({ queryKey: ['leaves'] });
    expect(invalidateSpy).toHaveBeenCalledWith({
      queryKey: ['histories', 'leave'],
    });
  });

  it('소급 취소 처리 성공 시 연차와 연차 이력을 무효화한다', async () => {
    processCancelApproval.mockResolvedValue({ id: 12 });
    const { queryClient, Wrapper } = createHarness();
    const invalidateSpy = vi.spyOn(queryClient, 'invalidateQueries');

    const { result } = renderHook(() => useProcessCancelApproval(), {
      wrapper: Wrapper,
    });

    await act(async () => {
      await result.current.mutateAsync({
        id: 12,
        approved: false,
        comment: '증빙 부족',
      });
    });

    expect(processCancelApproval).toHaveBeenCalledWith(12, {
      approved: false,
      comment: '증빙 부족',
    });
    expect(invalidateSpy).toHaveBeenCalledWith({ queryKey: ['leaves'] });
    expect(invalidateSpy).toHaveBeenCalledWith({
      queryKey: ['histories', 'leave'],
    });
  });
});
