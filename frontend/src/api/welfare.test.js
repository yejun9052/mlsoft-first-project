import { beforeEach, describe, expect, it, vi } from 'vitest';
import api from './index.js';
import { applyWelfare, getAllWelfarePolicies, getMyWelfareRequests, processWelfareApproval } from './welfare.js';

vi.mock('./index.js', () => ({
  default: { get: vi.fn(), post: vi.fn() },
}));

beforeEach(() => {
  api.get.mockReset();
  api.post.mockReset();
});

describe('api/welfare.js', () => {
  it('applyWelfare posts to /welfare-requests with subApproverId normalized to null when absent', async () => {
    api.post.mockResolvedValue({ data: { data: { id: 1 } } });
    await applyWelfare({ policyId: 5, reason: '형 결혼식 참석' });
    expect(api.post).toHaveBeenCalledWith('/welfare-requests', {
      policyId: 5,
      reason: '형 결혼식 참석',
      subApproverId: null,
    });
  });

  it('getAllWelfarePolicies calls /welfare-policies/all with no params', async () => {
    api.get.mockResolvedValue({ data: { data: [] } });
    await getAllWelfarePolicies();
    expect(api.get).toHaveBeenCalledWith('/welfare-policies/all');
  });

  it('getMyWelfareRequests calls /welfare-requests/me with paging params', async () => {
    api.get.mockResolvedValue({ data: { data: { content: [] } } });
    await getMyWelfareRequests({ page: 1 });
    expect(api.get).toHaveBeenCalledWith('/welfare-requests/me', {
      params: { page: 1, size: 20, sort: 'createdAt,desc' },
    });
  });

  it('processWelfareApproval posts approved/comment to /welfare-requests/{id}/approval', async () => {
    api.post.mockResolvedValue({ data: { data: {} } });
    await processWelfareApproval(42, { approved: true, comment: '확인' });
    expect(api.post).toHaveBeenCalledWith('/welfare-requests/42/approval', { approved: true, comment: '확인' });
  });
});
