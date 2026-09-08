import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query';
import {
  getApprovers,
  getLeaderCandidates,
  getRetiredUsers,
  getTeamMembers,
  getUsers,
  placePurgeHold,
  purgeUser,
  rehireUser,
  releasePurgeHold,
  restoreUser,
  retireUser,
  updateMyProfile,
  updateUserBaseDays,
  updateUserDepartment,
  updateUserRole,
  updateUserRoleAndDepartment,
} from '../api/users.js';

// 쿼리 키 규칙: ['users', 서브리소스, ...파라미터]. 접두사(['users'])로 invalidate하면
// list/retired/team-members가 한 번에 무효화된다 (leaves/welfare와 동일한 전략).
// 단, 내 정보(PATCH /me)는 이 목록들의 리소스가 아니라 '로그인 유저(auth)' 리소스라 별도로 다룬다.
// size도 키에 넣는다 (리뷰 F-1) — 같은 검색어·역할·페이지라도 size가 다르면 다른 결과다.
// size가 키에 없으면 크기가 다른 두 화면이 같은 캐시를 공유해 뒤쪽 목록이 조용히 잘린다.
const userKeys = {
  list: (keyword, role, page, size) => ['users', 'list', keyword || 'ALL', role || 'ALL', page, size],
  teamMembers: ['users', 'team-members'],
  approvers: ['users', 'approvers'],
  leaderCandidates: ['users', 'leader-candidates'],
  retired: (page, size) => ['users', 'retired', page, size],
};

// 서브 승인자 후보 (GET /api/users/approvers) — 연차 신청 패널·복리후생 신청 모달이 함께 쓴다.
// 팀장 승격·퇴직이 있어야 바뀌는 값이라 자주 조회할 이유가 없다.
// 이 훅이 constants/approvers.js 하드코딩을 대체했다 (리뷰 F-4) — 그 파일은 DB 행의 사본이라
// 한쪽만 바뀌면 화면과 실제 승인자가 어긋났다.
export function useApprovers() {
  return useQuery({
    queryKey: userKeys.approvers,
    queryFn: getApprovers,
    staleTime: 1000 * 60 * 5,
  });
}

// 팀장 후보 (GET /api/users/leader-candidates, SYSTEM_ADMIN 전용) — 부서 관리의 팀장 드롭다운.
// useApprovers와 조건이 같지만 본인을 제외하지 않아 별도 훅이다. 서버가 지정 가능 여부를
// 같은 기준으로 검증하므로, 여기 담긴 사람은 전부 실제로 팀장이 될 수 있다.
export function useLeaderCandidates({ enabled = true } = {}) {
  return useQuery({
    queryKey: userKeys.leaderCandidates,
    queryFn: getLeaderCandidates,
    staleTime: 1000 * 60 * 5,
    enabled,
  });
}

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

// 내 정보 수정 뮤테이션 — 성공 시 ['auth']를 무효화하면 끝이다.
// 예전에는 localStorage의 userInfo에 name·birthDay를 손으로 병합했다. 사이드바가 react-query가
// 아니라 localStorage를 직접 읽었기 때문인데, F-7에서 사이드바가 useCurrentUser를 쓰게 되면서
// 그 병합이 필요 없어졌다 — useCurrentUser가 새 응답을 받으면 localStorage까지 함께 맞춘다.
// 필드를 골라 병합하는 코드가 남아 있으면 응답에 필드가 늘 때 한쪽만 갱신되는 종류의 결함이 생긴다.
export function useUpdateMyProfile() {
  const queryClient = useQueryClient();
  return useMutation({
    mutationFn: updateMyProfile,
    onSuccess: () => queryClient.invalidateQueries({ queryKey: ['auth'] }),
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

// 역할·부서 동시 변경 뮤테이션 — 팀장 교체는 사용자와 부서 결재선,
// 기존 팀장의 대기 결재를 함께 바꿀 수 있어 관련 캐시를 전부 새로 받는다.
export function useUpdateUserRoleAndDepartment() {
  const queryClient = useQueryClient();
  return useMutation({
    mutationFn: ({ id, role, departmentId }) =>
      updateUserRoleAndDepartment(id, { role, departmentId }),
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: ['users'] });
      queryClient.invalidateQueries({ queryKey: ['departments'] });
      queryClient.invalidateQueries({ queryKey: ['leaves'] });
      queryClient.invalidateQueries({ queryKey: ['welfare'] });
    },
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

// 퇴직 복구 뮤테이션 — 재직 상태로 되돌린다 (POST /api/users/{id}/restore).
// 무효화 범위가 퇴직보다 좁다: 복구는 is_active·retired_at만 되돌리고 팀장직·결재 이관은
// 손대지 않으므로 부서·결재함 캐시는 낡지 않는다. 대신 재직·퇴직 두 목록이 함께 바뀐다.
export function useRestoreUser() {
  const queryClient = useQueryClient();
  return useMutation({
    mutationFn: restoreUser,
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: ['users'] });
    },
  });
}

// 재입사 처리 뮤테이션 — 퇴직 복구와 달리 근속을 새로 시작한다 (POST /api/users/{id}/rehire).
// 서버가 이전 근속의 살아 있는 신청을 취소하고 부서·결재선도 새로 배정하므로 무효화 범위가
// 퇴직 처리와 같다(F-5) — 재직·퇴직 목록, 결재함, 부서 목록이 함께 낡는다.
export function useRehireUser() {
  const queryClient = useQueryClient();
  return useMutation({
    mutationFn: ({ id, hireDate, departmentId }) => rehireUser(id, { hireDate, departmentId }),
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: ['users'] });
      queryClient.invalidateQueries({ queryKey: ['leaves'] });
      queryClient.invalidateQueries({ queryKey: ['welfare'] });
      queryClient.invalidateQueries({ queryKey: ['departments'] });
    },
  });
}

// 퇴직자 개인정보 수동 파기 뮤테이션 (POST /api/users/{id}/purge). 퇴직 목록의 파기 상태·배지가
// 함께 바뀌므로 'users' 접두사로 무효화하면 충분하다 — 재직 목록은 대상이 아니라 영향이 없다.
export function usePurgeUser() {
  const queryClient = useQueryClient();
  return useMutation({
    mutationFn: purgeUser,
    onSuccess: () => queryClient.invalidateQueries({ queryKey: ['users'] }),
  });
}

// 퇴직자 개인정보 파기 보류 설정 뮤테이션 (POST /api/users/{id}/purge-hold, 사유 필수)
export function usePlacePurgeHold() {
  const queryClient = useQueryClient();
  return useMutation({
    mutationFn: ({ id, reason }) => placePurgeHold(id, { reason }),
    onSuccess: () => queryClient.invalidateQueries({ queryKey: ['users'] }),
  });
}

// 퇴직자 개인정보 파기 보류 해제 뮤테이션 (DELETE /api/users/{id}/purge-hold)
export function useReleasePurgeHold() {
  const queryClient = useQueryClient();
  return useMutation({
    mutationFn: releasePurgeHold,
    onSuccess: () => queryClient.invalidateQueries({ queryKey: ['users'] }),
  });
}
