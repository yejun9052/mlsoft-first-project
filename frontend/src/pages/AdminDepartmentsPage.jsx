import { useMemo, useState } from 'react';
import toast from 'react-hot-toast';
import { CornerDownRight, Pencil, Plus, Trash2 } from 'lucide-react';
import PageHeader from '../components/ui/PageHeader.jsx';
import TableCard from '../components/ui/TableCard.jsx';
import Table, { THead, Th, TR, Td } from '../components/ui/Table.jsx';
import IconButton from '../components/ui/IconButton.jsx';
import Modal from '../components/ui/Modal.jsx';
import Field from '../components/ui/Field.jsx';
import TextInput from '../components/ui/TextInput.jsx';
import Textarea from '../components/ui/Textarea.jsx';
import Select from '../components/ui/Select.jsx';
import Button from '../components/ui/Button.jsx';
import ConfirmDialog from '../components/ui/ConfirmDialog.jsx';
import StatusBadge from '../components/ui/StatusBadge.jsx';
import {
  useCreateDepartment,
  useDeactivateDepartment,
  useDepartments,
  useUpdateDepartment,
} from '../hooks/useDepartments.js';
import { useUsers } from '../hooks/useUsers.js';

// 팀장 후보 드롭다운에 담을 최대 인원 — 사내 규모(수십 명)를 넘어서면 검색형 선택으로 바꿔야 한다.
const LEADER_CANDIDATE_SIZE = 200;

const EMPTY_FORM = { name: '', description: '', leaderId: '', parentId: '' };

// 부서 관리 — 부서 생성·수정(팀장 지정 포함)·비활성화 (docs/03 부서, SYSTEM_ADMIN 전용).
// 팀장 지정이 이 화면에만 있다 — 부서 팀장이 연차 기본 승인자이고, 공석이면 SYSTEM_ADMIN으로
// fallback 되므로(검증 Y-3) 결재 흐름을 정상화하려면 여기서 팀장을 채워야 한다.
export default function AdminDepartmentsPage() {
  const departmentsQuery = useDepartments();
  // 팀장 후보 — 재직 중인 전 구성원. 부서 배정은 구성원 관리에서 하므로, 신설 부서에도 팀장을
  // 지정할 수 있도록 소속으로 후보를 좁히지 않는다.
  const usersQuery = useUsers({ size: LEADER_CANDIDATE_SIZE });

  const createMutation = useCreateDepartment();
  const updateMutation = useUpdateDepartment();
  const deactivateMutation = useDeactivateDepartment();

  const departments = useMemo(() => departmentsQuery.data ?? [], [departmentsQuery.data]);
  const users = usersQuery.data?.content ?? [];

  // 모달 — 생성/수정 폼 하나를 공유한다. editing이 null이면 생성 모드.
  const [formOpen, setFormOpen] = useState(false);
  const [editing, setEditing] = useState(null);
  const [form, setForm] = useState(EMPTY_FORM);
  // 필드별 에러 — 서버도 @NotBlank로 막지만, 왕복 없이 어느 칸이 비었는지 바로 보이게 한다
  const [errors, setErrors] = useState({});
  // 비활성화 확인
  const [deactivateTarget, setDeactivateTarget] = useState(null);

  // 상위 부서 → 하위 부서 순으로 재배열 (API는 id 순 플랫 목록만 준다).
  // 2단계 계층이라 루트 아래 자식을 붙이는 것으로 충분하다.
  const orderedRows = useMemo(() => {
    const roots = departments.filter((d) => d.parentId == null);
    const childrenOf = (parentId) => departments.filter((d) => d.parentId === parentId);
    return roots.flatMap((root) => [
      { ...root, depth: 0 },
      ...childrenOf(root.id).map((child) => ({ ...child, depth: 1 })),
    ]);
  }, [departments]);

  const rootDepartments = departments.filter((d) => d.parentId == null);
  // 자식이 있는 부서는 스스로 하위 부서가 될 수 없다 — 3단계가 되기 때문. 서버는 "상위가 루트인가"만
  // 검증하므로(2단계 강제의 반쪽) 이 방향은 화면에서 막는다.
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
        empty={!departmentsQuery.isLoading && orderedRows.length === 0}
        emptyLabel="등록된 부서가 없습니다. 부서를 추가해주세요."
      >
        <Table className="min-w-[760px]">
          <THead>
            <Th>부서명</Th>
            <Th>설명</Th>
            <Th>팀장</Th>
            <Th right>관리</Th>
          </THead>
          <tbody>
            {orderedRows.map((d) => (
              <TR key={d.id}>
                <Td className="font-medium text-ink-hi">
                  <span className={`inline-flex items-center gap-1.5 ${d.depth === 1 ? 'pl-5' : ''}`}>
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
        <p className="border-t border-white/6 px-5 py-3 text-[11px] text-ink-faint">
          * 팀장이 공석이면 해당 부서원의 연차·복리후생 결재는 총관리자에게 넘어갑니다.
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
