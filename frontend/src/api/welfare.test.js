import { beforeEach, describe, expect, it, vi } from 'vitest';
import api from './index.js';
import {
  applyWelfare,
  createWelfarePolicy,
  deactivateWelfarePolicy,
  getAllWelfarePolicies,
  getMyWelfareRequests,
  getWelfarePolicies,
  processWelfareApproval,
  updateWelfarePolicy,
} from './welfare.js';

vi.mock('./index.js', () => ({
  default: { get: vi.fn(), post: vi.fn(), patch: vi.fn(), delete: vi.fn() },
}));

beforeEach(() => {
  api.get.mockReset();
  api.post.mockReset();
  api.patch.mockReset();
  api.delete.mockReset();
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

  // ── 정책 관리 (SYSTEM_ADMIN 전용) ──────────────────────────────────────────

  it('getWelfarePolicies calls /welfare-policies with keyword·category·paging', async () => {
    api.get.mockResolvedValue({ data: { data: { content: [] } } });
    await getWelfarePolicies({ keyword: '결혼', page: 2 });
    expect(api.get).toHaveBeenCalledWith('/welfare-policies', {
      params: { keyword: '결혼', category: undefined, page: 2, size: 10 },
    });
  });

  it('createWelfarePolicy posts the whole policy body', async () => {
    api.post.mockResolvedValue({ data: { data: { id: 7 } } });
    const body = {
      category: '결혼',
      target: 'SELF',
      defaultDays: '7.0',
      defaultEvidence: '청첩장',
      description: '본인 결혼',
    };
    await createWelfarePolicy(body);
    expect(api.post).toHaveBeenCalledWith('/welfare-policies', body);
  });

  it('updateWelfarePolicy patches /welfare-policies/{id} — id는 body에 넣지 않는다', async () => {
    api.patch.mockResolvedValue({ data: { data: { id: 7 } } });
    const body = {
      category: '결혼',
      target: 'CHILD',
      defaultDays: '1.0',
      defaultEvidence: '청첩장',
      description: '자녀 결혼',
    };
    await updateWelfarePolicy(7, body);
    expect(api.patch).toHaveBeenCalledWith('/welfare-policies/7', body);
  });

  it('deactivateWelfarePolicy deletes /welfare-policies/{id} — 소프트 삭제라 본문이 없다', async () => {
    api.delete.mockResolvedValue({ data: { data: null } });
    await deactivateWelfarePolicy(7);
    expect(api.delete).toHaveBeenCalledWith('/welfare-policies/7');
  });
});
