import api from './index.js';

// 전체 사용자 목록 — 페이징, keyword(이름/이메일)·role 필터 (GET /api/users, SYSTEM_ADMIN 전용)
export async function getUsers({ keyword, role, page = 0, size = 20 } = {}) {
  const res = await api.get('/users', { params: { keyword, role, page, size } });
  return res.data.data; // Page<UserResponse>
}

// 내 부서 팀원 목록 — 서버가 요청자 부서로 스코프, 파라미터 없음 (GET /api/users/team-members)
// 서브 승인자 후보 — 재직 중 TEAM_LEADER·SYSTEM_ADMIN, 본인 제외 (GET /api/users/approvers)
// 서버가 본인 제외까지 처리하므로 호출부에서 걸러낼 필요가 없다.
export async function getApprovers() {
  const res = await api.get('/users/approvers');
  return res.data.data;
}

// 팀장 후보 — 재직 중 TEAM_LEADER·SYSTEM_ADMIN (GET /api/users/leader-candidates, SYSTEM_ADMIN 전용)
// 승인자 후보와 달리 본인도 포함된다 — 관리자가 자기 부서의 팀장을 겸하는 것은 정상이다.
// 페이징이 없다: 예전에 getUsers({ size: 200 })로 받다가 상한(100)에 걸려 부서 관리 화면이 통째로 400을 받았다.
export async function getLeaderCandidates() {
  const res = await api.get('/users/leader-candidates');
  return res.data.data; // UserSummaryResponse[]
}

export async function getTeamMembers() {
  const res = await api.get('/users/team-members');
  return res.data.data; // UserSummaryResponse[] — 잔여 연차 등 민감 정보 미포함(의도된 설계)
}

// 퇴직자 목록 (페이징 — GET /api/users/retired, SYSTEM_ADMIN 전용)
export async function getRetiredUsers({ page = 0, size = 20 } = {}) {
  const res = await api.get('/users/retired', { params: { page, size } });
  return res.data.data; // Page<UserResponse>
}

// 내 정보 수정 — 이름·생일만 가능(연차·부서·권한은 관리자 전용 API로 분리) (PATCH /api/users/me)
export async function updateMyProfile({ name, birthDay }) {
  const res = await api.patch('/users/me', { name, birthDay });
  return res.data.data;
}

// 권한 변경 — SYSTEM_ADMIN 전용, 대상이 퇴직자면 ALREADY_RETIRED (PATCH /api/users/{id}/role)
export async function updateUserRole(id, { role }) {
  const res = await api.patch(`/users/${id}/role`, { role });
  return res.data.data;
}

// 부서 변경 — SYSTEM_ADMIN 전용, 대상이 퇴직자면 ALREADY_RETIRED (PATCH /api/users/{id}/department)
export async function updateUserDepartment(id, { departmentId }) {
  const res = await api.patch(`/users/${id}/department`, { departmentId });
  return res.data.data;
}

// 연차 기본일수 직접 설정 — SYSTEM_ADMIN 전용, 과거 데이터 정정 목적 (PATCH /api/users/{id}/base-days)
export async function updateUserBaseDays(id, { baseDays }) {
  const res = await api.patch(`/users/${id}/base-days`, { baseDays });
  return res.data.data;
}

// 퇴직 처리 — 팀장 해제·대기 결재 이관은 서버가 처리 (POST /api/users/{id}/retire, SYSTEM_ADMIN 전용)
export async function retireUser(id) {
  const res = await api.post(`/users/${id}/retire`);
  return res.data.data;
}
