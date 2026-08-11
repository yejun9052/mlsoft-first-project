import { beforeEach, describe, expect, it, vi } from 'vitest';
import { act, renderHook, waitFor } from '@testing-library/react';
import {
  QueryClient,
  QueryClientProvider,
} from '@tanstack/react-query';
import {
  useCreateWelfarePolicy,
  usePendingWelfareApprovals,
  useProcessWelfareApproval,
} from './useWelfare.js';
import {
  createWelfarePolicy,
  getPendingWelfareApprovals,
  processWelfareApproval,
} from '../api/welfare.js';

vi.mock('../api/welfare.js', () => ({
  applyWelfare: vi.fn(),
  cancelWelfareRequest: vi.fn(),
  createWelfarePolicy: vi.fn(),
  deactivateWelfarePolicy: vi.fn(),
  getAllWelfarePolicies: vi.fn(),
  getMyWelfareRequests: vi.fn(),
  getPendingWelfareApprovals: vi.fn(),
  getWelfarePolicies: vi.fn(),
  processWelfareApproval: vi.fn(),
  updateWelfarePolicy: vi.fn(),
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

describe('useWelfare 캐시 키와 무효화', () => {
  it('page가 같아도 size가 다르면 대기 목록을 별도 조회한다', async () => {
    getPendingWelfareApprovals.mockResolvedValue({
      content: [],
      page: { totalElements: 0 },
    });
    const { Wrapper } = createHarness();

    const first = renderHook(
      () => usePendingWelfareApprovals({ page: 0, size: 1 }),
      { wrapper: Wrapper },
    );
    const second = renderHook(
      () => usePendingWelfareApprovals({ page: 0, size: 50 }),
      { wrapper: Wrapper },
    );

    await waitFor(() => expect(first.result.current.isSuccess).toBe(true));
    await waitFor(() => expect(second.result.current.isSuccess).toBe(true));

    expect(getPendingWelfareApprovals).toHaveBeenCalledWith({
      page: 0,
      size: 1,
    });
    expect(getPendingWelfareApprovals).toHaveBeenCalledWith({
      page: 0,
      size: 50,
    });
    expect(getPendingWelfareApprovals).toHaveBeenCalledTimes(2);
  });

  it('복리후생 승인 성공 시 복리·이력·연차 쿼리를 모두 무효화한다', async () => {
    processWelfareApproval.mockResolvedValue({ id: 20 });
    const { queryClient, Wrapper } = createHarness();
    const invalidateSpy = vi.spyOn(queryClient, 'invalidateQueries');

    const { result } = renderHook(() => useProcessWelfareApproval(), {
      wrapper: Wrapper,
    });

    await act(async () => {
      await result.current.mutateAsync({
        id: 20,
        approved: true,
        comment: '승인',
      });
    });

    expect(processWelfareApproval).toHaveBeenCalledWith(20, {
      approved: true,
      comment: '승인',
    });
    expect(invalidateSpy).toHaveBeenCalledWith({ queryKey: ['welfare'] });
    expect(invalidateSpy).toHaveBeenCalledWith({
      queryKey: ['histories', 'welfare'],
    });
    expect(invalidateSpy).toHaveBeenCalledWith({ queryKey: ['leaves'] });
  });

  it('정책 생성은 연차 잔액을 바꾸지 않으므로 leaves를 무효화하지 않는다', async () => {
    createWelfarePolicy.mockResolvedValue({ id: 30 });
    const { queryClient, Wrapper } = createHarness();
    const invalidateSpy = vi.spyOn(queryClient, 'invalidateQueries');

    const body = {
      category: '출산',
      target: 'SELF',
      defaultDays: '3.0',
      defaultEvidence: '출생증명서',
      description: '출산 복리후생',
    };

    const { result } = renderHook(() => useCreateWelfarePolicy(), {
      wrapper: Wrapper,
    });

    await act(async () => {
      await result.current.mutateAsync(body);
    });

    expect(createWelfarePolicy).toHaveBeenCalledWith(
      body,
      expect.objectContaining({ client: queryClient }),
    );
    expect(invalidateSpy).toHaveBeenCalledWith({ queryKey: ['welfare'] });
    expect(invalidateSpy).toHaveBeenCalledWith({
      queryKey: ['histories', 'welfare'],
    });
    expect(invalidateSpy).not.toHaveBeenCalledWith({
      queryKey: ['leaves'],
    });
  });
});
