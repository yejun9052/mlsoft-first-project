import { keepPreviousData, useQuery } from '@tanstack/react-query';
import {
  getLeaveHistories,
  getMyTeamLeaveHistories,
  getMyTeamWelfareHistories,
  getWelfareHistories,
} from '../api/histories.js';

// 쿼리 키 규칙: ['histories', 종류, 스코프, action, page].
// 페이지를 넘길 때마다 그 페이지만 서버에서 가져오므로 page가 키에 포함된다 — 넘긴 페이지는 캐시에 남아
// 되돌아올 때 즉시 뜬다.
const historyKeys = {
  leave: (scope, action, page) => ['histories', 'leave', scope, action || 'ALL', page],
  welfare: (scope, action, page) => ['histories', 'welfare', scope, action || 'ALL', page],
};

// 스코프('all' = 전사, SA 전용 / 'my-team' = 내 팀, TL·SA)에 따라 호출할 엔드포인트를 고른다.
const LEAVE_FETCHER = { all: getLeaveHistories, 'my-team': getMyTeamLeaveHistories };
const WELFARE_FETCHER = { all: getWelfareHistories, 'my-team': getMyTeamWelfareHistories };

// 페이지 전환 시 목록이 빈 화면으로 깜박이지 않게 이전 페이지 데이터를 유지한다(react-query v5).
const PAGED_OPTIONS = { placeholderData: keepPreviousData };

// 연차 처리 로그 (GET /api/leave-histories[/my-team])
export function useLeaveHistories({ scope = 'all', action, page = 0, size = 20, enabled = true } = {}) {
  return useQuery({
    queryKey: historyKeys.leave(scope, action, page),
    queryFn: () => LEAVE_FETCHER[scope]({ action, page, size }),
    enabled,
    ...PAGED_OPTIONS,
  });
}

// 복리후생 처리 로그 (GET /api/welfare-histories[/my-team])
export function useWelfareHistories({ scope = 'all', action, page = 0, size = 20, enabled = true } = {}) {
  return useQuery({
    queryKey: historyKeys.welfare(scope, action, page),
    queryFn: () => WELFARE_FETCHER[scope]({ action, page, size }),
    enabled,
    ...PAGED_OPTIONS,
  });
}
