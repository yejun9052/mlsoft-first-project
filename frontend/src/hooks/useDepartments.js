import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query';
import {
  createDepartment,
  deactivateDepartment,
  getDepartments,
  getDepartmentTree,
  updateDepartment,
} from '../api/departments.js';

// 쿼리 키 규칙: ['departments', 서브리소스]. 접두사(['departments'])로 invalidate하면 목록·트리가
// 한 번에 무효화된다 (users/leaves와 동일한 전략).
const departmentKeys = {
  all: ['departments', 'all'],
  tree: ['departments', 'tree'],
};

// 부서 전체 목록 — 드롭다운·선택용 (GET /api/departments)
export function useDepartments() {
  return useQuery({ queryKey: departmentKeys.all, queryFn: getDepartments });
}

// 부서 2단계 계층 트리 (GET /api/departments/tree)
export function useDepartmentTree() {
  return useQuery({ queryKey: departmentKeys.tree, queryFn: getDepartmentTree });
}

// 부서 변경 뮤테이션 3종의 공통 무효화 — 부서명·팀장은 구성원 목록(departmentName)과 사이드바
// 유저 카드에도 실려 나가므로 ['users']까지 함께 무효화한다.
function useDepartmentMutation(mutationFn) {
  const queryClient = useQueryClient();
  return useMutation({
    mutationFn,
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: ['departments'] });
      queryClient.invalidateQueries({ queryKey: ['users'] });
    },
  });
}

// 부서 생성 (POST /api/departments, SYSTEM_ADMIN 전용)
export function useCreateDepartment() {
  return useDepartmentMutation(createDepartment);
}

// 부서 수정 (PUT /api/departments/{id}, SYSTEM_ADMIN 전용)
export function useUpdateDepartment() {
  return useDepartmentMutation(({ id, ...body }) => updateDepartment(id, body));
}

// 부서 비활성화 (DELETE /api/departments/{id}, SYSTEM_ADMIN 전용)
export function useDeactivateDepartment() {
  return useDepartmentMutation(deactivateDepartment);
}
