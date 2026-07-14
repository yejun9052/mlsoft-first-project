import { useQuery } from '@tanstack/react-query';
import { me } from '../api/auth.js';

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
