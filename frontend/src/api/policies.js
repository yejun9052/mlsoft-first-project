import api from './index.js';

// 근속년수별 연차 정책 목록 — 근속년수 오름차순 21건 (GET /api/admin/leave-policies, SYSTEM_ADMIN 전용)
export async function getLeavePolicies() {
  const res = await api.get('/admin/leave-policies');
  return res.data.data; // LeavePolicyResponse[]
}

// 정책 일수 수정 — description을 넘기지 않으면(undefined) 기존 값을 유지한다 (PATCH /api/admin/leave-policies/{id})
export async function updateLeavePolicy(id, { annualLeaveDays, description }) {
  const res = await api.patch(`/admin/leave-policies/${id}`, { annualLeaveDays, description });
  return res.data.data;
}

// 연차 시스템 설정 전체 조회 — value는 항상 문자열이며 타입·라벨 메타데이터는 없다 (GET /api/admin/configs)
export async function getLeavePolicyConfigs() {
  const res = await api.get('/admin/configs');
  return res.data.data; // LeavePolicyConfigResponse[]
}

// 설정 변경 — name 기준 단건 갱신, 새 키 생성은 지원하지 않는다 (PUT /api/admin/configs)
export async function updateLeavePolicyConfig({ name, value }) {
  const res = await api.put('/admin/configs', { name, value });
  return res.data.data;
}

// 기산일 리셋·소멸 이력 (페이징, 서버 기본 정렬=리셋일 최신순 — GET /api/admin/reset-histories)
export async function getResetHistories({ page = 0, size = 50 } = {}) {
  const res = await api.get('/admin/reset-histories', { params: { page, size } });
  return res.data.data; // Page<LeaveResetHistoryResponse>
}
