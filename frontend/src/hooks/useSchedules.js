import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query';
import {
  createSchedule,
  deleteSchedule,
  getMySchedules,
  getScheduleCalendar,
  getScheduleTypes,
  updateSchedule,
} from '../api/schedules.js';

// 쿼리 키 규칙: ['schedules', 서브리소스, ...파라미터] — 연차(['leaves'])와 접두사를 분리해
// 한쪽만 무효화할 수 있게 둔다. 캘린더 화면은 둘을 함께 그리지만 갱신 사유는 서로 다르다.
//
// 서버 응답을 바꾸는 파라미터는 전부 키에 넣는다 (리뷰 F-1).
const scheduleKeys = {
  calendar: (year, month, keyword, departmentId) =>
    ['schedules', 'calendar', year, month, keyword || 'ALL', departmentId || 'ALL'],
  me: (page, size) => ['schedules', 'me', page, size],
  types: ['schedules', 'types'],
};

// 캘린더용 개인 일정 (GET /api/schedules/calendar)
export function useScheduleCalendar(year, month, { keyword, departmentId } = {}) {
  return useQuery({
    queryKey: scheduleKeys.calendar(year, month, keyword, departmentId),
    queryFn: () => getScheduleCalendar({ year, month, keyword, departmentId }),
    // 연차 캘린더와 같은 이유 — 검색어 타이핑 중 깜박임 방지
    placeholderData: (previous) => previous,
  });
}

// 내 일정 목록 (GET /api/schedules/me)
export function useMySchedules({ page = 0, size = 20 } = {}) {
  return useQuery({
    queryKey: scheduleKeys.me(page, size),
    queryFn: () => getMySchedules({ page, size }),
  });
}

// 선택 가능한 일정 종류 — 거의 변하지 않으므로 오래 캐시한다.
// 서버가 라벨까지 내려주므로 종류를 추가해도 프론트 수정이 필요 없다.
export function useScheduleTypes() {
  return useQuery({
    queryKey: scheduleKeys.types,
    queryFn: getScheduleTypes,
    staleTime: 1000 * 60 * 60,
  });
}

// 일정 등록 — 승인이 없어 성공 즉시 캘린더에 반영된다
export function useCreateSchedule() {
  const queryClient = useQueryClient();
  return useMutation({
    mutationFn: createSchedule,
    onSuccess: () => queryClient.invalidateQueries({ queryKey: ['schedules'] }),
  });
}

export function useUpdateSchedule() {
  const queryClient = useQueryClient();
  return useMutation({
    mutationFn: updateSchedule,
    onSuccess: () => queryClient.invalidateQueries({ queryKey: ['schedules'] }),
  });
}

export function useDeleteSchedule() {
  const queryClient = useQueryClient();
  return useMutation({
    mutationFn: deleteSchedule,
    onSuccess: () => queryClient.invalidateQueries({ queryKey: ['schedules'] }),
  });
}
