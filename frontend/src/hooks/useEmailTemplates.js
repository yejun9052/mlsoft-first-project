import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query';
import {
  getEmailTemplates,
  previewEmailTemplate,
  updateEmailTemplate,
} from '../api/emailTemplates.js';

// 쿼리 키 규칙: ['email-templates', ...]. 발송 이력(['emails'])과 접두사를 나눠 두어
// 양식을 저장해도 이력 목록이 통째로 다시 조회되지 않게 한다.
const emailTemplateKeys = {
  all: ['email-templates'],
};

// 메일 양식 목록 (GET /api/admin/email-templates)
export function useEmailTemplates() {
  return useQuery({ queryKey: emailTemplateKeys.all, queryFn: getEmailTemplates });
}

// 양식 저장 (PUT /api/admin/email-templates/{templateKey})
export function useUpdateEmailTemplate() {
  const queryClient = useQueryClient();
  return useMutation({
    mutationFn: ({ templateKey, subjectTemplate, bodyTemplate }) =>
      updateEmailTemplate(templateKey, { subjectTemplate, bodyTemplate }),
    onSuccess: () => queryClient.invalidateQueries({ queryKey: emailTemplateKeys.all }),
  });
}

// 미리보기 (POST .../preview) — 저장하지 않으므로 **무효화하지 않는다**.
// 여기서 캐시를 비우면 편집 중인 양식이 서버 값으로 되돌아가 입력이 날아간다.
export function usePreviewEmailTemplate() {
  return useMutation({
    mutationFn: ({ templateKey, subjectTemplate, bodyTemplate }) =>
      previewEmailTemplate(templateKey, { subjectTemplate, bodyTemplate }),
  });
}
