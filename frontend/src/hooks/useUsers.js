import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query';
import {
  getRetiredUsers,
  getTeamMembers,
  getUsers,
  retireUser,
  updateMyProfile,
  updateUserBaseDays,
  updateUserDepartment,
  updateUserRole,
} from '../api/users.js';

// 쿼리 키 규칙: ['users', 서브리소스, ...파라미터]. 접두사(['users'])로 invalidate하면
// list/retired/team-members가 한 번에 무효화된다 (leaves/welfare와 동일한 전략).
// 단, 내 정보(PATCH /me)는 이 목록들의 리소스가 아니라 '로그인 유저(auth)' 리소스라 별도로 다룬다.
const userKeys = {
  list: (keyword, role, page) => ['users', 'list', keyword || 'ALL', role || 'ALL', page],
  teamMembers: ['users', 'team-members'],
  retired: (page) => ['users', 'retired', page],
};

// 전체 사용자 목록 — keyword·role 필터 (GET /api/users, SYSTEM_ADMIN 전용)
export function useUsers({ keyword, role, page = 0, size = 50, enabled = true } = {}) {
  return useQuery({
    queryKey: userKeys.list(keyword, role, page),
    queryFn: () => getUsers({ keyword, role, page, size }),
    enabled,
  });
}

// 내 부서 팀원 목록 — 서버가 요청자 부서로 스코프 (GET /api/users/team-members)
export function useTeamMembers() {
  return useQuery({ queryKey: userKeys.teamMembers, queryFn: getTeamMembers });
}

// 퇴직자 목록 (GET /api/users/retired, SYSTEM_ADMIN 전용)
export function useRetiredUsers({ page = 0, size = 50, enabled = true } = {}) {
  return useQuery({
    queryKey: userKeys.retired(page),
    queryFn: () => getRetiredUsers({ page, size }),
    enabled,
  });
}

// 내 정보 수정 뮤테이션 — 성공 시 useCurrentUser(['auth','me'])를 무효화해 대시보드 등 다른 화면의
// 프로필 표시도 갱신한다. 사이드바는 react-query가 아니라 localStorage(userInfo)를 직접 읽으므로,
// onboarded 등 다른 필드를 잃지 않도록 name·birthDay만 병합해 함께 갱신해준다.
export function useUpdateMyProfile() {
  const queryClient = useQueryClient();
  return useMutation({
    mutationFn: updateMyProfile,
    onSuccess: (data) => {
      try {
        const stored = JSON.parse(localStorage.getItem('userInfo'));
        if (stored) {
          localStorage.setItem(
            'userInfo',
            JSON.stringify({ ...stored, name: data.name, birthDay: data.birthDay }),
          );
        }
      } catch {
        // localStorage 파싱 실패는 무시 — 다음 로그인 시 정상화됨
      }
      queryClient.invalidateQueries({ queryKey: ['auth'] });
    },
  });
}

// 권한 변경 뮤테이션 (PATCH /api/users/{id}/role, SYSTEM_ADMIN 전용)
export function useUpdateUserRole() {
  const queryClient = useQueryClient();
  return useMutation({
    mutationFn: ({ id, role }) => updateUserRole(id, { role }),
    onSuccess: () => queryClient.invalidateQueries({ queryKey: ['users'] }),
  });
}

// 부서 변경 뮤테이션 (PATCH /api/users/{id}/department, SYSTEM_ADMIN 전용)
export function useUpdateUserDepartment() {
  const queryClient = useQueryClient();
  return useMutation({
    mutationFn: ({ id, departmentId }) => updateUserDepartment(id, { departmentId }),
    onSuccess: () => queryClient.invalidateQueries({ queryKey: ['users'] }),
  });
}

// 연차 기본일수 직접 설정 뮤테이션 (PATCH /api/users/{id}/base-days, SYSTEM_ADMIN 전용)
// 이번 라운드의 3개 화면(Team/MyInfo/AdminMembers)에는 호출부가 없다 — AdminPolicyPage(별도 진행 중)용으로
// api 레이어와 함께 미리 맞춰둔 훅.
export function useUpdateUserBaseDays() {
  const queryClient = useQueryClient();
  return useMutation({
    mutationFn: ({ id, baseDays }) => updateUserBaseDays(id, { baseDays }),
    onSuccess: () => queryClient.invalidateQueries({ queryKey: ['users'] }),
  });
}

// 퇴직 처리 뮤테이션 — 팀장 해제·대기 결재 이관은 서버가 처리 (POST /api/users/{id}/retire)
export function useRetireUser() {
  const queryClient = useQueryClient();
  return useMutation({
    mutationFn: retireUser,
    onSuccess: () => queryClient.invalidateQueries({ queryKey: ['users'] }),
  });
}
