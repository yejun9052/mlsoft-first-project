import { useEffect, useState } from 'react';
import toast from 'react-hot-toast';
import { Search, Shield, Building2, UserMinus, Check, X } from 'lucide-react';
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
import IconButton from '../components/ui/IconButton.jsx';
import Modal from '../components/ui/Modal.jsx';
import Field from '../components/ui/Field.jsx';
import Select from '../components/ui/Select.jsx';
import Button from '../components/ui/Button.jsx';
import ConfirmDialog from '../components/ui/ConfirmDialog.jsx';
import {
  useRetiredUsers,
  useRetireUser,
  useUpdateUserDepartment,
  useUpdateUserRole,
  useUsers,
} from '../hooks/useUsers.js';
import { useDepartments } from '../hooks/useDepartments.js';
import {
  useApproveOnboarding,
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

// 역할 변경 모달의 선택지 (전체 필터의 'ALL'은 제외)
const ROLE_OPTIONS = [ROLE.EMPLOYEE, ROLE.TEAM_LEADER, ROLE.SYSTEM_ADMIN];

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

  // 역할 변경 모달 — 대상 하나만 담는다(여러 행이 있어도 모달은 1개)
  const [roleTarget, setRoleTarget] = useState(null);
  const [roleValue, setRoleValue] = useState(ROLE.EMPLOYEE);
  // 부서 변경 모달
  const [deptTarget, setDeptTarget] = useState(null);
  const [deptValue, setDeptValue] = useState('');
  // 퇴직 처리 확인
  const [retireTarget, setRetireTarget] = useState(null);

  const rows = tab === TAB_ACTIVE ? (activeQuery.data?.content ?? []) : (retiredQuery.data?.content ?? []);
  const loading = tab === TAB_ACTIVE ? activeQuery.isLoading : retiredQuery.isLoading;
  // 조회 실패를 "조회된 구성원이 없습니다"로 보여주면 관리자가 계정이 사라졌다고 오해한다 (리뷰 F-6)
  const listError = tab === TAB_ACTIVE ? activeQuery.isError : retiredQuery.isError;
  const retryList = tab === TAB_ACTIVE ? activeQuery.refetch : retiredQuery.refetch;
  const activeDepartments = (departmentsQuery.data ?? []).filter((d) => d.active);

  function openRoleModal(user) {
    setRoleTarget(user);
    setRoleValue(user.role);
  }
  function closeRoleModal() {
    setRoleTarget(null);
  }
  // 저장 — ApprovalsPage 컨벤션과 동일하게 모달은 즉시 닫고, 성공/실패 안내는 토스트로(성공은 여기서,
  // 실패는 api 인터셉터가 일괄 처리) 보여준다.
  function handleRoleSave() {
    if (!roleTarget) return;
    updateRoleMutation.mutate(
      { id: roleTarget.id, role: roleValue },
      { onSuccess: () => toast.success(`${roleTarget.name}님의 역할을 변경했습니다.`) },
    );
    closeRoleModal();
  }

  function openDeptModal(user) {
    setDeptTarget(user);
    setDeptValue(user.departmentId ? String(user.departmentId) : '');
  }
  function closeDeptModal() {
    setDeptTarget(null);
  }
  function handleDeptSave() {
    if (!deptTarget || !deptValue) return;
    updateDepartmentMutation.mutate(
      { id: deptTarget.id, departmentId: Number(deptValue) },
      { onSuccess: () => toast.success(`${deptTarget.name}님의 부서를 변경했습니다.`) },
    );
    closeDeptModal();
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
                        <IconButton
                          Icon={Check}
                          label="승인"
                          onClick={() => setOnboardingTarget({ row, approve: true })}
                        />
                        <IconButton
                          Icon={X}
                          label="반려"
                          tone="danger"
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
            {tab === TAB_ACTIVE ? <Th right>관리</Th> : <Th>퇴직일</Th>}
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
                <Td className="text-ink-body">{m.departmentName ?? '미배정'}</Td>
                <Td className="text-ink-body">{m.position}</Td>
                <Td>
                  {tab === TAB_ACTIVE ? (
                    <StatusBadge
                      label={ROLE_LABEL[m.role]}
                      tone={m.role === ROLE.EMPLOYEE ? 'muted' : 'accent'}
                    />
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
                      <IconButton Icon={Shield} label="역할 변경" onClick={() => openRoleModal(m)} />
                      <IconButton Icon={Building2} label="부서 변경" onClick={() => openDeptModal(m)} />
                      <IconButton
                        Icon={UserMinus}
                        label="퇴직 처리"
                        tone="danger"
                        onClick={() => setRetireTarget(m)}
                      />
                    </div>
                  </Td>
                ) : (
                  <Td className="text-ink-mute">{m.retiredAt}</Td>
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

      {/* 역할 변경 모달 */}
      {roleTarget && (
        <Modal
          title="역할 변경"
          onClose={closeRoleModal}
          footer={
            <>
              <Button variant="secondary" onClick={closeRoleModal} lift={false}>
                취소
              </Button>
              <Button onClick={handleRoleSave} loading={updateRoleMutation.isPending} lift={false}>
                저장
              </Button>
            </>
          }
        >
          <p className="mb-3 text-[13px] text-ink-mute">
            <span className="font-semibold text-ink-hi">{roleTarget.name}</span>님의 역할을 변경합니다.
          </p>
          <Field label="역할">
            <Select value={roleValue} onChange={(e) => setRoleValue(e.target.value)}>
              {ROLE_OPTIONS.map((r) => (
                <option key={r} value={r}>
                  {ROLE_LABEL[r]}
                </option>
              ))}
            </Select>
          </Field>
        </Modal>
      )}

      {/* 부서 변경 모달 */}
      {deptTarget && (
        <Modal
          title="부서 변경"
          onClose={closeDeptModal}
          footer={
            <>
              <Button variant="secondary" onClick={closeDeptModal} lift={false}>
                취소
              </Button>
              <Button
                onClick={handleDeptSave}
                loading={updateDepartmentMutation.isPending}
                disabled={!deptValue}
                lift={false}
              >
                저장
              </Button>
            </>
          }
        >
          <p className="mb-3 text-[13px] text-ink-mute">
            <span className="font-semibold text-ink-hi">{deptTarget.name}</span>님의 부서를 변경합니다.
          </p>
          <Field label="부서">
            <Select value={deptValue} onChange={(e) => setDeptValue(e.target.value)}>
              <option value="" disabled>
                부서 선택
              </option>
              {activeDepartments.map((d) => (
                <option key={d.id} value={d.id}>
                  {d.name}
                </option>
              ))}
            </Select>
          </Field>
        </Modal>
      )}

      {/* 퇴직 처리 확인 — 되돌릴 수 없는 작업이라 ConfirmDialog로 한 번 더 확인 */}
      <ConfirmDialog
        open={Boolean(retireTarget)}
        title="퇴직 처리"
        message={
          retireTarget &&
          `${retireTarget.name}님을 퇴직 처리하시겠습니까? 진행 중인 결재는 관리자에게 자동 이관되고, 팀장으로 지정된 부서는 팀장이 해제됩니다. 이 작업은 되돌릴 수 없습니다.`
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
