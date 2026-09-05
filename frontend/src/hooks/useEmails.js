import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query';
import {
  getEmailHistories,
  getReminderTargets,
  resendEmail,
  sendBulkEmail,
} from '../api/emails.js';

// 쿼리 키 규칙: ['emails', 서브리소스, ...파라미터]. 접두사로 invalidate하면 대상 목록과 이력이
// 함께 무효화된다 — 발송하면 이력이 늘고 대상의 발송 이력도 달라지므로 둘을 갈라 둘 이유가 없다.
// 필터·페이지를 키에 넣는 것은 policies와 같은 이유다: 응답을 바꾸는 파라미터가 키에 없으면
// 조건이 다른 호출이 같은 캐시를 공유한다 (리뷰 F-1).
const emailKeys = {
  reminderTargets: ['emails', 'reminder-targets'],
  histories: (page, size, type, status) => ['emails', 'histories', page, size, type, status],
};

// 연차 소진 안내 대상자 (GET /api/emails/reminder-targets)
export function useReminderTargets() {
  return useQuery({ queryKey: emailKeys.reminderTargets, queryFn: getReminderTargets });
}

// 선택 대상 일괄 발송 (POST /api/emails/bulk)
export function useSendBulkEmail() {
  const queryClient = useQueryClient();
  return useMutation({
    mutationFn: sendBulkEmail,
    onSuccess: () => queryClient.invalidateQueries({ queryKey: ['emails'] }),
  });
}

// 발송 이력 (GET /api/emails)
export function useEmailHistories({ page = 0, size = 10, type, status } = {}) {
  return useQuery({
    queryKey: emailKeys.histories(page, size, type, status),
    queryFn: () => getEmailHistories({ page, size, type, status }),
  });
}

// FAILED 건 재발송 (POST /api/emails/{id}/resend)
export function useResendEmail() {
  const queryClient = useQueryClient();
  return useMutation({
    mutationFn: resendEmail,
    onSuccess: () => queryClient.invalidateQueries({ queryKey: ['emails'] }),
  });
}
