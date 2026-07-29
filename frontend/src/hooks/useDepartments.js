import { useQuery } from '@tanstack/react-query';
import { getDepartments, getDepartmentTree } from '../api/departments.js';

// 쿼리 키 규칙: ['departments', 서브리소스]. 부서는 자주 안 바뀌는 데이터라 별도 mutation이
// 없는 한(현재 이 3개 화면 범위에는 부서 생성/수정이 없음) 무효화 규칙도 단순하게 둔다.
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
