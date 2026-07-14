import api from './index.js';

// 연차 신청 — 선차감, 주말·과거·중복·잔여 검증은 서버가 수행 (POST /api/leaves)
// 승인자(primaryApprover)는 서버가 부서장 기준으로 자동 배정 — 클라이언트는 지정 불가.
// subApproverId는 선택(재직 중 TEAM_LEADER·SYSTEM_ADMIN만 가능, 서버가 검증).
export async function applyLeave({ leaveType, dates, reason, subApproverId }) {
  const res = await api.post('/leaves', { leaveType, dates, reason, subApproverId: subApproverId || null });
  return res.data.data;
}

// 내 신청 내역 (페이징, status 필터 — GET /api/leaves/me)
export async function getMyLeaves({ status, page = 0, size = 20, sort = 'createdAt,desc' } = {}) {
  const res = await api.get('/leaves/me', { params: { status, page, size, sort } });
  return res.data.data; // Page<LeaveResponse> — { content, page: { size, number, totalElements, totalPages } }
}

// 잔여 연차 현황 (GET /api/leaves/me/summary)
export async function getMySummary() {
  const res = await api.get('/leaves/me/summary');
  return res.data.data;
}

// 캘린더용 승인 연차 — 회사 전체, 타인 사유는 마스킹 (GET /api/leaves/calendar)
export async function getCalendar({ year, month }) {
  const res = await api.get('/leaves/calendar', { params: { year, month } });
  return res.data.data;
}

// 내 팀 연차 현황 — 기간 미지정 시 서버가 이번 달로 처리 (GET /api/leaves/team)
export async function getTeamLeaves({ from, to } = {}) {
  const res = await api.get('/leaves/team', { params: { from, to } });
  return res.data.data;
}

// 내가 승인자인 대기 목록 — 취소 대기(CANCEL_PENDING) 포함 (GET /api/leaves/pending, TL·SA)
export async function getPendingApprovals({ page = 0, size = 20, sort = 'createdAt,asc' } = {}) {
  const res = await api.get('/leaves/pending', { params: { page, size, sort } });
  return res.data.data;
}

// 전체 신청 목록 — SYSTEM_ADMIN 전용 (GET /api/leaves)
export async function getAllLeaves({ status, keyword, page = 0, size = 20 } = {}) {
  const res = await api.get('/leaves', { params: { status, keyword, page, size } });
  return res.data.data;
}

// 승인/반려 — 승인자만 (POST /api/leaves/{id}/approval)
export async function processApproval(id, { approved, comment = '' }) {
  const res = await api.post(`/leaves/${id}/approval`, { approved, comment });
  return res.data.data;
}

// 취소 신청 — 본인만. 미래만 포함이면 즉시 취소, 과거 포함이면 소급취소(승인 대기) (POST /api/leaves/{id}/cancel)
export async function cancelLeave(id, { reason }) {
  const res = await api.post(`/leaves/${id}/cancel`, { reason });
  return res.data.data;
}

// 소급 취소 승인/반려 — 승인자만 (POST /api/leaves/{id}/cancel-approval)
export async function processCancelApproval(id, { approved, comment = '' }) {
  const res = await api.post(`/leaves/${id}/cancel-approval`, { approved, comment });
  return res.data.data;
}

// 해당 건 처리 이력 — 본인·승인자·SA (GET /api/leaves/{id}/histories)
export async function getLeaveHistories(id) {
  const res = await api.get(`/leaves/${id}/histories`);
  return res.data.data;
}
