import { useEffect, useState } from 'react';
import toast from 'react-hot-toast';
import {
  Search,
  UserMinus,
  UserCheck,
  UserPlus,
  Check,
  X,
  Trash2,
  PauseCircle,
  PlayCircle,
} from 'lucide-react';
import { ROLE, ROLE_LABEL } from '../constants/roles.js';
import PageHeader from '../components/ui/PageHeader.jsx';
import Tabs from '../components/ui/Tabs.jsx';
import FilterGroup from '../components/ui/FilterGroup.jsx';
import Chip from '../components/ui/Chip.jsx';
import TextInput from '../components/ui/TextInput.jsx';
import Textarea from '../components/ui/Textarea.jsx';
import TableCard from '../components/ui/TableCard.jsx';
import Table, { THead, Th, TR, Td } from '../components/ui/Table.jsx';
import Avatar from '../components/ui/Avatar.jsx';
import StatusBadge from '../components/ui/StatusBadge.jsx';
import InlineSelect from '../components/ui/InlineSelect.jsx';
import IconButton from '../components/ui/IconButton.jsx';
import ConfirmDialog from '../components/ui/ConfirmDialog.jsx';
import Field from '../components/ui/Field.jsx';
import Select from '../components/ui/Select.jsx';
import Pagination from '../components/ui/Pagination.jsx';
import {
  usePlacePurgeHold,
  usePurgeUser,
  useRehireUser,
  useReleasePurgeHold,
  useRestoreUser,
  useRetiredUsers,
  useRetireUser,
  useUpdateUserDepartment,
  useUpdateUserRole,
  useUpdateUserRoleAndDepartment,
  useUsers,
} from '../hooks/useUsers.js';
import { useDepartments } from '../hooks/useDepartments.js';
import { departmentOptionLabel, orderByHierarchy } from '../utils/departmentTree.js';
import { usePageClamp } from '../hooks/usePageClamp.js';
import {
  useApproveOnboarding,
  useCurrentUser,
  usePendingOnboardings,
  useRejectOnboarding,
} from '../hooks/useAuth.js';

// 탭 정의 (재직 / 퇴직 / 온보딩 승인)
const TAB_ACTIVE = 'active';
const TAB_RETIRED = 'retired';
const TAB_ONBOARDING = 'onboarding';

// 세 탭이 같은 값을 쓴다 — 탭을 옮길 때 한 화면에 들어오는 행 수가 달라지면 목록이 튄다
const PAGE_SIZE = 10;

// 역할 필터 칩 (전체 + 3개 역할)
const ROLE_FILTERS = [
  { value: 'ALL', label: '전체' },
  { value: ROLE.EMPLOYEE, label: ROLE_LABEL[ROLE.EMPLOYEE] },
  { value: ROLE.TEAM_LEADER, label: ROLE_LABEL[ROLE.TEAM_LEADER] },
  { value: ROLE.SYSTEM_ADMIN, label: ROLE_LABEL[ROLE.SYSTEM_ADMIN] },
];

// 역할 셀렉트의 선택지 (전체 필터의 'ALL'은 제외)
const ROLE_OPTIONS = [ROLE.EMPLOYEE, ROLE.TEAM_LEADER, ROLE.SYSTEM_ADMIN];

// 역할 배지·셀렉트의 색 — 사원만 무채색, 권한이 있는 둘은 강조색 (기존 StatusBadge 규칙 유지)
function roleTone(role) {
  return role === ROLE.EMPLOYEE ? 'muted' : 'accent';
}

// 퇴직일 원문은 유지하고 경과 기간만 덧붙인다 — 날짜 자체를 상대 표현으로 바꾸면 감사 시점을 잃는다.
// 경과 연·월은 서버가 계산해 내려준다 (UserResponse.elapsedYears/elapsedMonths) — 파기 가능 여부
// 판정과 같은 기준을 써야 하므로 클라이언트에서 dayjs로 다시 세지 않는다.
function formatRetiredAt(retiredAt, elapsedYears, elapsedMonths) {
  if (!retiredAt) return '-';
  return `${retiredAt} (${formatElapsedPeriod(elapsedYears, elapsedMonths)})`;
}

// "2년 4개월" 형태 — 연차가 0이면 개월만, 둘 다 0이면 퇴직 당일이므로 "0개월"
function formatElapsedPeriod(years, months) {
  const y = years || 0;
  const m = months || 0;
  if (y === 0 && m === 0) return '0개월';
  return [y > 0 ? `${y}년` : null, m > 0 ? `${m}개월` : null].filter(Boolean).join(' ');
}

/**
 * 역할 변경 확인 문구 — **역할 변경은 예외 없이 되묻는다.**
 *
 * 표에서 바로 고르게 되면서 한 번의 클릭으로 권한이 바뀐다. 그런데 세 방향 모두 조용히
 * 넘어가면 안 되는 결과가 딸려 온다:
 *   - 본인 강등: 다음 요청부터 이 화면이 잠긴다 (인터셉터가 매 요청 DB role로 권한을 다시 세운다).
 *     마지막 관리자 보호(S-2)는 관리자가 2명 이상이면 걸리지 않아 서버가 막아 주지 않는다
 *   - 총관리자 부여: 전 사원의 연차·권한·설정을 만질 수 있게 된다
 *   - 사원으로 강등: 맡고 있던 부서의 팀장직이 해제되고 대기 결재가 다른 승인자에게 넘어간다
 *     (UserService.changeRole, 리뷰 I-5a) — 화면에 안 적으면 관리자가 알 방법이 없다
 *
 * 팀장 부여는 tone이 danger가 아니다. 전부 빨갛게 칠하면 "이건 위험"이라는 신호가 죽는다.
 * 대신 **어느 부서의 팀장이 되는지를 문장에 박는다** — 팀장 지정은 그 부서의 결재선을 바꾸는
 * 조작인데 "역할을 팀장으로 변경합니다"라고만 하면 어느 팀 얘기인지 알 수 없다 (2026-08-16).
 */
function roleChangeNotice({ user, nextRole, isSelf, departmentName, currentLeaderName }) {
  const head = `${user.name}님의 역할을 ${ROLE_LABEL[user.role]}에서 ${ROLE_LABEL[nextRole]}(으)로 변경합니다.`;

  if (isSelf) {
    return {
      title: '내 역할 변경',
      tone: 'danger',
      body: `${head} 저장하는 순간 관리자 화면에 더 이상 들어올 수 없고, 되돌리려면 다른 총관리자에게 요청해야 합니다. 정말 변경하시겠습니까?`,
    };
  }
  if (nextRole === ROLE.SYSTEM_ADMIN) {
    return {
      title: '총관리자 권한 부여',
      tone: 'danger',
      body: `${head} 총관리자는 전 사원의 연차·권한·부서와 시스템 설정을 바꿀 수 있습니다. 정말 변경하시겠습니까?`,
    };
  }
  if (nextRole === ROLE.EMPLOYEE) {
    return {
      title: '역할 강등',
      tone: 'danger',
      body: `${head} 맡고 있던 부서의 팀장직이 해제되고, 그 사람에게 걸려 있던 대기 결재는 다른 승인자에게 넘어갑니다. 정말 변경하시겠습니까?`,
    };
  }
  // 팀장 지정 — 역할 변경이자 그 부서 결재선의 변경이다. 부서 이름을 반드시 말한다.
  const replaced = currentLeaderName
    ? ` 현재 팀장인 ${currentLeaderName}님은 사원으로 내려가고, 그분 앞으로 걸려 있던 대기 결재는 다른 승인자에게 넘어갑니다.`
    : '';
  return {
    title: '팀장 지정',
    tone: 'default',
    body: `${user.name}님을 ${departmentName} 팀장으로 정말 지정하시겠습니까?`
      + ` 지정하면 ${departmentName}의 연차·복리후생 신청이 기본으로 ${user.name}님에게 갑니다.`
      + replaced,
  };
}

// 구성원 관리 — 재직/퇴직 조회 + 역할·부서 변경, 퇴직 처리 (docs/05 §관리자)
// "구성원 추가"는 회원가입/OAuth로만 생성되고 관리자 생성 API가 없어 버튼 자체를 두지 않는다.
export default function AdminMembersPage() {
  const [tab, setTab] = useState(TAB_ACTIVE);
  const [searchInput, setSearchInput] = useState('');
  const [keyword, setKeyword] = useState('');
  const [roleFilter, setRoleFilter] = useState('ALL');

  // 검색어 디바운스(300ms) — 키 입력마다 서버로 재조회하지 않도록
  useEffect(() => {
    const timer = setTimeout(() => setKeyword(searchInput.trim()), 300);
    return () => clearTimeout(timer);
  }, [searchInput]);

  // 페이지 위치는 탭마다 따로 둔다 — 하나로 합치면 퇴직 3페이지를 보다 재직으로 돌아왔을 때
  // 있지도 않은 3페이지를 요청해 빈 표가 뜬다
  const [activePage, setActivePage] = useState(0);
  const [retiredPage, setRetiredPage] = useState(0);
  const [onboardingPage, setOnboardingPage] = useState(0);

  // 검색어·역할 필터가 바뀌면 결과 집합 자체가 달라진다. 페이지를 그대로 두면
  // 조건에 맞는 사람이 5명인데 3페이지를 보고 있어 "검색 결과 없음"이 뜬다.
  useEffect(() => {
    setActivePage(0);
  }, [keyword, roleFilter]);

  // 탭 배지가 필터와 무관하게 항상 정확한 건수를 보여줄 수 있도록 재직·퇴직 둘 다 항상 조회한다
  // (ApprovalsPage가 탭과 무관하게 대기 목록을 항상 조회하는 것과 동일한 전략).
  const activeQuery = useUsers({
    keyword,
    role: roleFilter === 'ALL' ? undefined : roleFilter,
    page: activePage,
    size: PAGE_SIZE,
  });
  const retiredQuery = useRetiredUsers({ page: retiredPage, size: PAGE_SIZE });
  // 파기 대상 배지는 표시 중인 페이지가 아니라 퇴직자 전체를 기준으로 세야 한다 — 목록이
  // PAGE_SIZE(10)씩 페이징되므로 현재 페이지만 세면 뒤 페이지의 대상자를 놓친다. 그래서 표시용
  // retiredQuery와 별도로, 배지 계산 전용으로 넓게 한 번 더 받는다(leaderCandidates와 같은 이유로
  // 상한 100까지만 정확하다).
  const purgeTargetCountQuery = useRetiredUsers({ page: 0, size: 100 });
  const departmentsQuery = useDepartments();
  // 온보딩 승인 대기 — 배지에 항상 건수를 띄워야 관리자가 잠긴 계정을 놓치지 않는다 (리뷰 S-1)
  const onboardingQuery = usePendingOnboardings({ page: onboardingPage, size: PAGE_SIZE });

  // 이 화면의 동작은 대부분 목록에서 사람을 덜어낸다 — 퇴직·복구·온보딩 승인·반려.
  // 마지막 페이지의 마지막 한 명을 처리하면 그 페이지가 사라지는데, 보정하지 않으면
  // 서버가 빈 목록을 정상 응답으로 주고 화면은 "조회된 구성원이 없습니다"를 띄운다.
  usePageClamp(activePage, setActivePage, activeQuery.data?.page?.totalPages);
  usePageClamp(retiredPage, setRetiredPage, retiredQuery.data?.page?.totalPages);
  usePageClamp(onboardingPage, setOnboardingPage, onboardingQuery.data?.page?.totalPages);
  const approveOnboardingMutation = useApproveOnboarding();
  const rejectOnboardingMutation = useRejectOnboarding();

  const updateRoleMutation = useUpdateUserRole();
  const updateDepartmentMutation = useUpdateUserDepartment();
  const updateRoleAndDepartmentMutation = useUpdateUserRoleAndDepartment();
  const retireMutation = useRetireUser();
  const restoreMutation = useRestoreUser();
  const rehireMutation = useRehireUser();
  const purgeMutation = usePurgeUser();
  const placeHoldMutation = usePlacePurgeHold();
  const releaseHoldMutation = useReleasePurgeHold();

  // 표에서 고치는 중인 셀 하나 — { id, field, value }.
  // 이걸 두는 이유는 낙관적 갱신이 아니라 **저장 중 표시**다. 서버가 거부하면(마지막 관리자 강등 등)
  // 목록 데이터가 그대로라 값이 저절로 원래대로 돌아온다 — 실패를 성공처럼 보여주지 않는다.
  const [pendingEdit, setPendingEdit] = useState(null);
  // 확인을 기다리는 역할 변경 — { user, nextRole, isSelf } (roleChangeNotice 주석)
  const [roleTarget, setRoleTarget] = useState(null);
  // 퇴직 처리 확인
  const [retireTarget, setRetireTarget] = useState(null);
  // 퇴직 복구 확인 — 되살아나지 않는 것이 있어 그대로 실행하지 않는다 (아래 다이얼로그 문구)
  const [restoreTarget, setRestoreTarget] = useState(null);
  // 재입사 처리 — 퇴직 복구와 달리 근속을 새로 시작한다. 재입사일(필수)·부서를 폼에서 받는다
  // (설계-초안/재입사자-처리-설계-2026-09-07 §6). { user, hireDate, departmentId }
  const [rehireTarget, setRehireTarget] = useState(null);
  // 파기 확인 — 되돌릴 수 없으므로 이름을 직접 입력해야 실행 버튼이 열린다 (설계 §7)
  const [purgeTarget, setPurgeTarget] = useState(null);
  const [purgeNameInput, setPurgeNameInput] = useState('');
  // 파기 보류 — 사유 입력을 받는다 (설계 §3, 사유 없는 보류는 잊혀진 데이터가 된다)
  const [holdTarget, setHoldTarget] = useState(null);
  const [holdReasonInput, setHoldReasonInput] = useState('');

  const { data: currentUser } = useCurrentUser();

  const rows = tab === TAB_ACTIVE ? (activeQuery.data?.content ?? []) : (retiredQuery.data?.content ?? []);
  const listPage = tab === TAB_ACTIVE ? activePage : retiredPage;
  const setListPage = tab === TAB_ACTIVE ? setActivePage : setRetiredPage;
  const listPageInfo = tab === TAB_ACTIVE ? activeQuery.data?.page : retiredQuery.data?.page;
  const loading = tab === TAB_ACTIVE ? activeQuery.isLoading : retiredQuery.isLoading;
  // 조회 실패를 "조회된 구성원이 없습니다"로 보여주면 관리자가 계정이 사라졌다고 오해한다 (리뷰 F-6)
  const listError = tab === TAB_ACTIVE ? activeQuery.isError : retiredQuery.isError;
  const retryList = tab === TAB_ACTIVE ? activeQuery.refetch : retiredQuery.refetch;
  const activeDepartments = (departmentsQuery.data ?? []).filter((d) => d.active);
  // 미배정 부서에는 팀장을 둘 수 없다 — 이 목록이 승격 확인 창의 유일한 후보 출처다.
  const leaderAssignableDepartments = activeDepartments.filter((d) => !d.unassigned);

  // 저장 중인 셀만 그 값을 보여주고, 나머지는 서버 목록 값을 그대로 쓴다
  function cellValue(user, field, serverValue) {
    return pendingEdit?.id === user.id && pendingEdit.field === field ? pendingEdit.value : serverValue;
  }
  function isSaving(user, field) {
    return pendingEdit?.id === user.id && pendingEdit.field === field;
  }

  // 역할은 고른 즉시 저장하지 않는다 — 확인을 받고 나서 저장한다 (roleChangeNotice 주석)
  function handleRoleChange(user, nextRole) {
    if (nextRole === user.role) return;

    const department = activeDepartments.find((d) => d.id === user.departmentId);
    const needsDepartment =
      nextRole === ROLE.TEAM_LEADER && (!user.departmentId || department?.unassigned === true);
    setRoleTarget({
      user,
      nextRole,
      // 미배정 여부는 서버가 내려준 부서 플래그로 판단한다 — 이름은 관리자가 바꿀 수 있어 식별자가 아니다.
      needsDepartment,
      departmentId: needsDepartment ? '' : String(user.departmentId),
      // 본인 강등만 특별하다. 총관리자를 유지하는 변경은 잠기지 않으므로 자기 자신이어도 일반 문구다
      isSelf: currentUser?.id === user.id && nextRole !== ROLE.SYSTEM_ADMIN,
      departmentName: department?.name ?? user.departmentName,
      // 교체되는 사람의 이름 — 본인이 이미 팀장이면 교체가 아니므로 비운다
      currentLeaderName:
        department?.leaderId && department.leaderId !== user.id ? department.leaderName : null,
    });
  }

  function handleRoleDepartmentChange(departmentId) {
    const department = activeDepartments.find((d) => d.id === Number(departmentId));
    setRoleTarget((target) => ({
      ...target,
      departmentId,
      departmentName: department?.name,
      currentLeaderName:
        department?.leaderId && department.leaderId !== target.user.id
          ? department.leaderName
          : null,
    }));
  }

  function handleRoleConfirm() {
    if (!roleTarget) return;
    const { user, nextRole, needsDepartment, departmentId } = roleTarget;
    if (needsDepartment && !departmentId) return;

    setRoleTarget(null);
    setPendingEdit({ id: user.id, field: 'role', value: nextRole });

    const options = {
      onSuccess: () =>
        toast.success(`${user.name}님의 역할을 ${ROLE_LABEL[nextRole]}(으)로 변경했습니다.`),
      // 성공이든 실패든 표시를 거둔다 — 실패하면 서버 값이 그대로라 옛 역할로 되돌아간다
      onSettled: () => setPendingEdit(null),
    };

    if (needsDepartment) {
      updateRoleAndDepartmentMutation.mutate(
        {
          id: user.id,
          role: nextRole,
          departmentId: Number(departmentId),
        },
        options,
      );
      return;
    }

    updateRoleMutation.mutate({ id: user.id, role: nextRole }, options);
  }

  function handleDeptChange(user, nextDepartmentId) {
    if (!nextDepartmentId || Number(nextDepartmentId) === user.departmentId) return;
    setPendingEdit({ id: user.id, field: 'department', value: nextDepartmentId });
    updateDepartmentMutation.mutate(
      { id: user.id, departmentId: Number(nextDepartmentId) },
      {
        onSuccess: () => toast.success(`${user.name}님의 부서를 변경했습니다.`),
        onSettled: () => setPendingEdit(null),
      },
    );
  }

  // 배정된 부서가 비활성으로 바뀐 사원도 있다. 활성 목록만 내려주면 그 값이 선택지에 없어
  // 브라우저가 첫 항목을 대신 보여주고, 관리자는 부서가 조용히 바뀐 것으로 오해한다.
  // 그래서 현재 배정된 부서는 비활성이라도 선택지에 남긴다.
  //
  // 순서는 계층순이다 — 상위 부서 바로 아래에 그 하위 부서가 온다. 비활성 부서는 계층에
  // 끼우지 않고 맨 뒤에 붙인다. 예외 항목이 목록 중간에 섞이면 왜 거기 있는지 알 수 없다.
  function departmentOptions(user) {
    const options = orderByHierarchy(activeDepartments);
    const assigned = user.departmentId;
    if (!assigned || options.some((d) => d.id === assigned)) return options;
    return [...options, { id: assigned, name: `${user.departmentName ?? '알 수 없음'} (비활성)` }];
  }

  function handleRestoreConfirm() {
    if (!restoreTarget) return;
    restoreMutation.mutate(restoreTarget.id, {
      onSuccess: () => toast.success(`${restoreTarget.name}님을 재직 상태로 복구했습니다.`),
    });
    setRestoreTarget(null);
  }

  function openRehire(user) {
    setRehireTarget({ user, hireDate: '', departmentId: '' });
  }
  function closeRehireConfirm() {
    setRehireTarget(null);
  }
  // 재입사일이 퇴직일보다 앞서면 서버도 REHIRE_DATE_BEFORE_RETIREMENT로 막지만, 제출 후에야
  // 알게 하지 않으려고 모달에서 먼저 막는다 (지시서). 문자열 비교로 충분하다 — 둘 다 'YYYY-MM-DD'.
  const rehireDateBeforeRetirement =
    Boolean(rehireTarget?.hireDate) && rehireTarget.hireDate < rehireTarget.user.retiredAt;
  // 퇴직일과 재입사일이 같으면 공백이 없다 — 계속근로 인정 대상일 수 있어 경고만 하고 막지는
  // 않는다 (설계 §8). 판단은 회사가 한다.
  const rehireNoGap =
    Boolean(rehireTarget?.hireDate) && rehireTarget.hireDate === rehireTarget.user.retiredAt;

  function handleRehireConfirm() {
    if (!rehireTarget || !rehireTarget.hireDate || rehireDateBeforeRetirement) return;
    const { user, hireDate, departmentId } = rehireTarget;
    rehireMutation.mutate(
      { id: user.id, hireDate, departmentId: departmentId ? Number(departmentId) : null },
      { onSuccess: () => toast.success(`${user.name}님을 재입사 처리했습니다.`) },
    );
    setRehireTarget(null);
  }

  function closeRetireConfirm() {
    setRetireTarget(null);
  }
  function handleRetireConfirm() {
    if (!retireTarget) return;
    retireMutation.mutate(retireTarget.id, {
      onSuccess: () => toast.success(`${retireTarget.name}님을 퇴직 처리했습니다.`),
    });
    closeRetireConfirm();
  }

  function closePurgeConfirm() {
    setPurgeTarget(null);
    setPurgeNameInput('');
  }
  // 이름을 정확히 입력해야만 실행된다 — 파기는 되돌릴 수 없어 확인만으로는 부족하다 (설계 §7)
  function handlePurgeConfirm() {
    if (!purgeTarget || purgeNameInput !== purgeTarget.name) return;
    const target = purgeTarget;
    purgeMutation.mutate(target.id, {
      onSuccess: () => toast.success(`${target.name}님의 개인정보를 파기했습니다.`),
    });
    closePurgeConfirm();
  }

  function closeHoldConfirm() {
    setHoldTarget(null);
    setHoldReasonInput('');
  }
  function handleHoldConfirm() {
    if (!holdTarget || !holdReasonInput.trim()) return;
    const target = holdTarget;
    placeHoldMutation.mutate(
      { id: target.id, reason: holdReasonInput.trim() },
      { onSuccess: () => toast.success(`${target.name}님의 파기를 보류했습니다.`) },
    );
    closeHoldConfirm();
  }

  function handleReleaseHold(user) {
    releaseHoldMutation.mutate(user.id, {
      onSuccess: () => toast.success(`${user.name}님의 파기 보류를 해제했습니다.`),
    });
  }

  // 온보딩 승인/반려 — 승인은 그 시점에 연차를 부여하므로 되돌리기 어렵다. 확인 다이얼로그를 거친다
  const [onboardingTarget, setOnboardingTarget] = useState(null);

  function handleOnboardingDecision() {
    if (!onboardingTarget) return;
    const { row, approve } = onboardingTarget;
    const mutation = approve ? approveOnboardingMutation : rejectOnboardingMutation;
    mutation.mutate(row.userId, {
      onSuccess: () =>
        toast.success(
          approve
            ? `${row.name}님의 온보딩을 승인했습니다. 연차가 부여되었습니다.`
            : `${row.name}님의 온보딩을 반려했습니다. 다시 입력할 수 있습니다.`,
        ),
    });
    setOnboardingTarget(null);
  }

  const onboardingRows = onboardingQuery.data?.content ?? [];
  // 파기 대상 건수 — 수동 모드에서는 스케줄러가 없어 관리자가 직접 알아채야 한다.
  // 유일한 알림 수단이 이 배지이므로 탭 라벨에 붙인다 (설계 §5, §7).
  const purgeTargetCount = (purgeTargetCountQuery.data?.content ?? []).filter(
    (u) => u.purgeEligible,
  ).length;
  const retiredTabLabel = purgeTargetCount > 0 ? `퇴직 · 파기대상 ${purgeTargetCount}` : '퇴직';
  const tabItems = [
    { value: TAB_ACTIVE, label: '재직', count: activeQuery.data?.page?.totalElements },
    { value: TAB_RETIRED, label: retiredTabLabel, count: retiredQuery.data?.page?.totalElements },
    {
      value: TAB_ONBOARDING,
      label: '온보딩 승인',
      count: onboardingQuery.data?.page?.totalElements,
    },
  ];

  return (
    <div>
      <PageHeader title="구성원 관리" subtitle="전체 구성원 조회 및 권한·부서 관리" />

      <Tabs tabs={tabItems} value={tab} onChange={setTab} className="mb-5" />

      {/* 온보딩 승인 대기 — 자동 승인 범위를 벗어난 입사일을 신고한 계정 (리뷰 S-1).
          승인 전까지 연차가 0이고 로그인 외 아무것도 못 하므로 방치하면 그 사원이 잠긴다 */}
      {tab === TAB_ONBOARDING && (
        <>
          <p className="mb-4 text-[13px] leading-relaxed text-ink-mute">
            최근 입사일이 아닌 값을 신고한 계정입니다. 승인하면 <strong className="text-ink-body">그
            시점에</strong> 근속 기간에 맞는 연차가 부여되고, 반려하면 사원이 다시 입력할 수 있습니다.
            승인 전까지 그 계정은 로그인 외에는 아무것도 할 수 없습니다.
          </p>
          <TableCard
            loading={onboardingQuery.isLoading}
            error={onboardingQuery.isError}
            errorLabel="온보딩 승인 대기 목록을 불러오지 못했습니다."
            onRetry={onboardingQuery.refetch}
            empty={!onboardingQuery.isLoading && onboardingRows.length === 0}
            emptyLabel="승인을 기다리는 온보딩이 없습니다."
          >
            <Table className="min-w-[840px]">
              <THead>
                <Th>구성원</Th>
                <Th>이메일</Th>
                <Th>신고한 입사일</Th>
                <Th right>소급 일수</Th>
                <Th>승인 시 부여</Th>
                <Th right>관리</Th>
              </THead>
              <tbody>
                {onboardingRows.map((row) => (
                  <TR key={row.userId}>
                    <Td>
                      <div className="flex items-center gap-3">
                        <Avatar name={row.name} />
                        <span className="font-medium text-ink-hi">{row.name}</span>
                      </div>
                    </Td>
                    <Td className="text-ink-mute">{row.email}</Td>
                    <Td className="text-ink-body">{row.hireDate}</Td>
                    <Td right>
                      <span className="font-semibold text-ink-hi">{row.backdatedDays}</span>
                      <span className="text-ink-faint">일 전</span>
                    </Td>
                    <Td className="text-ink-body">
                      {/* 근속 1년 미만이면 월차 소급이라 일수가 정책이 아닌 경과 개월 수로 정해진다 */}
                      {row.yearsOfService == null
                        ? '월차 소급 (1년 미만)'
                        : `${row.yearsOfService}년차 정책 연차`}
                    </Td>
                    <Td right>
                      <div className="flex items-center justify-end gap-1">
                        {/* 승인·반려는 채워진 타일 — 이 화면에서 유일하게 되돌리기 어려운 이지선다다.
                            승인은 그 자리에서 연차를 부여하고, 반려는 사원의 입력값을 지운다.
                            둘을 색으로 확실히 갈라 놓지 않으면 옆자리를 누르는 사고가 난다 */}
                        <IconButton
                          Icon={Check}
                          label="승인"
                          tone="confirm"
                          size="lg"
                          onClick={() => setOnboardingTarget({ row, approve: true })}
                        />
                        <IconButton
                          Icon={X}
                          label="반려"
                          tone="deny"
                          size="lg"
                          onClick={() => setOnboardingTarget({ row, approve: false })}
                        />
                      </div>
                    </Td>
                  </TR>
                ))}
              </tbody>
            </Table>
            <Pagination
              page={onboardingPage}
              totalPages={onboardingQuery.data?.page?.totalPages}
              totalElements={onboardingQuery.data?.page?.totalElements}
              onChange={setOnboardingPage}
            />
          </TableCard>
        </>
      )}

      {/* 검색 + 역할 필터 (재직 탭 전용) */}
      {tab === TAB_ACTIVE && (
        <div className="mb-4 flex flex-wrap items-center justify-between gap-3">
          <div className="relative w-72">
            <Search
              size={15}
              className="pointer-events-none absolute left-3 top-1/2 -translate-y-1/2 text-ink-faint"
            />
            <TextInput
              type="text"
              value={searchInput}
              onChange={(e) => setSearchInput(e.target.value)}
              placeholder="이름 또는 이메일 검색"
              className="!pl-9"
            />
          </div>
          <FilterGroup label="역할">
            {ROLE_FILTERS.map((f) => (
              <Chip key={f.value} active={roleFilter === f.value} onClick={() => setRoleFilter(f.value)}>
                {f.label}
              </Chip>
            ))}
          </FilterGroup>
        </div>
      )}

      {/* 퇴직 탭의 두 처리 버튼 안내 — 이름만으로는 구분이 안 된다 (설계-초안 §3, §6) */}
      {tab === TAB_RETIRED && (
        <p className="mb-4 text-[13px] leading-relaxed text-ink-mute">
          <strong className="text-ink-body">퇴직 복구</strong>는 착오 처리를 되돌리거나 계속근로로
          인정할 때 씁니다 — 연차·기산일이 그대로 이어집니다.{' '}
          <strong className="text-ink-body">재입사 처리</strong>는 그만뒀다 다시 입사했을 때 씁니다 —
          연차가 0부터 다시 시작합니다.
        </p>
      )}

      {/* 테이블 카드 (재직·퇴직) — 온보딩 승인 탭은 위에서 자체 표를 렌더한다 */}
      {tab !== TAB_ONBOARDING && (
      <TableCard
        loading={loading}
        error={listError}
        errorLabel="구성원 목록을 불러오지 못했습니다."
        onRetry={retryList}
        empty={!loading && rows.length === 0}
        emptyLabel="조회된 구성원이 없습니다."
      >
        <Table className="min-w-[920px]">
          <THead>
            <Th>구성원</Th>
            <Th>이메일</Th>
            <Th>부서</Th>
            <Th>직책</Th>
            <Th>{tab === TAB_ACTIVE ? '역할' : '상태'}</Th>
            <Th right>연차 (잔여/기본)</Th>
            <Th>입사일</Th>
            {tab === TAB_ACTIVE ? (
              <Th right sticky>관리</Th>
            ) : (
              <>
                <Th>퇴직일</Th>
                <Th right sticky>관리</Th>
              </>
            )}
          </THead>
          <tbody>
            {rows.map((m) => (
              <TR key={m.id}>
                <Td>
                  {/* 파기된 행의 이름은 서버가 이미 "퇴직사원#id"로 익명화해 내려준다 (설계 §4,
                      User.purge()) — 화면은 값을 그대로 보여줄 뿐 따로 가공하지 않는다 */}
                  <div className="flex items-center gap-3">
                    <Avatar name={m.name} />
                    <span className="font-medium text-ink-hi">{m.name}</span>
                  </div>
                </Td>
                <Td className="max-w-[180px] truncate text-ink-mute">{m.email}</Td>
                {/* 부서·역할은 표에서 바로 고친다. 퇴직 탭은 조회 전용이라 텍스트·배지 그대로 둔다 */}
                <Td className="text-ink-body">
                  {tab === TAB_ACTIVE ? (
                    <InlineSelect
                      label={`${m.name}님의 부서`}
                      value={String(cellValue(m, 'department', m.departmentId ?? ''))}
                      disabled={isSaving(m, 'department')}
                      onChange={(e) => handleDeptChange(m, e.target.value)}
                    >
                      {/* 미배정은 고르는 값이 아니라 "아직 안 정해짐"이다 — 서버가 부서 해제를
                          받지 않으므로 disabled로 두어 되돌아갈 수 없다는 것을 드러낸다 */}
                      <option value="" disabled>
                        미배정
                      </option>
                      {departmentOptions(m).map((d) => (
                        <option key={d.id} value={d.id}>
                          {departmentOptionLabel(d)}
                        </option>
                      ))}
                    </InlineSelect>
                  ) : (
                    (m.departmentName ?? '미배정')
                  )}
                </Td>
                <Td className="text-ink-body">{m.position ?? '-'}</Td>
                <Td>
                  {tab === TAB_ACTIVE ? (
                    <InlineSelect
                      label={`${m.name}님의 역할`}
                      tone={roleTone(cellValue(m, 'role', m.role))}
                      value={cellValue(m, 'role', m.role)}
                      disabled={isSaving(m, 'role')}
                      onChange={(e) => handleRoleChange(m, e.target.value)}
                    >
                      {ROLE_OPTIONS.map((r) => (
                        <option key={r} value={r}>
                          {ROLE_LABEL[r]}
                        </option>
                      ))}
                    </InlineSelect>
                  ) : (
                    // 파기·보류 상태를 배지로 한 번 더 드러낸다 — 사유는 퇴직일 칸에 따로 적는다
                    <StatusBadge
                      label={m.purgedAt ? '파기완료' : m.purgeHoldReason ? '보류' : '퇴직'}
                      tone={m.purgedAt ? 'muted' : m.purgeHoldReason ? 'warn' : 'muted'}
                    />
                  )}
                </Td>
                <Td right>
                  <span className="font-semibold text-ink-hi">{Number(m.remainingDays)}</span>
                  <span className="text-ink-faint"> / {Number(m.baseDays)}일</span>
                </Td>
                <Td className="text-ink-mute">{m.hireDate ?? '-'}</Td>
                {tab === TAB_ACTIVE ? (
                  <Td right sticky>
                    <div className="flex items-center justify-end gap-1">
                      {/* 역할·부서 아이콘 버튼은 없앴다 — 같은 일을 하는 길이 둘이면 어느 쪽이
                          최신인지 헷갈리고, 표의 셀렉트가 이미 더 빠르다 */}
                      {/* 본인 행은 비활성 (S-7). 퇴직은 그 즉시 로그인까지 막혀 스스로 되돌릴 수
                          없다 — 서버도 CANNOT_RETIRE_SELF로 막지만, 막기만 하고 이유를 안 쓰면
                          관리자는 버튼이 고장 난 줄 안다. label이 title/aria-label로 나간다 */}
                      <IconButton
                        Icon={UserMinus}
                        label={
                          m.id === currentUser?.id
                            ? '본인 계정은 퇴직 처리할 수 없습니다 (다른 관리자에게 요청)'
                            : '퇴직 처리'
                        }
                        tone="danger"
                        disabled={m.id === currentUser?.id}
                        onClick={() => setRetireTarget(m)}
                      />
                    </div>
                  </Td>
                ) : (
                  <>
                    <Td className="text-ink-mute">
                      {formatRetiredAt(m.retiredAt, m.elapsedYears, m.elapsedMonths)}
                      {/* 보류된 행은 사유를 함께 보여준다 — 사유 없는 보류는 잊혀진 데이터가 된다 (설계 §3) */}
                      {m.purgeHoldReason && (
                        <div className="mt-1 text-[11px] text-warn">보류: {m.purgeHoldReason}</div>
                      )}
                    </Td>
                    <Td right sticky>
                      <div className="flex items-center justify-end gap-1">
                        {/* 복구는 되살리는 조작이라 accent(시안)다 — 퇴직 처리의 danger와 방향이 반대인 것이
                            색으로 보여야 옆자리를 잘못 누르지 않는다 */}
                        <IconButton
                          Icon={UserCheck}
                          label="퇴직 복구"
                          tone="accent"
                          onClick={() => setRestoreTarget(m)}
                        />
                        {/* 파기된 사원은 이메일이 바뀌어 신규 가입으로 들어오므로 재입사 대상이
                            아니다 — 서버도 ALREADY_PURGED로 막지만, 애초에 버튼을 보여주지 않는다
                            (지시서, 설계 §7) */}
                        {!m.purgedAt && (
                          <IconButton
                            Icon={UserPlus}
                            label="재입사 처리"
                            tone="muted"
                            onClick={() => openRehire(m)}
                          />
                        )}
                        {/* 파기된 행은 파기·보류 버튼이 완전히 사라진다 — 더 이상 조작할 개인정보가
                            없다. 보류된 행은 파기 버튼이 없어지는 게 아니라 잠긴다 — 보류 사유가
                            남아 있다는 것 자체가 "지금은 안 된다"는 신호이기 때문이다 */}
                        {!m.purgedAt && (
                          <>
                            {m.purgeHoldReason ? (
                              <IconButton
                                Icon={PlayCircle}
                                label="파기 보류 해제"
                                tone="accent"
                                disabled={releaseHoldMutation.isPending}
                                onClick={() => handleReleaseHold(m)}
                              />
                            ) : (
                              <IconButton
                                Icon={PauseCircle}
                                label="파기 보류"
                                tone="muted"
                                onClick={() => setHoldTarget(m)}
                              />
                            )}
                            <IconButton
                              Icon={Trash2}
                              label={
                                m.purgeEligible
                                  ? '파기'
                                  : m.purgeHoldReason
                                    ? '파기 보류 상태입니다. 보류를 해제한 뒤 다시 시도해주세요.'
                                    : '퇴직 후 3년이 지나야 파기할 수 있습니다.'
                              }
                              tone="danger"
                              disabled={!m.purgeEligible}
                              onClick={() => setPurgeTarget(m)}
                            />
                          </>
                        )}
                      </div>
                    </Td>
                  </>
                )}
              </TR>
            ))}
          </tbody>
        </Table>
        <Pagination
          page={listPage}
          totalPages={listPageInfo?.totalPages}
          totalElements={listPageInfo?.totalElements}
          onChange={setListPage}
        />
      </TableCard>
      )}

      {/* 온보딩 승인/반려 확인 — 승인은 그 자리에서 연차를 부여하므로 되돌리기 어렵다 */}
      <ConfirmDialog
        open={Boolean(onboardingTarget)}
        title={onboardingTarget?.approve ? '온보딩 승인' : '온보딩 반려'}
        message={
          onboardingTarget?.approve
            ? `${onboardingTarget.row.name}님의 입사일 ${onboardingTarget.row.hireDate}을(를) 확정하고 연차를 부여합니다.`
            : `${onboardingTarget?.row.name}님의 입력을 지우고 처음으로 되돌립니다. 사원이 다시 입력할 수 있습니다.`
        }
        confirmLabel={onboardingTarget?.approve ? '승인' : '반려'}
        tone={onboardingTarget?.approve ? 'default' : 'danger'}
        loading={approveOnboardingMutation.isPending || rejectOnboardingMutation.isPending}
        onConfirm={handleOnboardingDecision}
        onCancel={() => setOnboardingTarget(null)}
      />

      {/* 역할 변경 확인 — 미배정 팀장 승격은 부분 성공을 막기 위해 부서까지 한 번에 받는다 */}
      <ConfirmDialog
        open={Boolean(roleTarget)}
        title={roleTarget ? roleChangeNotice(roleTarget).title : ''}
        message={
          roleTarget?.needsDepartment && !roleTarget.departmentId
            ? `${roleTarget.user.name}님을 팀장으로 지정할 부서를 먼저 선택해 주세요.`
            : roleTarget
              ? roleChangeNotice(roleTarget).body
              : ''
        }
        tone={roleTarget ? roleChangeNotice(roleTarget).tone : 'default'}
        confirmLabel={`${roleTarget ? ROLE_LABEL[roleTarget.nextRole] : ''}(으)로 변경`}
        loading={updateRoleMutation.isPending || updateRoleAndDepartmentMutation.isPending}
        confirmDisabled={Boolean(roleTarget?.needsDepartment && !roleTarget.departmentId)}
        onConfirm={handleRoleConfirm}
        onCancel={() => setRoleTarget(null)}
      >
        {roleTarget?.needsDepartment && (
          <Field
            className="mt-4"
            label="팀장으로 지정할 부서"
            required
            hint="부서 배정과 팀장 승격이 서버에서 한 번에 처리됩니다."
          >
            <Select
              aria-label={`${roleTarget.user.name}님의 팀장 부서`}
              value={roleTarget.departmentId}
              onChange={(event) => handleRoleDepartmentChange(event.target.value)}
            >
              <option value="">부서를 선택해 주세요</option>
              {orderByHierarchy(leaderAssignableDepartments).map((department) => (
                <option key={department.id} value={department.id}>
                  {departmentOptionLabel(department)}
                </option>
              ))}
            </Select>
          </Field>
        )}
      </ConfirmDialog>

      {/* 퇴직 복구 확인 — 되살아나지 않는 것을 반드시 적는다.
          "퇴직을 취소한다"고만 쓰면 관리자는 팀장직과 결재까지 되돌아온다고 읽는다 */}
      <ConfirmDialog
        open={Boolean(restoreTarget)}
        title="퇴직 복구"
        message={
          restoreTarget &&
          `${restoreTarget.name}님을 재직 상태로 되돌립니다. 다시 로그인하고 연차를 신청할 수 있게 되며, 연차 잔액은 퇴직 전 그대로입니다. 다만 퇴직 때 해제된 팀장직과 다른 승인자에게 넘어간 결재는 되돌아오지 않습니다 — 필요하면 부서 관리에서 팀장을 다시 지정해 주세요.`
        }
        confirmLabel="복구"
        loading={restoreMutation.isPending}
        onConfirm={handleRestoreConfirm}
        onCancel={() => setRestoreTarget(null)}
      />

      {/* 재입사 처리 확인 — 퇴직 복구와 달리 근속을 새로 시작한다. 재입사일(필수)·부서를 받고
          적용 결과를 그대로 보여준다 (설계-초안 §6). 역할·직책은 승계하지 않으므로 그 사실도 밝힌다 */}
      <ConfirmDialog
        open={Boolean(rehireTarget)}
        title="재입사 처리"
        message={
          rehireTarget &&
          `${rehireTarget.user.name}님을 재입사 처리합니다. 이전 근속의 진행 중인 신청은 취소되고, 승인된 신청은 이력으로 남습니다.`
        }
        confirmLabel="재입사 처리"
        confirmDisabled={
          !rehireTarget || !rehireTarget.hireDate || rehireDateBeforeRetirement
        }
        loading={rehireMutation.isPending}
        onConfirm={handleRehireConfirm}
        onCancel={closeRehireConfirm}
      >
        {rehireTarget && (
          <div className="mt-4 space-y-4">
            <Field label="재입사일" required error={rehireDateBeforeRetirement ? `퇴직일(${rehireTarget.user.retiredAt})보다 앞설 수 없습니다.` : null}>
              <TextInput
                type="date"
                aria-label={`${rehireTarget.user.name}님의 재입사일`}
                value={rehireTarget.hireDate}
                invalid={rehireDateBeforeRetirement}
                onChange={(e) =>
                  setRehireTarget((target) => ({ ...target, hireDate: e.target.value }))
                }
              />
            </Field>
            {rehireNoGap && (
              <p className="text-[12px] leading-relaxed text-warn">
                공백이 없습니다. 계속근로로 인정해야 하는지 확인하세요.
              </p>
            )}
            <Field label="부서" hint="지정하지 않으면 미배정으로 등록됩니다.">
              <Select
                aria-label={`${rehireTarget.user.name}님의 재입사 부서`}
                value={rehireTarget.departmentId}
                onChange={(e) =>
                  setRehireTarget((target) => ({ ...target, departmentId: e.target.value }))
                }
              >
                <option value="">미배정</option>
                {orderByHierarchy(activeDepartments).map((department) => (
                  <option key={department.id} value={department.id}>
                    {departmentOptionLabel(department)}
                  </option>
                ))}
              </Select>
            </Field>
            {rehireTarget.hireDate && !rehireDateBeforeRetirement && (
              <div className="rounded-btn border border-white/[0.12] bg-white/[0.03] p-3 text-[12px] leading-relaxed text-ink-body">
                <p>
                  역할 {ROLE_LABEL[rehireTarget.user.role]} → {ROLE_LABEL[ROLE.EMPLOYEE]}
                  <span className="text-ink-faint"> (관리자가 다시 지정해야 합니다)</span>
                </p>
                <p className="mt-1">
                  직책 {rehireTarget.user.position ?? '-'} → <span className="text-ink-faint">(비움)</span>
                </p>
                <p className="mt-1">
                  직급 {rehireTarget.user.jobGrade ?? '-'} → <span className="text-ink-faint">(비움)</span>
                </p>
                <p className="mt-1">
                  연차 {Number(rehireTarget.user.remainingDays)}/{Number(rehireTarget.user.baseDays)} → 0/0, 기산일{' '}
                  {rehireTarget.hireDate}
                </p>
              </div>
            )}
          </div>
        )}
      </ConfirmDialog>

      {/* 퇴직 처리 확인 — 되돌릴 수 없는 작업이라 ConfirmDialog로 한 번 더 확인 */}
      <ConfirmDialog
        open={Boolean(retireTarget)}
        title="퇴직 처리"
        message={
          retireTarget &&
          `${retireTarget.name}님을 퇴직 처리하시겠습니까? 진행 중인 결재는 관리자에게 자동 이관되고, 팀장으로 지정된 부서는 팀장이 해제됩니다. 계정은 퇴직 탭에서 다시 복구할 수 있지만, 이관된 결재와 해제된 팀장직은 복구해도 되돌아오지 않습니다.`
        }
        tone="danger"
        confirmLabel="퇴직 처리"
        onConfirm={handleRetireConfirm}
        onCancel={closeRetireConfirm}
        loading={retireMutation.isPending}
      />

      {/* 파기 확인 — 되돌릴 수 없으므로 확인만으로는 실행하지 않는다. 이름을 정확히 입력해야
          실행 버튼이 열린다 (설계 §7). 무엇이 사라지고 무엇이 남는지도 그대로 적는다 (설계 §4) */}
      <ConfirmDialog
        open={Boolean(purgeTarget)}
        title="퇴직자 개인정보 파기"
        message={purgeTarget && `${purgeTarget.name}님의 개인정보를 파기합니다.`}
        tone="danger"
        confirmLabel="파기"
        confirmDisabled={!purgeTarget || purgeNameInput !== purgeTarget.name}
        loading={purgeMutation.isPending}
        onConfirm={handlePurgeConfirm}
        onCancel={closePurgeConfirm}
      >
        {purgeTarget && (
          <div className="mt-4 space-y-4">
            <div className="rounded-btn border border-danger/30 bg-danger/[0.06] p-3 text-[12px] leading-relaxed text-ink-body">
              <p>
                <span className="font-semibold text-danger">사라집니다</span> — 이름·이메일·생일·입사일,
                연차/복리후생 사유, 발송된 메일 본문
              </p>
              <p className="mt-1.5">
                <span className="font-semibold text-ink-hi">남습니다</span> — 연차 사용 통계, 결재 이력,
                관리자 처리 기록
              </p>
              <p className="mt-1.5 font-semibold text-danger">되돌릴 수 없습니다.</p>
            </div>
            <Field label={`확인을 위해 "${purgeTarget.name}"을(를) 입력해 주세요`} required>
              <TextInput
                aria-label={`${purgeTarget.name}님 이름 확인`}
                value={purgeNameInput}
                onChange={(e) => setPurgeNameInput(e.target.value)}
                placeholder={purgeTarget.name}
                autoComplete="off"
              />
            </Field>
          </div>
        )}
      </ConfirmDialog>

      {/* 파기 보류 — 사유를 반드시 받는다. 사유 없는 보류는 잊혀진 데이터가 된다 (설계 §3) */}
      <ConfirmDialog
        open={Boolean(holdTarget)}
        title="파기 보류"
        message={holdTarget && `${holdTarget.name}님의 개인정보 파기를 보류합니다.`}
        confirmLabel="보류"
        confirmDisabled={!holdReasonInput.trim()}
        loading={placeHoldMutation.isPending}
        onConfirm={handleHoldConfirm}
        onCancel={closeHoldConfirm}
      >
        {holdTarget && (
          <Field className="mt-4" label="보류 사유" required>
            <Textarea
              aria-label={`${holdTarget.name}님의 파기 보류 사유`}
              value={holdReasonInput}
              onChange={(e) => setHoldReasonInput(e.target.value)}
              placeholder="예: 재입사 예정, 분쟁 진행 중"
            />
          </Field>
        )}
      </ConfirmDialog>
    </div>
  );
}
