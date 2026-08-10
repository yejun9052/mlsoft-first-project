import { keepPreviousData, useQuery } from '@tanstack/react-query';
import { getAuditActions, getAuditLogs } from '../api/audit.js';

// 쿼리 키 규칙: ['audit', 서브리소스, ...파라미터].
// size도 키에 넣는다 — 서버 응답을 바꾸는 파라미터가 키에 없으면 크기가 다른 호출이 같은 캐시를
// 공유해 먼저 캐시된 응답이 재사용된다 (리뷰 F-1).
const auditKeys = {
  logs: (action, targetUserId, page, size) =>
    ['audit', 'logs', action || 'ALL', targetUserId ?? 'ALL', page, size],
  actions: () => ['audit', 'actions'],
};

// 감사 로그 목록 (GET /api/admin/audit-logs)
export function useAuditLogs({ action, targetUserId, page = 0, size = 20, enabled = true } = {}) {
  return useQuery({
    queryKey: auditKeys.logs(action, targetUserId, page, size),
    queryFn: () => getAuditLogs({ action, targetUserId, page, size }),
    enabled,
    // 페이지 전환 시 목록이 빈 화면으로 깜박이지 않게 이전 페이지를 유지한다(react-query v5)
    placeholderData: keepPreviousData,
  });
}

// 필터용 액션 목록 — 서버 카탈로그라 세션 중에 바뀌지 않는다
export function useAuditActions({ enabled = true } = {}) {
  return useQuery({
    queryKey: auditKeys.actions(),
    queryFn: getAuditActions,
    enabled,
    staleTime: Infinity,
  });
}
