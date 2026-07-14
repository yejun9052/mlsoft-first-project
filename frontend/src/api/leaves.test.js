import { beforeEach, describe, expect, it, vi } from 'vitest';
import api from './index.js';
import { applyLeave, getMyLeaves, processApproval } from './leaves.js';

vi.mock('./index.js', () => ({
  default: { get: vi.fn(), post: vi.fn() },
}));

beforeEach(() => {
  api.get.mockReset();
  api.post.mockReset();
});

describe('api/leaves.js', () => {
  it('applyLeave posts to /leaves with subApproverId normalized to null when absent', async () => {
    api.post.mockResolvedValue({ data: { data: { id: 1 } } });
    await applyLeave({ leaveType: 'ANNUAL', dates: ['2026-08-03'], reason: '휴식' });
    expect(api.post).toHaveBeenCalledWith('/leaves', {
      leaveType: 'ANNUAL',
      dates: ['2026-08-03'],
      reason: '휴식',
      subApproverId: null,
    });
  });

  it('getMyLeaves calls /leaves/me with paging params', async () => {
    api.get.mockResolvedValue({ data: { data: { content: [] } } });
    await getMyLeaves({ status: 'PENDING', page: 1 });
    expect(api.get).toHaveBeenCalledWith('/leaves/me', {
      params: { status: 'PENDING', page: 1, size: 20, sort: 'createdAt,desc' },
    });
  });

  it('processApproval posts approved/comment to /leaves/{id}/approval', async () => {
    api.post.mockResolvedValue({ data: { data: {} } });
    await processApproval(42, { approved: true, comment: '확인' });
    expect(api.post).toHaveBeenCalledWith('/leaves/42/approval', { approved: true, comment: '확인' });
  });
});
