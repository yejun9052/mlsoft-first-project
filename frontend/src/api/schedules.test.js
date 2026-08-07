import { beforeEach, describe, expect, it, vi } from 'vitest';
import api from './index.js';
import { createSchedule, deleteSchedule, getScheduleCalendar, updateSchedule } from './schedules.js';

vi.mock('./index.js', () => ({
  default: { get: vi.fn(), post: vi.fn(), put: vi.fn(), delete: vi.fn() },
}));

beforeEach(() => {
  api.get.mockReset();
  api.post.mockReset();
  api.put.mockReset();
  api.delete.mockReset();
});

describe('api/schedules.js', () => {
  it('createSchedule posts to /schedules with memo normalized to null when blank', async () => {
    api.post.mockResolvedValue({ data: { data: { id: 1 } } });
    await createSchedule({ scheduleType: 'FIELD_WORK', dates: ['2026-08-10'], memo: '' });
    expect(api.post).toHaveBeenCalledWith('/schedules', {
      scheduleType: 'FIELD_WORK',
      dates: ['2026-08-10'],
      memo: null,
    });
  });

  it('updateSchedule puts to /schedules/{id}', async () => {
    api.put.mockResolvedValue({ data: { data: { id: 7 } } });
    await updateSchedule({ id: 7, scheduleType: 'REMOTE', dates: ['2026-08-11'], memo: '재택' });
    expect(api.put).toHaveBeenCalledWith('/schedules/7', {
      scheduleType: 'REMOTE',
      dates: ['2026-08-11'],
      memo: '재택',
    });
  });

  it('deleteSchedule deletes /schedules/{id}', async () => {
    api.delete.mockResolvedValue({ data: { data: null } });
    await deleteSchedule(7);
    expect(api.delete).toHaveBeenCalledWith('/schedules/7');
  });

  // 빈 검색어를 그대로 보내면 axios가 keyword= 로 직렬화하고, 서버는 "설정됨+빈 문자열"을 받는다.
  // undefined여야 파라미터 자체가 빠져 "필터 없음"이 된다 (O-1에서 겪은 것과 같은 함정).
  it('getScheduleCalendar omits blank keyword/departmentId instead of sending empty strings', async () => {
    api.get.mockResolvedValue({ data: { data: [] } });
    await getScheduleCalendar({ year: 2026, month: 8, keyword: '', departmentId: '' });
    expect(api.get).toHaveBeenCalledWith('/schedules/calendar', {
      params: { year: 2026, month: 8, keyword: undefined, departmentId: undefined },
    });
  });

  it('getScheduleCalendar passes through keyword and departmentId when present', async () => {
    api.get.mockResolvedValue({ data: { data: [] } });
    await getScheduleCalendar({ year: 2026, month: 8, keyword: '박민수', departmentId: 3 });
    expect(api.get).toHaveBeenCalledWith('/schedules/calendar', {
      params: { year: 2026, month: 8, keyword: '박민수', departmentId: 3 },
    });
  });
});
