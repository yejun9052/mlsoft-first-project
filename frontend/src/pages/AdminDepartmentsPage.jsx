import { useMemo, useState } from 'react';
import toast from 'react-hot-toast';
import { CornerDownRight, FolderTree, GripVertical, Pencil, Plus, Trash2 } from 'lucide-react';
import PageHeader from '../components/ui/PageHeader.jsx';
import TableCard from '../components/ui/TableCard.jsx';
import Table, { THead, Th, TR, Td } from '../components/ui/Table.jsx';
import ResponsiveTable from '../components/ui/ResponsiveTable.jsx';
import Card from '../components/ui/Card.jsx';
import IconButton from '../components/ui/IconButton.jsx';
import Modal from '../components/ui/Modal.jsx';
import Field from '../components/ui/Field.jsx';
import TextInput from '../components/ui/TextInput.jsx';
import Textarea from '../components/ui/Textarea.jsx';
import Select from '../components/ui/Select.jsx';
import Button from '../components/ui/Button.jsx';
import ConfirmDialog from '../components/ui/ConfirmDialog.jsx';
import StatusBadge from '../components/ui/StatusBadge.jsx';
import useMediaQuery from '../hooks/useMediaQuery.js';
import {
  useCreateDepartment,
  useDeactivateDepartment,
  useDepartments,
  useUpdateDepartment,
} from '../hooks/useDepartments.js';
import { useLeaderCandidates } from '../hooks/useUsers.js';
import {
  buildDepartmentMoveBody,
  canDropDepartment,
  resolveDropParentId,
} from '../utils/departmentDrop.js';
import { orderByHierarchy } from '../utils/departmentTree.js';

const EMPTY_FORM = { name: '', description: '', leaderId: '', parentId: '' };

// 표 전환과 같은 브레이크포인트(ResponsiveTable의 MOBILE_TABLE_QUERY, CHANGELOG 모바일 1단계).
const MOBILE_QUERY = '(max-width: 767px)';

// 부서 관리 — 부서 생성·수정(팀장 지정 포함)·비활성화 (docs/03 부서, SYSTEM_ADMIN 전용).
// 팀장 지정이 이 화면에만 있다 — 부서 팀장이 연차 기본 승인자이고, 공석이면 SYSTEM_ADMIN으로
// fallback 되므로(검증 Y-3) 결재 흐름을 정상화하려면 여기서 팀장을 채워야 한다.
export default function AdminDepartmentsPage() {
  // 드래그는 마우스 전용이라 좁은 화면에서는 카드 목록 + "상위 부서 변경" 버튼으로 대신한다 (C-3).
  const isMobile = useMediaQuery(MOBILE_QUERY);
  const departmentsQuery = useDepartments();
  // 팀장 후보 — 재직 중 TEAM_LEADER·SYSTEM_ADMIN. 부서 배정은 구성원 관리에서 하므로,
  // 신설 부서에도 팀장을 지정할 수 있도록 소속으로 후보를 좁히지 않는다.
  // 전 사원 목록(useUsers)을 쓰지 않는 이유: 서버가 결재 자격자만 팀장으로 받으므로
  // EMPLOYEE를 후보에 담으면 고른 뒤 저장 단계에서 거부된다.
  const usersQuery = useLeaderCandidates();

  const createMutation = useCreateDepartment();
  const updateMutation = useUpdateDepartment();
  const deactivateMutation = useDeactivateDepartment();

  const departments = useMemo(() => departmentsQuery.data ?? [], [departmentsQuery.data]);
  const users = usersQuery.data ?? []; // 페이징 없는 배열 응답

  // 모달 — 생성/수정 폼 하나를 공유한다. editing이 null이면 생성 모드.
  const [formOpen, setFormOpen] = useState(false);
  const [editing, setEditing] = useState(null);
  const [form, setForm] = useState(EMPTY_FORM);
  // 필드별 에러 — 서버도 @NotBlank로 막지만, 왕복 없이 어느 칸이 비었는지 바로 보이게 한다
  const [errors, setErrors] = useState({});
  // 비활성화 확인
  const [deactivateTarget, setDeactivateTarget] = useState(null);

  // 상위 부서 → 하위 부서 순으로 재배열 (API는 id 순 플랫 목록만 준다).
  // 구성원 관리의 부서 선택도 같은 순서를 써야 해서 utils/departmentTree.js 한 곳에 뒀다.
  const orderedRows = useMemo(() => orderByHierarchy(departments), [departments]);

  const rootDepartments = departments.filter((d) => d.parentId == null);
  // 자식이 있는 부서는 스스로 하위 부서가 될 수 없다 — 내 자식이 손자가 돼 3단계가 되기 때문.
  // 서버도 같은 검사를 한다(DepartmentService.validateParent ②, 1차 테스트 F) — 여기서 막는 건
  // 왕복 없이 이유를 보여주기 위한 것이지 유일한 방어선이 아니다.
  const hasChildren = (id) => departments.some((d) => d.parentId === id);

  function openCreate() {
    setEditing(null);
    setForm(EMPTY_FORM);
    setErrors({});
    setFormOpen(true);
  }

  function openEdit(department) {
    setEditing(department);
    setForm({
      name: department.name,
      description: department.description ?? '',
      leaderId: department.leaderId ? String(department.leaderId) : '',
      parentId: department.parentId ? String(department.parentId) : '',
    });
    setErrors({});
    setFormOpen(true);
  }

  function closeForm() {
    setFormOpen(false);
    setEditing(null);
  }

  function updateField(key, value) {
    setForm((prev) => ({ ...prev, [key]: value }));
  }

  // 저장 — 성공 토스트는 여기서, 실패는 api 인터셉터가 일괄 처리(AdminMembersPage와 동일 컨벤션).
  function handleSave() {
    const name = form.name.trim();
    const description = form.description.trim();
    const nextErrors = {};
    if (!name) nextErrors.name = '부서명을 입력해주세요.';
    if (!description) nextErrors.description = '부서 설명을 입력해주세요.';
    setErrors(nextErrors);
    if (Object.keys(nextErrors).length > 0) return;

    const body = {
      name,
      description,
      leaderId: form.leaderId ? Number(form.leaderId) : null,
      parentId: form.parentId ? Number(form.parentId) : null,
    };

    if (editing) {
      updateMutation.mutate(
        { id: editing.id, ...body },
        { onSuccess: () => toast.success(`${name} 부서를 수정했습니다.`) },
      );
    } else {
      createMutation.mutate(body, { onSuccess: () => toast.success(`${name} 부서를 생성했습니다.`) });
    }
    closeForm();
  }

  // ── 드래그로 상위 부서 옮기기 ──────────────────────────────────────────
  // 모달의 "상위 부서" 드롭다운과 **같은 일**을 한다. 드래그는 마우스 전용이라 이것만 두면
  // 키보드로는 계층을 못 바꾸므로, 드롭다운을 없애지 않고 나란히 둔다.
  const [draggingId, setDraggingId] = useState(null);
  const [dropTargetId, setDropTargetId] = useState(null); // null이면서 dragging이면 '최상위 영역'
  const [rootZoneActive, setRootZoneActive] = useState(false);

  const dragging = departments.find((d) => d.id === draggingId) ?? null;

  function handleDragStart(event, department) {
    setDraggingId(department.id);
    setDropTargetId(null);
    setRootZoneActive(false);
    event.dataTransfer.effectAllowed = 'move';
    // Firefox는 데이터가 없으면 드래그를 시작하지 않는다
    event.dataTransfer.setData('text/plain', String(department.id));
  }

  function handleDragEnd() {
    setDraggingId(null);
    setDropTargetId(null);
    setRootZoneActive(false);
  }

  // 놓을 수 있는 자리에서만 preventDefault를 부른다 — 안 부르면 브라우저가 드롭을 거부하고
  // 커서가 '금지'로 바뀐다. 즉 이 한 줄이 곧 시각 피드백이다.
  function handleDragOver(event, target) {
    if (!canDropDepartment(dragging, target, hasChildren)) return;
    event.preventDefault();
    event.dataTransfer.dropEffect = 'move';
    if (target) {
      setDropTargetId(target.id);
      setRootZoneActive(false);
    } else {
      setDropTargetId(null);
      setRootZoneActive(true);
    }
  }

  function handleDrop(event, target) {
    event.preventDefault();
    if (!canDropDepartment(dragging, target, hasChildren)) {
      handleDragEnd();
      return;
    }
    const moved = dragging;
    const nextParentId = resolveDropParentId(target);
    const parentName = nextParentId
      ? (departments.find((d) => d.id === nextParentId)?.name ?? '상위 부서')
      : null;

    updateMutation.mutate(buildDepartmentMoveBody(moved, nextParentId), {
      onSuccess: () =>
        toast.success(
          nextParentId
            ? `${moved.name}을(를) ${parentName} 하위로 옮겼습니다.`
            : `${moved.name}을(를) 최상위 부서로 옮겼습니다.`,
        ),
    });
    handleDragEnd();
  }

  function rowDropTone(department) {
    if (draggingId === department.id) return 'opacity-40';
    if (dropTargetId === department.id) return 'bg-accent/[0.12] ring-1 ring-inset ring-accent';
    return '';
  }

  function handleDeactivate() {
    if (!deactivateTarget) return;
    deactivateMutation.mutate(deactivateTarget.id, {
      onSuccess: () => toast.success(`${deactivateTarget.name} 부서를 비활성화했습니다.`),
    });
    setDeactivateTarget(null);
  }

  const saving = createMutation.isPending || updateMutation.isPending;
  // 수정 중인 부서 자신과, 자식을 가진 부서는 상위 부서 후보에서 제외한다.
  const parentOptions = rootDepartments.filter((d) => d.id !== editing?.id);
  const parentDisabled = Boolean(editing && hasChildren(editing.id));

  return (
    <div>
      <PageHeader title="부서 관리" subtitle="부서 생성·수정 및 팀장 지정" />

      <TableCard
        title="부서 목록"
        right={
          <Button Icon={Plus} size="sm" onClick={openCreate}>
            부서 추가
          </Button>
        }
        loading={departmentsQuery.isLoading}
        error={departmentsQuery.isError}
        errorLabel="부서 목록을 불러오지 못했습니다."
        onRetry={departmentsQuery.refetch}
        empty={!departmentsQuery.isLoading && orderedRows.length === 0}
        emptyLabel="등록된 부서가 없습니다. 부서를 추가해주세요."
      >
        <ResponsiveTable
          table={
            <>
              <Table className="min-w-[760px]">
                <THead>
                  <Th>부서명</Th>
                  <Th>설명</Th>
                  <Th>팀장</Th>
                  <Th right>관리</Th>
                </THead>
                <tbody>
                  {orderedRows.map((d) => (
                    <TR
                      key={d.id}
                      draggable
                      onDragStart={(e) => handleDragStart(e, d)}
                      onDragEnd={handleDragEnd}
                      onDragOver={(e) => handleDragOver(e, d)}
                      onDrop={(e) => handleDrop(e, d)}
                      className={`group cursor-grab active:cursor-grabbing ${rowDropTone(d)}`}
                    >
                      <Td className="font-medium text-ink-hi">
                        <span className={`inline-flex items-center gap-1.5 ${d.depth === 1 ? 'pl-5' : ''}`}>
                          <GripVertical
                            size={13}
                            className="text-ink-dim opacity-0 transition-opacity group-hover:opacity-100"
                            aria-hidden="true"
                          />
                          {d.depth === 1 && <CornerDownRight size={13} className="text-ink-dim" />}
                          {d.name}
                        </span>
                      </Td>
                      <Td className="text-ink-mute">{d.description}</Td>
                      <Td>
                        {d.leaderName ? (
                          <span className="text-ink-body">{d.leaderName}</span>
                        ) : (
                          <StatusBadge label="공석" tone="warn" />
                        )}
                      </Td>
                      <Td right>
                        <div className="flex items-center justify-end gap-1">
                          <IconButton Icon={Pencil} label="부서 수정" onClick={() => openEdit(d)} />
                          <IconButton
                            Icon={Trash2}
                            label="부서 비활성화"
                            tone="danger"
                            onClick={() => setDeactivateTarget(d)}
                          />
                        </div>
                      </Td>
                    </TR>
                  ))}
                </tbody>
              </Table>
              {/* 최상위로 빼기 — 드래그 중에만 나타난다. 평소에 두면 빈 영역이 늘 자리를 차지한다 */}
              {dragging && (
                <div
                  onDragOver={(e) => handleDragOver(e, null)}
                  onDrop={(e) => handleDrop(e, null)}
                  className={`mx-5 mb-3 rounded-card border border-dashed px-5 py-4 text-center text-[12px] transition-colors ${
                    rootZoneActive
                      ? 'border-accent bg-accent/[0.12] text-ink-hi'
                      : 'border-white/20 text-ink-faint'
                  }`}
                >
                  여기에 놓으면 <span className="font-medium text-ink-body">최상위 부서</span>가 됩니다
                </div>
              )}
            </>
          }
          cards={orderedRows.map((d) => {
            // 상위 부서 이름 — 카드는 표처럼 들여쓰기로 계층을 보여줄 폭이 없다. 들여쓰기가
            // 깊어지면 이름이 잘리므로, 글자로 명시한다 (좁은 폭에서도 안 잘림).
            const parentName = d.depth === 1 ? (departments.find((p) => p.id === d.parentId)?.name ?? '—') : null;
            return (
              <Card key={d.id} padding="tight" as="article">
                <div className="flex items-start justify-between gap-3">
                  <div className="min-w-0">
                    <p className="flex items-center gap-1.5 font-semibold text-ink-hi">
                      {d.depth === 1 && <CornerDownRight size={13} className="shrink-0 text-ink-dim" />}
                      <span className="truncate">{d.name}</span>
                    </p>
                    <p className="mt-1 text-[12px] text-ink-mute">{d.description}</p>
                  </div>
                  <div className="flex shrink-0 items-center gap-1">
                    <IconButton Icon={Pencil} label="부서 수정" onClick={() => openEdit(d)} />
                    <IconButton
                      Icon={Trash2}
                      label="부서 비활성화"
                      tone="danger"
                      onClick={() => setDeactivateTarget(d)}
                    />
                  </div>
                </div>

                <dl className="mt-4 grid grid-cols-[72px_1fr] gap-x-3 gap-y-2 text-[13px]">
                  <dt className="text-ink-faint">상위 부서</dt>
                  <dd className="min-w-0 text-ink-body">{parentName ?? '최상위'}</dd>
                  <dt className="text-ink-faint">팀장</dt>
                  <dd className="min-w-0">
                    {d.leaderName ? (
                      <span className="text-ink-body">{d.leaderName}</span>
                    ) : (
                      <StatusBadge label="공석" tone="warn" />
                    )}
                  </dd>
                </dl>

                {/* 드래그 대신 터치로 상위 부서를 바꾸는 진입점 — 새 흐름이 아니라 기존
                    부서 수정 폼(상위 부서 select 포함)을 그대로 연다 */}
                <Button
                  variant="secondary"
                  size="sm"
                  Icon={FolderTree}
                  className="mt-4 w-full justify-center"
                  onClick={() => openEdit(d)}
                >
                  상위 부서 변경
                </Button>
              </Card>
            );
          })}
        />

        <p className="border-t border-white/[0.12] px-5 py-3 text-[11px] text-ink-faint">
          {isMobile ? (
            <>
              * 상위 부서를 바꾸려면 각 카드의{' '}
              <span className="text-ink-mute">상위 부서 변경</span> 버튼을 누르세요. 계층은 2단계까지라,
              하위 부서를 가진 부서는 다른 부서 아래로 옮길 수 없습니다.
            </>
          ) : (
            <>
              * 부서를 <span className="text-ink-mute">끌어서 다른 부서 위에 놓으면</span> 그 부서의 하위로
              들어갑니다. 계층은 2단계까지라, 하위 부서를 가진 부서는 다른 부서 아래로 옮길 수 없습니다.
            </>
          )}
          <br />* 팀장이 공석이면 해당 부서원의 연차·복리후생 결재는 총관리자에게 넘어갑니다.
        </p>
      </TableCard>

      {/* 생성·수정 공용 모달 */}
      {formOpen && (
        <Modal
          title={editing ? '부서 수정' : '부서 추가'}
          onClose={closeForm}
          footer={
            <>
              <Button variant="secondary" onClick={closeForm} lift={false}>
                취소
              </Button>
              <Button onClick={handleSave} loading={saving} lift={false}>
                저장
              </Button>
            </>
          }
        >
          <div className="flex flex-col gap-4">
            <Field label="부서명" required error={errors.name}>
              <TextInput
                value={form.name}
                onChange={(e) => updateField('name', e.target.value)}
                placeholder="예: 개발팀"
                invalid={Boolean(errors.name)}
                autoFocus
              />
            </Field>

            <Field label="설명" required error={errors.description}>
              <Textarea
                value={form.description}
                onChange={(e) => updateField('description', e.target.value)}
                placeholder="부서가 담당하는 업무를 적어주세요."
                invalid={Boolean(errors.description)}
                rows={2}
              />
            </Field>

            <Field
              label="팀장"
              hint="지정하지 않으면 공석으로 두며, 결재는 총관리자가 처리합니다."
            >
              <Select value={form.leaderId} onChange={(e) => updateField('leaderId', e.target.value)}>
                <option value="">공석</option>
                {users.map((u) => (
                  <option key={u.id} value={u.id}>
                    {u.name} ({u.departmentName ?? '부서 미배정'})
                  </option>
                ))}
              </Select>
            </Field>

            <Field
              label="상위 부서"
              hint={
                parentDisabled
                  ? '하위 부서를 가진 부서는 다른 부서 아래로 옮길 수 없습니다 (부서 계층은 2단계까지).'
                  : '선택하지 않으면 최상위 부서가 됩니다. 계층은 2단계까지만 가능합니다.'
              }
            >
              <Select
                value={form.parentId}
                onChange={(e) => updateField('parentId', e.target.value)}
                disabled={parentDisabled}
              >
                <option value="">최상위 부서</option>
                {parentOptions.map((d) => (
                  <option key={d.id} value={d.id}>
                    {d.name}
                  </option>
                ))}
              </Select>
            </Field>
          </div>
        </Modal>
      )}

      {/* 비활성화 확인 — 소프트 삭제라 이력은 남지만 목록에서는 사라진다 */}
      <ConfirmDialog
        open={Boolean(deactivateTarget)}
        title="부서 비활성화"
        message={
          deactivateTarget &&
          `${deactivateTarget.name} 부서를 비활성화하시겠습니까? 목록과 부서 선택에서 사라지며, 소속 사원의 부서는 그대로 남습니다. 화면에서 되돌릴 수 없습니다.`
        }
        tone="danger"
        confirmLabel="비활성화"
        onConfirm={handleDeactivate}
        onCancel={() => setDeactivateTarget(null)}
        loading={deactivateMutation.isPending}
      />
    </div>
  );
}
