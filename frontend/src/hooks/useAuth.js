import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query';
import { approveOnboarding, getPendingOnboardings, me, rejectOnboarding } from '../api/auth.js';

// 로그인 유저 정보 — localStorage(OAuth 콜백/온보딩 시 저장된 UserMeResponse)를 initialData로 즉시
// 렌더하고, 백그라운드에서 GET /api/auth/me로 최신값을 재검증한다.
export function useCurrentUser() {
  return useQuery({
    queryKey: ['auth', 'me'],
    queryFn: me,
    initialData: () => {
      try {
        return JSON.parse(localStorage.getItem('userInfo')) ?? undefined;
      } catch {
        return undefined;
      }
    },
    staleTime: 60 * 1000,
  });
}

// 온보딩 승인 대기 목록 (GET /api/admin/onboardings, SYSTEM_ADMIN 전용) — 리뷰 S-1.
// 쿼리 키에 size를 포함한다 (리뷰 F-1) — 같은 page라도 size가 다르면 다른 결과다.
export function usePendingOnboardings({ page = 0, size = 20, enabled = true } = {}) {
  return useQuery({
    queryKey: ['auth', 'onboardings', page, size],
    queryFn: () => getPendingOnboardings({ page, size }),
    enabled,
  });
}

// 승인 — 그 사원의 연차가 이 시점에 부여되므로 구성원 목록도 함께 낡는다 (리뷰 F-5)
export function useApproveOnboarding() {
  const queryClient = useQueryClient();
  return useMutation({
    mutationFn: approveOnboarding,
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: ['auth', 'onboardings'] });
      queryClient.invalidateQueries({ queryKey: ['users'] });
    },
  });
}

// 반려 — 사원이 NOT_STARTED로 돌아가 다시 낼 수 있게 된다
export function useRejectOnboarding() {
  const queryClient = useQueryClient();
  return useMutation({
    mutationFn: rejectOnboarding,
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: ['auth', 'onboardings'] });
      queryClient.invalidateQueries({ queryKey: ['users'] });
    },
  });
}
