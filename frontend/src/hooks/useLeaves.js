import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query';
import {
  applyLeave,
  cancelLeave,
  getAllLeaves,
  getCalendar,
  getMyAnnualUsage,
  getMyLeaves,
  getMySummary,
  getPendingApprovals,
  getTeamAnnualUsage,
  getTeamLeaves,
  processApproval,
  processCancelApproval,
} from '../api/leaves.js';

// 쿼리 키 규칙: ['leaves', 서브리소스, ...파라미터]. 접두사(['leaves'])로 invalidate하면
// summary/me/calendar/pending 등 연차 관련 쿼리가 한 번에 무효화된다 (아래 useApplyLeave 등 참고).
//
// **서버 응답을 바꾸는 파라미터는 전부 키에 넣는다** (리뷰 F-1). size가 빠져 있어서
// 사이드바의 usePendingApprovals({size:1})와 결재 화면의 size=50이 같은 키를 공유했고,
// 먼저 캐시된 응답이 재사용되어 결재 목록이 1건만 보일 수 있었다.
const leaveKeys = {
  summary: ['leaves', 'summary'],
  me: (status, page, size) => ['leaves', 'me', status ?? 'ALL', page, size],
  // 검색 필터도 서버 응답을 바꾸므로 키에 넣는다 (리뷰 F-1과 같은 이유)
  calendar: (year, month, keyword, departmentId) =>
    ['leaves', 'calendar', year, month, keyword || 'ALL', departmentId || 'ALL'],
  team: (from, to) => ['leaves', 'team', from ?? 'default', to ?? 'default'],
  myAnnualUsage: (year) => ['leaves', 'me', 'annual-usage', year],
  teamAnnualUsage: (year) => ['leaves', 'team', 'annual-usage', year],
  pending: (page, size) => ['leaves', 'pending', page, size],
  allCount: (status) => ['leaves', 'all-count', status],
};

// 연차 상태가 바뀌면 처리 이력 로그(['histories','leave'])도 함께 낡는다 — 관리자 이력 화면이나
// 결재 완료 탭이 열려 있으면 이전 목록이 그대로 남는다 (리뷰 F-5).
function invalidateLeaveAndHistories(queryClient) {
  queryClient.invalidateQueries({ queryKey: ['leaves'] });
  queryClient.invalidateQueries({ queryKey: ['histories', 'leave'] });
}

// 잔여 연차 요약 (GET /api/leaves/me/summary)
export function useLeaveSummary() {
  return useQuery({ queryKey: leaveKeys.summary, queryFn: getMySummary });
}

// 내 신청 내역 (GET /api/leaves/me)
export function useMyLeaves({ status, page = 0, size = 100 } = {}) {
  return useQuery({
    queryKey: leaveKeys.me(status, page, size),
    queryFn: () => getMyLeaves({ status, page, size }),
  });
}

// 캘린더용 승인 연차 (GET /api/leaves/calendar) — keyword·departmentId는 선택 필터.
// enabled는 대시보드가 월말에만 다음 달을 덧붙여 조회하려고 쓴다 (평소에는 요청이 나가지 않는다).
export function useLeaveCalendar(year, month, { keyword, departmentId, enabled = true } = {}) {
  return useQuery({
    queryKey: leaveKeys.calendar(year, month, keyword, departmentId),
    queryFn: () => getCalendar({ year, month, keyword, departmentId }),
    // 검색어를 타이핑하는 동안 목록이 빈 화면으로 깜박이지 않게 이전 결과를 유지한다
    placeholderData: (previous) => previous,
    enabled,
  });
}

// 내 팀 연차 현황 — 기간 미지정 시 서버가 이번 달로 처리, 부서 미배정이면 빈 배열 (GET /api/leaves/team)
export function useTeamLeaves({ from, to } = {}) {
  return useQuery({
    queryKey: leaveKeys.team(from, to),
    queryFn: () => getTeamLeaves({ from, to }),
  });
}

// 개인 히트맵 — 승인 완료 날짜별 사용량
export function useMyAnnualUsage(year) {
  return useQuery({
    queryKey: leaveKeys.myAnnualUsage(year),
    queryFn: () => getMyAnnualUsage({ year }),
    enabled: Boolean(year),
  });
}

// 팀 히트맵 — 현재 부서의 승인 완료 날짜별 인원 수
export function useTeamAnnualUsage(year) {
  return useQuery({
    queryKey: leaveKeys.teamAnnualUsage(year),
    queryFn: () => getTeamAnnualUsage({ year }),
    enabled: Boolean(year),
  });
}

// 내가 승인자인 대기 목록 (GET /api/leaves/pending, TEAM_LEADER·SYSTEM_ADMIN 전용)
// enabled로 막지 않으면 EMPLOYEE가 마운트된 컴포넌트(사이드바 등)에서 403 에러 toast가 뜬다.
export function usePendingApprovals({ page = 0, size = 50, enabled = true } = {}) {
  return useQuery({
    queryKey: leaveKeys.pending(page, size),
    queryFn: () => getPendingApprovals({ page, size }),
    enabled,
  });
}

// 회사 전체 기준 특정 상태 건수 (SYSTEM_ADMIN 전용, GET /api/leaves) — 목록 전체를 받을 필요 없이
// size=1로 요청해 페이지 메타(totalElements)만 읽는다. enabled로 관리자가 아닐 때는 아예 호출하지 않음
// (TEAM_LEADER가 부르면 403이라 인터셉터가 에러 toast를 띄우게 됨 — 반드시 enabled로 막을 것).
export function useAllLeavesCount(status, enabled = true) {
  return useQuery({
    queryKey: leaveKeys.allCount(status),
    queryFn: () => getAllLeaves({ status, page: 0, size: 1 }).then((page) => page.page?.totalElements ?? 0),
    enabled,
  });
}

// 연차 신청 뮤테이션 — 성공 시 잔여·내역·캘린더가 전부 바뀌므로 'leaves' 전체를 무효화한다.
// (이 앱 규모에서는 세밀하게 쪼개는 것보다 "연차가 바뀌면 연차 화면을 전부 다시 받아온다"는
//  단순한 규칙이 버그 낼 여지가 적다 — 화면이 늘면 그때 필요한 쿼리만 골라 무효화하도록 다듬기)
export function useApplyLeave() {
  const queryClient = useQueryClient();
  return useMutation({
    mutationFn: applyLeave,
    onSuccess: () => invalidateLeaveAndHistories(queryClient),
  });
}

// 승인/반려 뮤테이션 (POST /api/leaves/{id}/approval)
export function useProcessApproval() {
  const queryClient = useQueryClient();
  return useMutation({
    mutationFn: ({ id, approved, comment }) => processApproval(id, { approved, comment }),
    onSuccess: () => invalidateLeaveAndHistories(queryClient),
  });
}

// 소급 취소 승인/반려 뮤테이션 (POST /api/leaves/{id}/cancel-approval)
export function useProcessCancelApproval() {
  const queryClient = useQueryClient();
  return useMutation({
    mutationFn: ({ id, approved, comment }) => processCancelApproval(id, { approved, comment }),
    onSuccess: () => invalidateLeaveAndHistories(queryClient),
  });
}

// 연차 취소 뮤테이션 (POST /api/leaves/{id}/cancel) — 본인만.
// 서버가 결과 상태를 돌려준다: 미래 날짜만이면 CANCELLED(즉시 취소), 과거가 섞여 있으면
// CANCEL_PENDING(승인자 승인 대기). 호출부는 이 값으로 안내 문구를 갈라야 한다 (리뷰 F-3).
export function useCancelLeave() {
  const queryClient = useQueryClient();
  return useMutation({
    mutationFn: ({ id, reason }) => cancelLeave(id, { reason }),
    onSuccess: () => invalidateLeaveAndHistories(queryClient),
  });
}
