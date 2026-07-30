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

// 부서 생성 — SYSTEM_ADMIN 전용 (POST /api/departments)
// leaderId·parentId는 선택 사항(팀장 공석·최상위 부서 허용)이라 빈 값은 null로 보낸다.
export async function createDepartment({ name, description, leaderId, parentId }) {
  const res = await api.post('/departments', {
    name,
    description,
    leaderId: leaderId || null,
    parentId: parentId || null,
  });
  return res.data.data;
}

// 부서 수정 — SYSTEM_ADMIN 전용 (PUT /api/departments/{id})
// ⚠️ PUT은 전체 갱신이라 leaderId를 빼고 보내면 서버가 팀장 공석으로 처리한다 — 폼은 항상 전 필드를 채워 보낼 것.
export async function updateDepartment(id, { name, description, leaderId, parentId }) {
  const res = await api.put(`/departments/${id}`, {
    name,
    description,
    leaderId: leaderId || null,
    parentId: parentId || null,
  });
  return res.data.data;
}

// 부서 비활성화(소프트 삭제) — SYSTEM_ADMIN 전용 (DELETE /api/departments/{id})
// 소속 사원·과거 신청의 부서 참조를 보존하기 위해 실제 삭제하지 않는다.
export async function deactivateDepartment(id) {
  const res = await api.delete(`/departments/${id}`);
  return res.data.data;
}
