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

// 최초 온보딩 제출 — 생일·입사일 등록, base_days는 서버가 정책으로 자동 계산 (POST /api/auth/onboarding)
export async function submitOnboarding({ birthDay, hireDate }) {
  const res = await api.post('/auth/onboarding', { birthDay, hireDate });
  return res.data.data;
}
