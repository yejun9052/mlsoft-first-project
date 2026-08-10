import api from './index.js';

// 처리 이력 로그 (docs/03 처리 이력 — 관리자·팀장 로그 화면).
// 전사(`/`)는 SYSTEM_ADMIN, `/my-approvals`는 TEAM_LEADER·SYSTEM_ADMIN.
// 스코프는 파라미터가 아니라 서버가 토큰의 요청자 id로 결정하므로 클라이언트가 대상을 지정하지 않는다.
// 정렬은 서버 기본값(createdAt DESC = 최근순)을 그대로 쓴다.

// 전사 연차 처리 로그 (GET /api/leave-histories, SA)
export async function getLeaveHistories({ action, page = 0, size = 20 } = {}) {
  const res = await api.get('/leave-histories', { params: { action, page, size } });
  return res.data.data; // Page<LeaveHistoryLogResponse>
}

// 내가 결재자인 연차 신청의 이력 (GET /api/leave-histories/my-approvals, TL·SA — 리뷰 S-6).
// 예전 /my-team(신청자 소속 부서 기준)을 대체한다 — 부서로 묶으면 실제 결재 권한과 어긋난다.
export async function getMyApprovalLeaveHistories({ action, page = 0, size = 20 } = {}) {
  const res = await api.get('/leave-histories/my-approvals', { params: { action, page, size } });
  return res.data.data;
}

// 내가 처리한 연차 결재 로그 (GET /api/leave-histories/my-actions, 로그인 전체).
// my-approvals와 다르다 — 그쪽은 내가 승인자로 지정된 신청의 모든 이력이라 서브 승인자가 처리한
// 기록도 섞이고, 이건 actor가 본인인 것만. 결재 화면의 승인·반려 완료 탭이 쓴다.
export async function getMyActionLeaveHistories({ action, page = 0, size = 20 } = {}) {
  const res = await api.get('/leave-histories/my-actions', { params: { action, page, size } });
  return res.data.data;
}

// 전사 복리후생 처리 로그 (GET /api/welfare-histories, SA)
export async function getWelfareHistories({ action, page = 0, size = 20 } = {}) {
  const res = await api.get('/welfare-histories', { params: { action, page, size } });
  return res.data.data; // Page<WelfareHistoryLogResponse>
}

// 내가 결재자인 복리후생 신청의 이력 (GET /api/welfare-histories/my-approvals, TL·SA)
export async function getMyApprovalWelfareHistories({ action, page = 0, size = 20 } = {}) {
  const res = await api.get('/welfare-histories/my-approvals', { params: { action, page, size } });
  return res.data.data;
}

// 내가 처리한 복리후생 결재 로그 (GET /api/welfare-histories/my-actions, 로그인 전체)
export async function getMyActionWelfareHistories({ action, page = 0, size = 20 } = {}) {
  const res = await api.get('/welfare-histories/my-actions', { params: { action, page, size } });
  return res.data.data;
}
