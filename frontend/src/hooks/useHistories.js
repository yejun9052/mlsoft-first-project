import { keepPreviousData, useQuery } from '@tanstack/react-query';
import {
  getLeaveHistories,
  getMyActionLeaveHistories,
  getMyActionWelfareHistories,
  getMyTeamLeaveHistories,
  getMyTeamWelfareHistories,
  getWelfareHistories,
} from '../api/histories.js';

// 쿼리 키 규칙: ['histories', 종류, 스코프, action, page, size].
// 페이지를 넘길 때마다 그 페이지만 서버에서 가져오므로 page가 키에 포함된다 — 넘긴 페이지는 캐시에 남아
// 되돌아올 때 즉시 뜬다.
// size도 키에 넣는다 — 서버 응답을 바꾸는 파라미터가 키에 없으면 크기가 다른 호출이 같은 캐시를
// 공유해 먼저 캐시된 응답이 그대로 재사용된다 (리뷰 F-1).
const historyKeys = {
  leave: (scope, action, page, size) => ['histories', 'leave', scope, action || 'ALL', page, size],
  welfare: (scope, action, page, size) => ['histories', 'welfare', scope, action || 'ALL', page, size],
};

// 스코프별 엔드포인트.
// all      = 전사 (SA 전용)
// my-team  = 내 팀 (TL·SA) — 신청자 부서 기준이라 남이 처리한 건도 포함된다
// my-actions = 내가 처리한 것 (로그인 전체) — actor가 본인인 이력만. 결재 완료 탭이 쓴다
const LEAVE_FETCHER = {
  all: getLeaveHistories,
  'my-team': getMyTeamLeaveHistories,
  'my-actions': getMyActionLeaveHistories,
};
const WELFARE_FETCHER = {
  all: getWelfareHistories,
  'my-team': getMyTeamWelfareHistories,
  'my-actions': getMyActionWelfareHistories,
};

// 페이지 전환 시 목록이 빈 화면으로 깜박이지 않게 이전 페이지 데이터를 유지한다(react-query v5).
const PAGED_OPTIONS = { placeholderData: keepPreviousData };

// 연차 처리 로그 (GET /api/leave-histories[/my-team|/my-actions])
export function useLeaveHistories({ scope = 'all', action, page = 0, size = 20, enabled = true } = {}) {
  return useQuery({
    queryKey: historyKeys.leave(scope, action, page, size),
    queryFn: () => LEAVE_FETCHER[scope]({ action, page, size }),
    enabled,
    ...PAGED_OPTIONS,
  });
}

// 복리후생 처리 로그 (GET /api/welfare-histories[/my-team|/my-actions])
export function useWelfareHistories({ scope = 'all', action, page = 0, size = 20, enabled = true } = {}) {
  return useQuery({
    queryKey: historyKeys.welfare(scope, action, page, size),
    queryFn: () => WELFARE_FETCHER[scope]({ action, page, size }),
    enabled,
    ...PAGED_OPTIONS,
  });
}
