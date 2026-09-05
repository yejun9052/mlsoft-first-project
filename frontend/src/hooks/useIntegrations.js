import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query';
import {
  getIntegrations,
  sendTestMail,
  updateIntegration,
  verifyHolidayKey,
} from '../api/integrations.js';

// 쿼리 키: ['integrations'] 하나. 메일·공휴일이 한 응답으로 오므로 나눌 것이 없다.
const integrationKeys = {
  all: ['integrations'],
};

// 외부 연동 설정 조회 (GET /api/admin/integrations)
export function useIntegrations() {
  return useQuery({ queryKey: integrationKeys.all, queryFn: getIntegrations });
}

// 자격 증명 저장·회전 (PUT /api/admin/integrations/{provider})
export function useUpdateIntegration() {
  const queryClient = useQueryClient();
  return useMutation({
    mutationFn: ({ provider, ...body }) => updateIntegration(provider, body),
    onSuccess: () => queryClient.invalidateQueries({ queryKey: integrationKeys.all }),
  });
}

// 테스트 메일 발송 (POST /api/admin/integrations/mail/test).
// 큐에 넣기만 하므로 설정은 그대로다 — 무효화하지 않는다.
export function useSendTestMail() {
  return useMutation({ mutationFn: sendTestMail });
}

// 공휴일 키 검증 (POST /api/admin/integrations/holiday/verify) — 저장하지 않으므로 무효화 없음
export function useVerifyHolidayKey() {
  return useMutation({ mutationFn: verifyHolidayKey });
}
