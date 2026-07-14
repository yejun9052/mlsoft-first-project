import { QueryClient } from '@tanstack/react-query';

// 전역 QueryClient — 연차 등 서버 상태 캐싱 정책.
// staleTime 30초: 그 안에는 재방문해도 네트워크 재요청 없이 캐시를 바로 보여줌.
// axios 인터셉터가 401을 이미 처리(로그인 리다이렉트)하므로 여기선 추가 재시도만 1회로 제한.
export const queryClient = new QueryClient({
  defaultOptions: {
    queries: {
      staleTime: 30 * 1000,
      retry: 1,
    },
  },
});
