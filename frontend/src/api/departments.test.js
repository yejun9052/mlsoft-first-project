import { beforeEach, describe, expect, it, vi } from 'vitest';
import api from './index.js';
import { createDepartment, deactivateDepartment, updateDepartment } from './departments.js';

vi.mock('./index.js', () => ({
  default: { get: vi.fn(), post: vi.fn(), put: vi.fn(), delete: vi.fn() },
}));

beforeEach(() => {
  api.post.mockReset();
  api.put.mockReset();
  api.delete.mockReset();
});

describe('api/departments.js', () => {
  it('createDepartment posts to /departments with leaderId·parentId normalized to null when absent', async () => {
    api.post.mockResolvedValue({ data: { data: { id: 1 } } });
    await createDepartment({ name: '개발팀', description: '서비스 개발' });
    expect(api.post).toHaveBeenCalledWith('/departments', {
      name: '개발팀',
      description: '서비스 개발',
      leaderId: null,
      parentId: null,
    });
  });

  it('createDepartment keeps leaderId·parentId when given', async () => {
    api.post.mockResolvedValue({ data: { data: { id: 1 } } });
    await createDepartment({ name: '플랫폼팀', description: '공통 플랫폼', leaderId: 7, parentId: 2 });
    expect(api.post).toHaveBeenCalledWith('/departments', {
      name: '플랫폼팀',
      description: '공통 플랫폼',
      leaderId: 7,
      parentId: 2,
    });
  });

  it('updateDepartment puts full body to /departments/{id} — 팀장 미지정은 null(공석)로 전달', async () => {
    api.put.mockResolvedValue({ data: { data: { id: 3 } } });
    await updateDepartment(3, { name: '개발팀', description: '서비스 개발', leaderId: '', parentId: '' });
    expect(api.put).toHaveBeenCalledWith('/departments/3', {
      name: '개발팀',
      description: '서비스 개발',
      leaderId: null,
      parentId: null,
    });
  });

  it('deactivateDepartment deletes /departments/{id}', async () => {
    api.delete.mockResolvedValue({ data: { data: null } });
    await deactivateDepartment(5);
    expect(api.delete).toHaveBeenCalledWith('/departments/5');
  });
});
