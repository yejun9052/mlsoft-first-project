import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query';
import {
  applyWelfare,
  cancelWelfareRequest,
  getAllWelfarePolicies,
  getMyWelfareRequests,
  getPendingWelfareApprovals,
  processWelfareApproval,
} from '../api/welfare.js';

// 쿼리 키 규칙: ['welfare', 서브리소스, ...파라미터]. 접두사(['welfare'])로 invalidate하면
// policies/me/pending 등 복리후생 관련 쿼리가 한 번에 무효화된다 (useLeaves.js와 동일한 전략).
const welfareKeys = {
  policiesAll: ['welfare', 'policies', 'all'],
  me: (page) => ['welfare', 'me', page],
  pending: (page) => ['welfare', 'pending', page],
};

// 활성 정책 전체 — 신청 폼·카테고리 카드 그리드 공용 (GET /api/welfare-policies/all)
export function useWelfarePoliciesAll() {
  return useQuery({ queryKey: welfareKeys.policiesAll, queryFn: getAllWelfarePolicies });
}

// 내 신청 내역 (GET /api/welfare-requests/me)
export function useMyWelfareRequests({ page = 0, size = 20 } = {}) {
  return useQuery({
    queryKey: welfareKeys.me(page),
    queryFn: () => getMyWelfareRequests({ page, size }),
  });
}

// 내가 승인자인 대기 목록 (GET /api/welfare-requests/pending, TEAM_LEADER·SYSTEM_ADMIN 전용)
// enabled로 막지 않으면 EMPLOYEE가 마운트된 화면에서 403 에러 toast가 뜬다.
export function usePendingWelfareApprovals({ page = 0, size = 50, enabled = true } = {}) {
  return useQuery({
    queryKey: welfareKeys.pending(page),
    queryFn: () => getPendingWelfareApprovals({ page, size }),
    enabled,
  });
}

// 복리후생 신청 뮤테이션 — 성공 시 내 신청 내역·대기 목록이 바뀌므로 'welfare' 전체를 무효화한다.
export function useApplyWelfare() {
  const queryClient = useQueryClient();
  return useMutation({
    mutationFn: applyWelfare,
    onSuccess: () => queryClient.invalidateQueries({ queryKey: ['welfare'] }),
  });
}

// 승인/반려 뮤테이션 (POST /api/welfare-requests/{id}/approval)
export function useProcessWelfareApproval() {
  const queryClient = useQueryClient();
  return useMutation({
    mutationFn: ({ id, approved, comment }) => processWelfareApproval(id, { approved, comment }),
    onSuccess: () => queryClient.invalidateQueries({ queryKey: ['welfare'] }),
  });
}

// 취소 뮤테이션 — 본인, PENDING 상태만 (POST /api/welfare-requests/{id}/cancel)
export function useCancelWelfareRequest() {
  const queryClient = useQueryClient();
  return useMutation({
    mutationFn: cancelWelfareRequest,
    onSuccess: () => queryClient.invalidateQueries({ queryKey: ['welfare'] }),
  });
}
