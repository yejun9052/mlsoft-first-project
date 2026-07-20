import api from './index.js';

// 정책 목록 (페이징, keyword·category 필터 — GET /api/welfare-policies)
export async function getWelfarePolicies({ keyword, category, page = 0, size = 10 } = {}) {
  const res = await api.get('/welfare-policies', { params: { keyword, category, page, size } });
  return res.data.data;
}

// 카테고리 문자열 목록 (필터·신청 폼용 — GET /api/welfare-policies/categories)
export async function getWelfareCategories() {
  const res = await api.get('/welfare-policies/categories');
  return res.data.data;
}

// 활성 정책 전체 — 페이징 없이 신청 폼·카테고리 카드 그리드용 (GET /api/welfare-policies/all)
export async function getAllWelfarePolicies() {
  const res = await api.get('/welfare-policies/all');
  return res.data.data;
}

// 복리후생 신청 (POST /api/welfare-requests)
// reason은 서버가 자동 생성하지 않고 그대로 저장하므로 필수(@NotBlank) — 빈 문자열을 보내면 400.
export async function applyWelfare({ policyId, reason, subApproverId }) {
  const res = await api.post('/welfare-requests', { policyId, reason, subApproverId: subApproverId || null });
  return res.data.data;
}

// 내 신청 내역 (페이징 — GET /api/welfare-requests/me)
export async function getMyWelfareRequests({ page = 0, size = 20, sort = 'createdAt,desc' } = {}) {
  const res = await api.get('/welfare-requests/me', { params: { page, size, sort } });
  return res.data.data;
}

// 내가 승인자인 대기 목록 (GET /api/welfare-requests/pending, TL·SA)
export async function getPendingWelfareApprovals({ page = 0, size = 20, sort = 'createdAt,asc' } = {}) {
  const res = await api.get('/welfare-requests/pending', { params: { page, size, sort } });
  return res.data.data;
}

// 전체 신청 목록 — SYSTEM_ADMIN 전용 (GET /api/welfare-requests)
export async function getAllWelfareRequests({ page = 0, size = 20 } = {}) {
  const res = await api.get('/welfare-requests', { params: { page, size } });
  return res.data.data;
}

// 승인/반려 — 승인자만. 승인 시 서버가 addDays만큼 신청자 bonus_days 가산 (POST /api/welfare-requests/{id}/approval)
export async function processWelfareApproval(id, { approved, comment = '' }) {
  const res = await api.post(`/welfare-requests/${id}/approval`, { approved, comment });
  return res.data.data;
}

// 취소 — 본인만, PENDING 상태일 때만 가능 (POST /api/welfare-requests/{id}/cancel)
export async function cancelWelfareRequest(id) {
  const res = await api.post(`/welfare-requests/${id}/cancel`);
  return res.data.data;
}
