import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query';
import {
  getLeavePolicies,
  getLeavePolicyConfigs,
  getResetHistories,
  updateLeavePolicy,
  updateLeavePolicyConfig,
} from '../api/policies.js';

// 쿼리 키 규칙: ['policies', 서브리소스, ...파라미터]. 접두사(['policies'])로 invalidate하면
// leave-policies/configs/reset-histories가 한 번에 무효화된다 (users/welfare와 동일한 전략).
// size도 키에 넣는다 — 서버 응답을 바꾸는 파라미터가 키에 없으면 크기가 다른 호출이 같은 캐시를
// 공유해 먼저 캐시된 응답이 재사용된다 (리뷰 F-1).
const policyKeys = {
  leavePolicies: ['policies', 'leave-policies'],
  configs: ['policies', 'configs'],
  resetHistories: (page, size) => ['policies', 'reset-histories', page, size],
};

// 근속년수별 연차 정책 목록 (GET /api/admin/leave-policies, SYSTEM_ADMIN 전용)
export function useLeavePolicies() {
  return useQuery({ queryKey: policyKeys.leavePolicies, queryFn: getLeavePolicies });
}

// 정책 일수 수정 뮤테이션 (PATCH /api/admin/leave-policies/{id})
export function useUpdateLeavePolicy() {
  const queryClient = useQueryClient();
  return useMutation({
    mutationFn: ({ id, annualLeaveDays, description }) =>
      updateLeavePolicy(id, { annualLeaveDays, description }),
    onSuccess: () => queryClient.invalidateQueries({ queryKey: ['policies'] }),
  });
}

// 연차 시스템 설정 전체 조회 (GET /api/admin/configs, SYSTEM_ADMIN 전용)
export function useLeavePolicyConfigs() {
  return useQuery({ queryKey: policyKeys.configs, queryFn: getLeavePolicyConfigs });
}

// 설정 변경 뮤테이션 — name 기준 단건 갱신 (PUT /api/admin/configs)
export function useUpdateLeavePolicyConfig() {
  const queryClient = useQueryClient();
  return useMutation({
    mutationFn: updateLeavePolicyConfig,
    onSuccess: () => queryClient.invalidateQueries({ queryKey: ['policies'] }),
  });
}

// 기산일 리셋·소멸 이력 (GET /api/admin/reset-histories, SYSTEM_ADMIN 전용)
export function useResetHistories({ page = 0, size = 50 } = {}) {
  return useQuery({
    queryKey: policyKeys.resetHistories(page, size),
    queryFn: () => getResetHistories({ page, size }),
  });
}
