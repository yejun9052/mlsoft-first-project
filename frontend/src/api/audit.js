import api from './index.js';

// 관리자 조작 감사 로그 (리뷰 S-3) — SYSTEM_ADMIN 전용.
// 쓰기 엔드포인트가 없다: 기록은 조작이 일어나는 서버 서비스 안에서만 만들어진다.
// 정렬은 서버 기본값(createdAt DESC = 최근순)을 그대로 쓴다.

// 감사 로그 목록 (GET /api/admin/audit-logs, SA)
export async function getAuditLogs({ action, targetUserId, page = 0, size = 20 } = {}) {
  const res = await api.get('/admin/audit-logs', { params: { action, targetUserId, page, size } });
  return res.data.data; // Page<AdminAuditLogResponse>
}

// 필터용 액션 목록 (GET /api/admin/audit-logs/actions, SA).
// 라벨을 서버가 내려주므로 액션 이름을 프론트에 하드코딩하지 않는다 — 서버 enum에 상수를
// 한 줄 추가하면 필터 칩이 따라온다.
export async function getAuditActions() {
  const res = await api.get('/admin/audit-logs/actions');
  return res.data.data; // AdminActionOption[]
}
