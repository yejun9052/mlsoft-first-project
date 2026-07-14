import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query';
import {
  applyLeave,
  getAllLeaves,
  getCalendar,
  getMyLeaves,
  getMySummary,
  getPendingApprovals,
  processApproval,
  processCancelApproval,
} from '../api/leaves.js';

// 쿼리 키 규칙: ['leaves', 서브리소스, ...파라미터]. 접두사(['leaves'])로 invalidate하면
// summary/me/calendar/pending 등 연차 관련 쿼리가 한 번에 무효화된다 (아래 useApplyLeave 등 참고).
const leaveKeys = {
  summary: ['leaves', 'summary'],
  me: (status, page) => ['leaves', 'me', status ?? 'ALL', page],
  calendar: (year, month) => ['leaves', 'calendar', year, month],
  pending: (page) => ['leaves', 'pending', page],
  allCount: (status) => ['leaves', 'all-count', status],
};

// 잔여 연차 요약 (GET /api/leaves/me/summary)
export function useLeaveSummary() {
  return useQuery({ queryKey: leaveKeys.summary, queryFn: getMySummary });
}

// 내 신청 내역 (GET /api/leaves/me)
export function useMyLeaves({ status, page = 0, size = 100 } = {}) {
  return useQuery({
    queryKey: leaveKeys.me(status, page),
    queryFn: () => getMyLeaves({ status, page, size }),
  });
}

// 캘린더용 승인 연차 (GET /api/leaves/calendar)
export function useLeaveCalendar(year, month) {
  return useQuery({
    queryKey: leaveKeys.calendar(year, month),
    queryFn: () => getCalendar({ year, month }),
  });
}

// 내가 승인자인 대기 목록 (GET /api/leaves/pending, TEAM_LEADER·SYSTEM_ADMIN 전용)
// enabled로 막지 않으면 EMPLOYEE가 마운트된 컴포넌트(사이드바 등)에서 403 에러 toast가 뜬다.
export function usePendingApprovals({ page = 0, size = 50, enabled = true } = {}) {
  return useQuery({
    queryKey: leaveKeys.pending(page),
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
    onSuccess: () => queryClient.invalidateQueries({ queryKey: ['leaves'] }),
  });
}

// 승인/반려 뮤테이션 (POST /api/leaves/{id}/approval)
export function useProcessApproval() {
  const queryClient = useQueryClient();
  return useMutation({
    mutationFn: ({ id, approved, comment }) => processApproval(id, { approved, comment }),
    onSuccess: () => queryClient.invalidateQueries({ queryKey: ['leaves'] }),
  });
}

// 소급 취소 승인/반려 뮤테이션 (POST /api/leaves/{id}/cancel-approval)
export function useProcessCancelApproval() {
  const queryClient = useQueryClient();
  return useMutation({
    mutationFn: ({ id, approved, comment }) => processCancelApproval(id, { approved, comment }),
    onSuccess: () => queryClient.invalidateQueries({ queryKey: ['leaves'] }),
  });
}
