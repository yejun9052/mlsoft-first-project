import { useEffect, useState } from 'react';
import toast from 'react-hot-toast';
import { Search, UserMinus, UserCheck, Check, X } from 'lucide-react';
import { ROLE, ROLE_LABEL } from '../constants/roles.js';
import PageHeader from '../components/ui/PageHeader.jsx';
import Tabs from '../components/ui/Tabs.jsx';
import FilterGroup from '../components/ui/FilterGroup.jsx';
import Chip from '../components/ui/Chip.jsx';
import TextInput from '../components/ui/TextInput.jsx';
import TableCard from '../components/ui/TableCard.jsx';
import Table, { THead, Th, TR, Td } from '../components/ui/Table.jsx';
import Avatar from '../components/ui/Avatar.jsx';
import StatusBadge from '../components/ui/StatusBadge.jsx';
import InlineSelect from '../components/ui/InlineSelect.jsx';
import IconButton from '../components/ui/IconButton.jsx';
import ConfirmDialog from '../components/ui/ConfirmDialog.jsx';
import {
  useRestoreUser,
  useRetiredUsers,
  useRetireUser,
  useUpdateUserDepartment,
  useUpdateUserRole,
  useUsers,
} from '../hooks/useUsers.js';
import { useDepartments } from '../hooks/useDepartments.js';
import { departmentOptionLabel, orderByHierarchy } from '../utils/departmentTree.js';
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

  // 탭 배지가 필터와 무관하게 항상 정확한 건수를 보여줄 수 있도록 재직·퇴직 둘 다 항상 조회한다
  // (ApprovalsPage가 탭과 무관하게 대기 목록을 항상 조회하는 것과 동일한 전략).
  const activeQuery = useUsers({ keyword, role: roleFilter === 'ALL' ? undefined : roleFilter });
  const retiredQuery = useRetiredUsers();
  const departmentsQuery = useDepartments();
  // 온보딩 승인 대기 — 배지에 항상 건수를 띄워야 관리자가 잠긴 계정을 놓치지 않는다 (리뷰 S-1)
  const onboardingQuery = usePendingOnboardings();
  const approveOnboardingMutation = useApproveOnboarding();
  const rejectOnboardingMutation = useRejectOnboarding();

  const updateRoleMutation = useUpdateUserRole();
  const updateDepartmentMutation = useUpdateUserDepartment();
  const retireMutation = useRetireUser();
  const restoreMutation = useRestoreUser();

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

  const { data: currentUser } = useCurrentUser();

  const rows = tab === TAB_ACTIVE ? (activeQuery.data?.content ?? []) : (retiredQuery.data?.content ?? []);
  const loading = tab === TAB_ACTIVE ? activeQuery.isLoading : retiredQuery.isLoading;
  // 조회 실패를 "조회된 구성원이 없습니다"로 보여주면 관리자가 계정이 사라졌다고 오해한다 (리뷰 F-6)
  const listError = tab === TAB_ACTIVE ? activeQuery.isError : retiredQuery.isError;
  const retryList = tab === TAB_ACTIVE ? activeQuery.refetch : retiredQuery.refetch;
  const activeDepartments = (departmentsQuery.data ?? []).filter((d) => d.active);

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

    // 팀장은 "어느 부서의" 팀장이므로 부서가 없으면 앉힐 자리가 없다. 서버도 같은 이유로 거부하지만
    // (DEPARTMENT_REQUIRED_FOR_LEADER) 확인 창까지 띄웠다가 오류를 보여 주는 것은 헛걸음이다
    if (nextRole === ROLE.TEAM_LEADER && !user.departmentId) {
      toast.error(`${user.name}님은 부서가 없습니다. 부서를 먼저 배정한 뒤 팀장으로 지정해 주세요.`);
      return;
    }

    const department = activeDepartments.find((d) => d.id === user.departmentId);
    setRoleTarget({
      user,
      nextRole,
      // 본인 강등만 특별하다. 총관리자를 유지하는 변경은 잠기지 않으므로 자기 자신이어도 일반 문구다
      isSelf: currentUser?.id === user.id && nextRole !== ROLE.SYSTEM_ADMIN,
      departmentName: department?.name ?? user.departmentName,
      // 교체되는 사람의 이름 — 본인이 이미 팀장이면 교체가 아니므로 비운다
      currentLeaderName:
        department?.leaderId && department.leaderId !== user.id ? department.leaderName : null,
    });
  }

  function handleRoleConfirm() {
    if (!roleTarget) return;
    const { user, nextRole } = roleTarget;
    setRoleTarget(null);
    setPendingEdit({ id: user.id, field: 'role', value: nextRole });
    updateRoleMutation.mutate(
      { id: user.id, role: nextRole },
      {
        onSuccess: () =>
          toast.success(`${user.name}님의 역할을 ${ROLE_LABEL[nextRole]}(으)로 변경했습니다.`),
        // 성공이든 실패든 표시를 거둔다 — 실패하면 서버 값이 그대로라 옛 역할로 되돌아간다
        onSettled: () => setPendingEdit(null),
      },
    );
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
  const tabItems = [
    { value: TAB_ACTIVE, label: '재직', count: activeQuery.data?.page?.totalElements },
    { value: TAB_RETIRED, label: '퇴직', count: retiredQuery.data?.page?.totalElements },
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
              <Th right>관리</Th>
            ) : (
              <>
                <Th>퇴직일</Th>
                <Th right>관리</Th>
              </>
            )}
          </THead>
          <tbody>
            {rows.map((m) => (
              <TR key={m.id}>
                <Td>
                  <div className="flex items-center gap-3">
                    <Avatar name={m.name} />
                    <span className="font-medium text-ink-hi">{m.name}</span>
                  </div>
                </Td>
                <Td className="text-ink-mute">{m.email}</Td>
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
                <Td className="text-ink-body">{m.position}</Td>
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
                    <StatusBadge label="퇴직" tone="muted" />
                  )}
                </Td>
                <Td right>
                  <span className="font-semibold text-ink-hi">{Number(m.remainingDays)}</span>
                  <span className="text-ink-faint"> / {Number(m.baseDays)}일</span>
                </Td>
                <Td className="text-ink-mute">{m.hireDate}</Td>
                {tab === TAB_ACTIVE ? (
                  <Td right>
                    <div className="flex items-center justify-end gap-1">
                      {/* 역할·부서 아이콘 버튼은 없앴다 — 같은 일을 하는 길이 둘이면 어느 쪽이
                          최신인지 헷갈리고, 표의 셀렉트가 이미 더 빠르다 */}
                      <IconButton
                        Icon={UserMinus}
                        label="퇴직 처리"
                        tone="danger"
                        onClick={() => setRetireTarget(m)}
                      />
                    </div>
                  </Td>
                ) : (
                  <>
                    <Td className="text-ink-mute">{m.retiredAt}</Td>
                    <Td right>
                      <div className="flex items-center justify-end">
                        {/* 복구는 되살리는 조작이라 accent(시안)다 — 퇴직 처리의 danger와 방향이 반대인 것이
                            색으로 보여야 옆자리를 잘못 누르지 않는다 */}
                        <IconButton
                          Icon={UserCheck}
                          label="퇴직 복구"
                          tone="accent"
                          onClick={() => setRestoreTarget(m)}
                        />
                      </div>
                    </Td>
                  </>
                )}
              </TR>
            ))}
          </tbody>
        </Table>
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

      {/* 역할 변경 확인 — 세 방향 모두 조용히 넘어가면 안 되는 결과가 딸려 온다 (roleChangeNotice) */}
      <ConfirmDialog
        open={Boolean(roleTarget)}
        title={roleTarget ? roleChangeNotice(roleTarget).title : ''}
        message={roleTarget ? roleChangeNotice(roleTarget).body : ''}
        tone={roleTarget ? roleChangeNotice(roleTarget).tone : 'default'}
        confirmLabel={`${roleTarget ? ROLE_LABEL[roleTarget.nextRole] : ''}(으)로 변경`}
        loading={updateRoleMutation.isPending}
        onConfirm={handleRoleConfirm}
        onCancel={() => setRoleTarget(null)}
      />

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
    </div>
  );
}
