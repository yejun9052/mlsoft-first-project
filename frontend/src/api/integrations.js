import api from './index.js';

// 외부 연동 설정 조회 (GET /api/admin/integrations, SYSTEM_ADMIN 전용).
// **비밀값은 절대 평문으로 오지 않는다** — maskedSecret/maskedKey만 담긴다.
// encryptionConfigured=false면 암호화 키가 없어 저장 자체가 불가능하다.
export async function getIntegrations() {
  const res = await api.get('/admin/integrations');
  return res.data.data; // {mail, holiday, encryptionConfigured}
}

// 자격 증명 저장·회전 (PUT /api/admin/integrations/{provider}).
// provider는 mail | holiday. body는 mail={username, secret}, holiday={apiKey}.
export async function updateIntegration(provider, body) {
  const res = await api.put(`/admin/integrations/${provider}`, body);
  return res.data.data; // 해당 항목(마스킹된 상태)
}

// 테스트 메일 발송 (POST /api/admin/integrations/mail/test).
// 수신자는 요청한 관리자 본인으로 서버가 고정한다 — 화면에서 주소를 고를 수 없다.
export async function sendTestMail() {
  const res = await api.post('/admin/integrations/mail/test');
  return res.data.data; // null
}

// 공휴일 키 검증 (POST /api/admin/integrations/holiday/verify) — **저장하지 않는다**.
// apiKey를 주면 그 키로, 생략하면 저장된 키로 올해를 조회해 본다.
export async function verifyHolidayKey({ apiKey } = {}) {
  const res = await api.post('/admin/integrations/holiday/verify', apiKey ? { apiKey } : {});
  return res.data.data; // {valid, count}
}
