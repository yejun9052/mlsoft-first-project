import api from './index.js';

// 메일 양식 목록 (GET /api/admin/email-templates, SYSTEM_ADMIN 전용).
// 현재 LEAVE_BALANCE_REMINDER 1종이고, DB에 저장된 적이 없으면 기본 문구가 version 0으로 온다.
export async function getEmailTemplates() {
  const res = await api.get('/admin/email-templates');
  return res.data.data; // [{templateKey, subjectTemplate, bodyTemplate, version, updatedAt, updatedByName, variables}]
}

// 양식 저장 (PUT /api/admin/email-templates/{templateKey}) — 평문을 저장하고 서버가 escaping한다
export async function updateEmailTemplate(templateKey, { subjectTemplate, bodyTemplate }) {
  const res = await api.put(`/admin/email-templates/${templateKey}`, {
    subjectTemplate,
    bodyTemplate,
  });
  return res.data.data;
}

// 미리보기 (POST /api/admin/email-templates/{templateKey}/preview) — **저장하지 않는다**.
// 편집 중인 원문을 그대로 보내고 서버가 샘플 값으로 치환한 결과를 돌려준다.
export async function previewEmailTemplate(templateKey, { subjectTemplate, bodyTemplate }) {
  const res = await api.post(`/admin/email-templates/${templateKey}/preview`, {
    subjectTemplate,
    bodyTemplate,
  });
  return res.data.data; // {subject, html}
}
