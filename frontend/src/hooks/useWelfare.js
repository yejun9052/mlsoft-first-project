import { keepPreviousData, useMutation, useQuery, useQueryClient } from '@tanstack/react-query';
import {
  applyWelfare,
  cancelWelfareRequest,
  createWelfarePolicy,
  deactivateWelfarePolicy,
  getAllWelfarePolicies,
  getMyWelfareRequests,
  getPendingWelfareApprovals,
  getWelfarePolicies,
  processWelfareApproval,
  updateWelfarePolicy,
} from '../api/welfare.js';

// 쿼리 키 규칙: ['welfare', 서브리소스, ...파라미터]. 접두사(['welfare'])로 invalidate하면
// policies/me/pending 등 복리후생 관련 쿼리가 한 번에 무효화된다 (useLeaves.js와 동일한 전략).
// size도 키에 넣는다 — 서버 응답을 바꾸는 파라미터가 키에 없으면 크기가 다른 호출이 같은 캐시를
// 공유해 먼저 캐시된 응답이 재사용된다 (리뷰 F-1).
const welfareKeys = {
  policiesAll: ['welfare', 'policies', 'all'],
  policies: (keyword, category, page, size) =>
    ['welfare', 'policies', keyword || '', category || '', page, size],
  me: (page, size) => ['welfare', 'me', page, size],
  pending: (page, size) => ['welfare', 'pending', page, size],
};

// 복리후생 승인은 User.addBonusDays로 **연차 잔액을 바꾼다** — ['welfare']만 무효화하면
// 대시보드·내 정보의 잔여 연차가 낡은 값으로 남는다 (리뷰 F-5). 처리 이력 로그도 함께 낡는다.
function invalidateWelfareAndRelated(queryClient, { touchesLeaveBalance = false } = {}) {
  queryClient.invalidateQueries({ queryKey: ['welfare'] });
  queryClient.invalidateQueries({ queryKey: ['histories', 'welfare'] });
  if (touchesLeaveBalance) {
    queryClient.invalidateQueries({ queryKey: ['leaves'] });
  }
}

// 활성 정책 전체 — 신청 폼·카테고리 카드 그리드 공용 (GET /api/welfare-policies/all)
export function useWelfarePoliciesAll() {
  return useQuery({ queryKey: welfareKeys.policiesAll, queryFn: getAllWelfarePolicies });
}

// 정책 목록 (페이징 — GET /api/welfare-policies). 관리 화면이 쓴다.
// policiesAll과 나눠 둔 이유: 신청 폼은 전체가 한 번에 필요하고(카드 그리드), 관리 화면은
// 페이징·검색이 필요하다. 응답 형태가 달라(List vs Page) 훅도 분리한다.
export function useWelfarePolicies({ keyword, category, page = 0, size = 10 } = {}) {
  return useQuery({
    queryKey: welfareKeys.policies(keyword, category, page, size),
    queryFn: () => getWelfarePolicies({ keyword, category, page, size }),
    placeholderData: keepPreviousData,
  });
}

// 정책 추가·수정·비활성화 — 셋 다 신청 폼의 정책 목록을 바꾸므로 ['welfare'] 전체를 무효화한다.
// 연차 잔액은 건드리지 않는다 (부여는 승인 시점에 일어난다).
export function useCreateWelfarePolicy() {
  const queryClient = useQueryClient();
  return useMutation({
    mutationFn: createWelfarePolicy,
    onSuccess: () => invalidateWelfareAndRelated(queryClient),
  });
}

export function useUpdateWelfarePolicy() {
  const queryClient = useQueryClient();
  return useMutation({
    mutationFn: ({ id, ...body }) => updateWelfarePolicy(id, body),
    onSuccess: () => invalidateWelfareAndRelated(queryClient),
  });
}

export function useDeactivateWelfarePolicy() {
  const queryClient = useQueryClient();
  return useMutation({
    mutationFn: deactivateWelfarePolicy,
    onSuccess: () => invalidateWelfareAndRelated(queryClient),
  });
}

// 내 신청 내역 (GET /api/welfare-requests/me)
export function useMyWelfareRequests({ page = 0, size = 20 } = {}) {
  return useQuery({
    queryKey: welfareKeys.me(page, size),
    queryFn: () => getMyWelfareRequests({ page, size }),
  });
}

// 내가 승인자인 대기 목록 (GET /api/welfare-requests/pending, TEAM_LEADER·SYSTEM_ADMIN 전용)
// enabled로 막지 않으면 EMPLOYEE가 마운트된 화면에서 403 에러 toast가 뜬다.
export function usePendingWelfareApprovals({ page = 0, size = 50, enabled = true } = {}) {
  return useQuery({
    queryKey: welfareKeys.pending(page, size),
    queryFn: () => getPendingWelfareApprovals({ page, size }),
    enabled,
  });
}

// 복리후생 신청 뮤테이션 — 성공 시 내 신청 내역·대기 목록이 바뀌므로 'welfare' 전체를 무효화한다.
export function useApplyWelfare() {
  const queryClient = useQueryClient();
  return useMutation({
    mutationFn: applyWelfare,
    onSuccess: () => invalidateWelfareAndRelated(queryClient),
  });
}

// 승인/반려 뮤테이션 (POST /api/welfare-requests/{id}/approval)
// 승인은 신청자의 bonus_days를 가산하므로 연차 잔액 쿼리까지 무효화한다 (리뷰 F-5).
export function useProcessWelfareApproval() {
  const queryClient = useQueryClient();
  return useMutation({
    mutationFn: ({ id, approved, comment }) => processWelfareApproval(id, { approved, comment }),
    onSuccess: () => invalidateWelfareAndRelated(queryClient, { touchesLeaveBalance: true }),
  });
}

// 취소 뮤테이션 — 본인, PENDING 상태만 (POST /api/welfare-requests/{id}/cancel)
export function useCancelWelfareRequest() {
  const queryClient = useQueryClient();
  return useMutation({
    mutationFn: cancelWelfareRequest,
    onSuccess: () => invalidateWelfareAndRelated(queryClient),
  });
}
