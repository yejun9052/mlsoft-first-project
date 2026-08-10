import { useEffect } from 'react';
import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query';
import { approveOnboarding, getPendingOnboardings, me, rejectOnboarding } from '../api/auth.js';

// localStorage의 로그인 정보 — 손상된 값은 null로 떨어뜨린다
export function readStoredUserInfo() {
  try {
    return JSON.parse(localStorage.getItem('userInfo')) ?? null;
  } catch {
    return null;
  }
}

/**
 * 로그인 유저 정보 — localStorage(OAuth 콜백/온보딩 시 저장된 UserMeResponse)를 initialData로 즉시
 * 렌더하고, 백그라운드에서 GET /api/auth/me로 최신값을 재검증한다.
 *
 * **이것이 권한의 단일 출처다** (리뷰 F-7). 서버는 OnboardingCheckInterceptor가 매 요청 DB role로
 * SecurityContext를 재구성해 강등·승격을 즉시 반영하는데, 화면이 localStorage를 직접 읽으면
 * 재로그인 전까지 옛 권한으로 렌더된다 — 강등된 사람에게 관리자 메뉴가 계속 보이고(누르면 403),
 * 승격된 사람은 메뉴가 나타나지 않았다. 컴포넌트는 localStorage를 직접 읽지 말고 이 훅을 쓴다.
 */
export function useCurrentUser() {
  const query = useQuery({
    queryKey: ['auth', 'me'],
    queryFn: me,
    initialData: () => readStoredUserInfo() ?? undefined,
    // **initialDataUpdatedAt: 0이 없으면 재검증이 아예 돌지 않는다.** react-query는 시각을
    // 주지 않은 initialData를 "지금 받은 값"으로 취급하므로 staleTime 안에서는 fresh로 판정해
    // 마운트 시 fetch를 건너뛴다 — 저장값이 낡아 있어도 그대로 쓴다는 뜻이다.
    // 0을 주면 "화면에는 즉시 저장값, 뒤로는 곧바로 재검증"이 된다. F-7이 여기 걸려 있다.
    initialDataUpdatedAt: 0,
    staleTime: 60 * 1000,
  });

  // 서버 값이 갱신되면 localStorage도 맞춘다 — 새로고침 직후 첫 렌더은 아직 응답이 없어
  // 저장값으로 부트스트랩되므로, 저장값이 낡아 있으면 그 한 프레임이 옛 권한으로 그려진다.
  useEffect(() => {
    if (query.data) {
      localStorage.setItem('userInfo', JSON.stringify(query.data));
    }
  }, [query.data]);

  return query;
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
