import api from './index.js';

// 연차 소진 안내 대상자 — 다음 기산일이 `reminder_list_days` 이내이고 잔여가 남은 사원
// (GET /api/emails/reminder-targets, SYSTEM_ADMIN 전용).
// emailAvailable=false는 메일 주소가 없어 발송 대상이 될 수 없는 사원이다 — 서버도 skipped로 센다.
export async function getReminderTargets() {
  const res = await api.get('/emails/reminder-targets');
  return res.data.data; // [{userId, name, departmentName, remainingDays, nextResetDate, daysUntilReset, emailAvailable}]
}

// 선택 대상 일괄 발송 (POST /api/emails/bulk).
// 회당 100명·일 400건 상한은 **서버가 판정한다** — 화면은 100명 상한만 미리 막아 헛클릭을 줄이고,
// 일 한도 초과는 서버 거부 메시지를 인터셉터가 그대로 띄운다.
export async function sendBulkEmail({ userIds, title, content }) {
  const res = await api.post('/emails/bulk', { userIds, title, content });
  return res.data.data; // {requested, queued, skipped}
}

// 발송 이력 (페이징 · type·status 필터 — GET /api/emails).
// 필터 미지정(undefined)은 axios가 쿼리에서 빼므로 전체 조회가 된다.
export async function getEmailHistories({ page = 0, size = 10, type, status } = {}) {
  const res = await api.get('/emails', { params: { page, size, type, status } });
  return res.data.data; // Page<EmailHistoryResponse>
}

// FAILED 건 재발송 (POST /api/emails/{id}/resend).
// FAILED가 아니면 서버가 400(EMAIL_RESEND_NOT_ALLOWED)을 준다 — SENT 재발송은 중복 수신이라 막혀 있다.
export async function resendEmail(id) {
  const res = await api.post(`/emails/${id}/resend`);
  return res.data.data; // 갱신된 이력 1건
}
