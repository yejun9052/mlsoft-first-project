import { beforeEach, describe, expect, it, vi } from 'vitest';
import api from './index.js';
import {
  getLeaveHistories,
  getMyApprovalLeaveHistories,
  getMyApprovalWelfareHistories,
  getWelfareHistories,
} from './histories.js';

vi.mock('./index.js', () => ({
  default: { get: vi.fn() },
}));

beforeEach(() => {
  api.get.mockReset();
  api.get.mockResolvedValue({ data: { data: { content: [] } } });
});

describe('api/histories.js', () => {
  it('getLeaveHistories calls /leave-histories with paging params — action 미지정이면 undefined', async () => {
    await getLeaveHistories();
    expect(api.get).toHaveBeenCalledWith('/leave-histories', {
      params: { action: undefined, page: 0, size: 20 },
    });
  });

  it('getLeaveHistories passes action filter and requested page through', async () => {
    await getLeaveHistories({ action: 'REJECTED', page: 3 });
    expect(api.get).toHaveBeenCalledWith('/leave-histories', {
      params: { action: 'REJECTED', page: 3, size: 20 },
    });
  });

  it('getMyApprovalLeaveHistories calls /my-approvals — 대상을 파라미터로 보내지 않는다', async () => {
    // 스코프는 서버가 토큰의 요청자 id로 결정한다 (리뷰 S-6)
    await getMyApprovalLeaveHistories({ page: 1 });
    expect(api.get).toHaveBeenCalledWith('/leave-histories/my-approvals', {
      params: { action: undefined, page: 1, size: 20 },
    });
  });

  it('getWelfareHistories calls /welfare-histories', async () => {
    await getWelfareHistories({ action: 'APPROVED' });
    expect(api.get).toHaveBeenCalledWith('/welfare-histories', {
      params: { action: 'APPROVED', page: 0, size: 20 },
    });
  });

  it('getMyApprovalWelfareHistories calls /my-approvals', async () => {
    await getMyApprovalWelfareHistories();
    expect(api.get).toHaveBeenCalledWith('/welfare-histories/my-approvals', {
      params: { action: undefined, page: 0, size: 20 },
    });
  });

  it('unwraps res.data.data', async () => {
    api.get.mockResolvedValue({ data: { data: { content: [{ id: 9 }], page: { totalPages: 2 } } } });
    const result = await getLeaveHistories();
    expect(result.content).toEqual([{ id: 9 }]);
    expect(result.page.totalPages).toBe(2);
  });
});
