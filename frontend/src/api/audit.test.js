import { beforeEach, describe, expect, it, vi } from 'vitest';
import api from './index.js';
import { getAuditActions, getAuditLogs } from './audit.js';

vi.mock('./index.js', () => ({
  default: { get: vi.fn() },
}));

beforeEach(() => {
  api.get.mockReset();
  api.get.mockResolvedValue({ data: { data: { content: [] } } });
});

describe('api/audit.js', () => {
  it('getAuditLogs calls /admin/audit-logs with paging params — 필터 미지정이면 undefined', async () => {
    await getAuditLogs();
    expect(api.get).toHaveBeenCalledWith('/admin/audit-logs', {
      params: { action: undefined, targetUserId: undefined, page: 0, size: 20 },
    });
  });

  it('getAuditLogs passes action·targetUserId·page through', async () => {
    await getAuditLogs({ action: 'BASE_DAYS_CHANGED', targetUserId: 7, page: 2 });
    expect(api.get).toHaveBeenCalledWith('/admin/audit-logs', {
      params: { action: 'BASE_DAYS_CHANGED', targetUserId: 7, page: 2, size: 20 },
    });
  });

  it('getAuditActions calls the /actions catalog endpoint without params', async () => {
    api.get.mockResolvedValue({ data: { data: [{ name: 'ROLE_CHANGED', label: '권한 변경' }] } });
    const result = await getAuditActions();
    expect(api.get).toHaveBeenCalledWith('/admin/audit-logs/actions');
    // 라벨을 서버에서 받아 오는 것이 이 엔드포인트의 목적이다 (프론트 하드코딩 방지)
    expect(result[0].label).toBe('권한 변경');
  });

  it('unwraps res.data.data', async () => {
    api.get.mockResolvedValue({
      data: { data: { content: [{ id: 3, action: 'USER_RETIRED' }], page: { totalPages: 1 } } },
    });
    const result = await getAuditLogs();
    expect(result.content).toEqual([{ id: 3, action: 'USER_RETIRED' }]);
    expect(result.page.totalPages).toBe(1);
  });
});
