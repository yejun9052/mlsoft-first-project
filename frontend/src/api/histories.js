import api from './index.js';

// 처리 이력 로그 (docs/03 처리 이력 — 관리자·팀장 로그 화면).
// 전사(`/`)는 SYSTEM_ADMIN, 팀(`/my-team`)은 TEAM_LEADER·SYSTEM_ADMIN. 팀 스코프는 파라미터가 아니라
// 서버가 요청자의 소속 부서로 결정하므로 클라이언트가 부서를 지정하지 않는다.
// 정렬은 서버 기본값(createdAt DESC = 최근순)을 그대로 쓴다.

// 전사 연차 처리 로그 (GET /api/leave-histories, SA)
export async function getLeaveHistories({ action, page = 0, size = 20 } = {}) {
  const res = await api.get('/leave-histories', { params: { action, page, size } });
  return res.data.data; // Page<LeaveHistoryLogResponse>
}

// 내 팀 연차 처리 로그 (GET /api/leave-histories/my-team, TL·SA)
export async function getMyTeamLeaveHistories({ action, page = 0, size = 20 } = {}) {
  const res = await api.get('/leave-histories/my-team', { params: { action, page, size } });
  return res.data.data;
}

// 전사 복리후생 처리 로그 (GET /api/welfare-histories, SA)
export async function getWelfareHistories({ action, page = 0, size = 20 } = {}) {
  const res = await api.get('/welfare-histories', { params: { action, page, size } });
  return res.data.data; // Page<WelfareHistoryLogResponse>
}

// 내 팀 복리후생 처리 로그 (GET /api/welfare-histories/my-team, TL·SA)
export async function getMyTeamWelfareHistories({ action, page = 0, size = 20 } = {}) {
  const res = await api.get('/welfare-histories/my-team', { params: { action, page, size } });
  return res.data.data;
}
