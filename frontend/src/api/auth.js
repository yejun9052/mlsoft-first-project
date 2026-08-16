import api from './index.js';

// 내 정보 조회 — 로그인 유저의 프로필·역할·온보딩 여부 (GET /api/auth/me)
export async function me() {
  const res = await api.get('/auth/me');
  return res.data.data;
}

// 로그아웃 — 서버에서 JWT HttpOnly 쿠키 만료 (POST /api/auth/logout)
export async function logout() {
  const res = await api.post('/auth/logout');
  return res.data.data;
}

// 최초 온보딩 제출 — 생일·입사일 등록, base_days는 서버가 정책으로 자동 계산 (POST /api/auth/onboarding).
// 응답의 onboardingStatus가 PENDING_APPROVAL이면 자동 승인 범위를 벗어난 입사일이라 연차가 아직 없다 (리뷰 S-1).
export async function submitOnboarding({ birthDay, hireDate }) {
  const res = await api.post('/auth/onboarding', { birthDay, hireDate });
  return res.data.data;
}

// 승인 대기 중 입사일·생일 수정 — 1회만 허용 (PATCH /api/auth/onboarding).
export async function reviseOnboarding({ birthDay, hireDate }) {
  const res = await api.patch('/auth/onboarding', { birthDay, hireDate });
  return res.data.data;
}

// 온보딩 승인 대기 목록 (GET /api/admin/onboardings, SYSTEM_ADMIN 전용)
export async function getPendingOnboardings({ page = 0, size = 20 } = {}) {
  const res = await api.get('/admin/onboardings', { params: { page, size } });
  return res.data.data;
}

// 온보딩 승인 — 신고한 입사일을 확정하고 그 시점에 연차를 부여한다 (POST /api/admin/onboardings/{id}/approval)
export async function approveOnboarding(userId) {
  const res = await api.post(`/admin/onboardings/${userId}/approval`);
  return res.data.data;
}

// 온보딩 반려 — 입력값을 지워 사원이 다시 낼 수 있게 한다 (POST /api/admin/onboardings/{id}/rejection)
export async function rejectOnboarding(userId) {
  const res = await api.post(`/admin/onboardings/${userId}/rejection`);
  return res.data.data;
}
