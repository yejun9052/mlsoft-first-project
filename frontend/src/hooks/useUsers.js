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
// size도 키에 넣는다 (리뷰 F-1) — AdminDepartmentsPage는 팀장 후보를 size=200으로,
// AdminMembersPage는 목록을 기본 size=50으로 조회한다. size가 키에 없으면 검색어·역할·페이지가
// 같을 때 두 화면이 같은 캐시를 공유해 **팀장 후보가 50명에서 잘린다.**
const userKeys = {
  list: (keyword, role, page, size) => ['users', 'list', keyword || 'ALL', role || 'ALL', page, size],
  teamMembers: ['users', 'team-members'],
  retired: (page, size) => ['users', 'retired', page, size],
};

// 전체 사용자 목록 — keyword·role 필터 (GET /api/users, SYSTEM_ADMIN 전용)
export function useUsers({ keyword, role, page = 0, size = 50, enabled = true } = {}) {
  return useQuery({
    queryKey: userKeys.list(keyword, role, page, size),
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
    queryKey: userKeys.retired(page, size),
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
// 연차를 직접 바꾸면 그 사원의 잔여 연차 요약도 바뀐다 — 본인 계정을 수정한 경우 내 화면이 낡는다 (F-5)
export function useUpdateUserBaseDays() {
  const queryClient = useQueryClient();
  return useMutation({
    mutationFn: ({ id, baseDays }) => updateUserBaseDays(id, { baseDays }),
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: ['users'] });
      queryClient.invalidateQueries({ queryKey: ['leaves', 'summary'] });
    },
  });
}

// 퇴직 처리 뮤테이션 — 팀장 해제·대기 결재 이관은 서버가 처리 (POST /api/users/{id}/retire).
// 서버가 대기 결재를 다른 승인자에게 이관하고 팀장을 해제하므로, 결재함·부서 목록도 함께 낡는다 (F-5).
export function useRetireUser() {
  const queryClient = useQueryClient();
  return useMutation({
    mutationFn: retireUser,
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: ['users'] });
      queryClient.invalidateQueries({ queryKey: ['leaves'] });
      queryClient.invalidateQueries({ queryKey: ['welfare'] });
      queryClient.invalidateQueries({ queryKey: ['departments'] });
    },
  });
}
