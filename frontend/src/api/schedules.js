import api from './index.js';

// 개인 일정(외근·출장·재택·교육) API — 연차와 달리 잔액 차감·결재가 없어 등록 즉시 확정된다.

// 일정 등록 (POST /api/schedules)
export async function createSchedule({ scheduleType, dates, memo }) {
  const res = await api.post('/schedules', { scheduleType, dates, memo: memo || null });
  return res.data.data;
}

// 일정 수정 — 본인만 (PUT /api/schedules/{id})
export async function updateSchedule({ id, scheduleType, dates, memo }) {
  const res = await api.put(`/schedules/${id}`, { scheduleType, dates, memo: memo || null });
  return res.data.data;
}

// 일정 삭제 — 본인만 (DELETE /api/schedules/{id})
export async function deleteSchedule(id) {
  const res = await api.delete(`/schedules/${id}`);
  return res.data.data;
}

// 캘린더용 일정 — 전 직원, 타인 메모는 마스킹 (GET /api/schedules/calendar)
export async function getScheduleCalendar({ year, month, keyword, departmentId } = {}) {
  const res = await api.get('/schedules/calendar', {
    params: { year, month, keyword: keyword || undefined, departmentId: departmentId || undefined },
  });
  return res.data.data;
}

// 내 일정 목록 (GET /api/schedules/me)
export async function getMySchedules({ page = 0, size = 20 } = {}) {
  const res = await api.get('/schedules/me', { params: { page, size } });
  return res.data.data;
}

// 선택 가능한 일정 종류 + 한글 라벨 (GET /api/schedules/types)
// 종류를 서버에서 받아 쓰면 백엔드에 종류를 추가했을 때 화면이 자동으로 따라온다.
export async function getScheduleTypes() {
  const res = await api.get('/schedules/types');
  return res.data.data;
}
