import api from './index.js';

// 공휴일 — 서버가 data.go.kr 응답을 DB에 캐시해 두고 내려준다 (GET /api/holidays)
// year 생략 시 서버가 올해(KST)로 처리한다.
export async function getHolidays({ year } = {}) {
  const res = await api.get('/holidays', { params: { year } });
  return res.data.data; // [{ date: 'YYYY-MM-DD', name }]
}

// 강제 재동기화 — SYSTEM_ADMIN 전용 (POST /api/holidays/sync)
export async function syncHolidays({ year } = {}) {
  const res = await api.post('/holidays/sync', null, { params: { year } });
  return res.data.data; // { year, saved }
}
