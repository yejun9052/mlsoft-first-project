import { beforeEach, describe, expect, it, vi } from 'vitest';
import api from './index.js';
import {
  getLeaveHistories,
  getMyTeamLeaveHistories,
  getMyTeamWelfareHistories,
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

  it('getMyTeamLeaveHistories calls the /my-team endpoint — 부서를 파라미터로 보내지 않는다', async () => {
    await getMyTeamLeaveHistories({ page: 1 });
    expect(api.get).toHaveBeenCalledWith('/leave-histories/my-team', {
      params: { action: undefined, page: 1, size: 20 },
    });
  });

  it('getWelfareHistories calls /welfare-histories', async () => {
    await getWelfareHistories({ action: 'APPROVED' });
    expect(api.get).toHaveBeenCalledWith('/welfare-histories', {
      params: { action: 'APPROVED', page: 0, size: 20 },
    });
  });

  it('getMyTeamWelfareHistories calls the /my-team endpoint', async () => {
    await getMyTeamWelfareHistories();
    expect(api.get).toHaveBeenCalledWith('/welfare-histories/my-team', {
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
