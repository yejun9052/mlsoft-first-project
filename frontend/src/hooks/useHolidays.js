import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query';
import { getHolidays, syncHolidays } from '../api/holidays.js';

// 쿼리 키 규칙: ['holidays', 연도]
const holidayKeys = {
  byYear: (year) => ['holidays', year],
};

/**
 * 연도별 공휴일 (GET /api/holidays).
 * 한 해 동안 바뀌지 않는 데이터라 오래 캐시한다 — 서버도 DB 캐시로 받쳐 두고 있어
 * 이 쿼리가 외부 API를 반복 호출하는 일은 없다.
 *
 * 조회에 실패해도 화면은 그려져야 한다(공휴일 표시가 빠질 뿐) — 호출부는 `data ?? []`로 쓴다.
 */
export function useHolidays(year) {
  return useQuery({
    queryKey: holidayKeys.byYear(year),
    queryFn: () => getHolidays({ year }),
    staleTime: 1000 * 60 * 60 * 12,
    enabled: Boolean(year),
  });
}

// 강제 재동기화 (SA) — 대체공휴일이 뒤늦게 지정된 경우 등
export function useSyncHolidays() {
  const queryClient = useQueryClient();
  return useMutation({
    mutationFn: syncHolidays,
    onSuccess: () => queryClient.invalidateQueries({ queryKey: ['holidays'] }),
  });
}
