import api from './index.js';

// 부서 전체 목록 (플랫, 드롭다운·선택용 — GET /api/departments)
export async function getDepartments() {
  const res = await api.get('/departments');
  return res.data.data;
}

// 부서 2단계 계층 트리 (GET /api/departments/tree)
export async function getDepartmentTree() {
  const res = await api.get('/departments/tree');
  return res.data.data;
}
