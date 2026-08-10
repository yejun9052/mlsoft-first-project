import { useState } from 'react';
import toast from 'react-hot-toast';
import { Pencil, Plus, Trash2 } from 'lucide-react';
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
import Pagination from '../components/ui/Pagination.jsx';
import { WELFARE_TARGET_LABEL } from '../constants/welfare.js';
import {
  useCreateWelfarePolicy,
  useDeactivateWelfarePolicy,
  useUpdateWelfarePolicy,
  useWelfarePolicies,
} from '../hooks/useWelfare.js';

const PAGE_SIZE = 10;

// 서버 WelfarePolicyRequest의 제약과 같은 값 — @DecimalMin 0.0 / @DecimalMax 365.0
const MIN_DAYS = 0;
const MAX_DAYS = 365;

const EMPTY_FORM = {
  category: '',
  target: 'SELF',
  defaultDays: '',
  defaultEvidence: '',
  description: '',
};

// 복리후생 정책 관리 — 정책 추가·수정·비활성화 (docs/03 복리후생 정책, SYSTEM_ADMIN 전용).
// 백엔드 CRUD는 완성돼 있었고 화면만 없었다. 화면이 없는 동안 정책은 DataInitializer의 시드
// 6종에서 고정돼 있었다 — 새 경조사 항목을 추가할 방법이 DB 직접 INSERT뿐이었다.
export default function AdminWelfarePoliciesPage() {
  const [page, setPage] = useState(0);
  const policiesQuery = useWelfarePolicies({ page, size: PAGE_SIZE });

  const createMutation = useCreateWelfarePolicy();
  const updateMutation = useUpdateWelfarePolicy();
  const deactivateMutation = useDeactivateWelfarePolicy();

  const rows = policiesQuery.data?.content ?? [];
  const pageInfo = policiesQuery.data?.page;

  // 모달 — 생성/수정 폼 하나를 공유한다. editing이 null이면 생성 모드 (부서 관리와 같은 구조).
  const [formOpen, setFormOpen] = useState(false);
  const [editing, setEditing] = useState(null);
  const [form, setForm] = useState(EMPTY_FORM);
  const [errors, setErrors] = useState({});
  const [deactivateTarget, setDeactivateTarget] = useState(null);

  function openCreate() {
    setEditing(null);
    setForm(EMPTY_FORM);
    setErrors({});
    setFormOpen(true);
  }

  function openEdit(policy) {
    setEditing(policy);
    setForm({
      category: policy.category,
      target: policy.target,
      // 서버는 BigDecimal("7.0")을 내려준다. 입력 칸에는 숫자 문자열로 그대로 둔다
      defaultDays: String(Number(policy.defaultDays)),
      defaultEvidence: policy.defaultEvidence ?? '',
      description: policy.description ?? '',
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

  // 저장 — 성공 토스트는 여기서, 실패는 api 인터셉터가 일괄 처리 (AdminDepartmentsPage와 동일 컨벤션).
  // 구분+대상 조합 중복은 서버가 409로 막으므로 화면에서 미리 검사하지 않는다 —
  // 목록이 페이징이라 현재 페이지에 없는 정책과의 중복을 클라이언트가 알 수 없다.
  function handleSave() {
    const category = form.category.trim();
    const defaultEvidence = form.defaultEvidence.trim();
    const description = form.description.trim();
    const days = Number(form.defaultDays);

    const nextErrors = {};
    if (!category) nextErrors.category = '구분을 입력해주세요.';
    if (form.defaultDays === '' || Number.isNaN(days)) {
      nextErrors.defaultDays = '부여 일수를 입력해주세요.';
    } else if (days < MIN_DAYS || days > MAX_DAYS) {
      nextErrors.defaultDays = `부여 일수는 ${MIN_DAYS}~${MAX_DAYS}일 사이여야 합니다.`;
    }
    if (!defaultEvidence) nextErrors.defaultEvidence = '제출자료 안내를 입력해주세요.';
    if (!description) nextErrors.description = '설명을 입력해주세요.';
    setErrors(nextErrors);
    if (Object.keys(nextErrors).length > 0) return;

    const body = {
      category,
      target: form.target,
      // 서버가 DECIMAL(4,1)로 받으므로 소수 한 자리로 맞춰 보낸다
      defaultDays: days.toFixed(1),
      defaultEvidence,
      description,
    };

    if (editing) {
      updateMutation.mutate(
        { id: editing.id, ...body },
        { onSuccess: () => toast.success(`${category} 정책을 수정했습니다.`) },
      );
    } else {
      createMutation.mutate(body, {
        onSuccess: () => toast.success(`${category} 정책을 추가했습니다.`),
      });
    }
    closeForm();
  }

  function handleDeactivate() {
    if (!deactivateTarget) return;
    deactivateMutation.mutate(deactivateTarget.id, {
      onSuccess: () => toast.success(`${deactivateTarget.category} 정책을 비활성화했습니다.`),
    });
    setDeactivateTarget(null);
  }

  const saving = createMutation.isPending || updateMutation.isPending;

  return (
    <div>
      <PageHeader title="복리후생 정책" subtitle="경조사 항목·대상별 부여 일수 관리" />

      <TableCard
        title="정책 목록"
        right={
          <Button Icon={Plus} size="sm" onClick={openCreate}>
            정책 추가
          </Button>
        }
        loading={policiesQuery.isLoading}
        error={policiesQuery.isError}
        errorLabel="복리후생 정책을 불러오지 못했습니다."
        onRetry={policiesQuery.refetch}
        empty={!policiesQuery.isLoading && rows.length === 0}
        emptyLabel="등록된 정책이 없습니다. 정책을 추가해주세요."
      >
        <Table className="min-w-[860px]">
          <THead>
            <Th>구분</Th>
            <Th>대상</Th>
            <Th right>부여 일수</Th>
            <Th>제출자료</Th>
            <Th>설명</Th>
            <Th right>관리</Th>
          </THead>
          <tbody>
            {rows.map((p) => (
              <TR key={p.id}>
                <Td className="font-medium text-ink-hi">{p.category}</Td>
                <Td className="text-ink-body">{WELFARE_TARGET_LABEL[p.target] ?? p.target}</Td>
                <Td right className="text-ink-body tabular-nums">
                  {Number(p.defaultDays)}일
                </Td>
                <Td className="max-w-[200px] truncate text-ink-mute" title={p.defaultEvidence}>
                  {p.defaultEvidence || '—'}
                </Td>
                <Td className="max-w-[220px] truncate text-ink-mute" title={p.description}>
                  {p.description || '—'}
                </Td>
                <Td right>
                  <div className="flex items-center justify-end gap-1">
                    <IconButton Icon={Pencil} label="정책 수정" onClick={() => openEdit(p)} />
                    <IconButton
                      Icon={Trash2}
                      label="정책 비활성화"
                      tone="danger"
                      onClick={() => setDeactivateTarget(p)}
                    />
                  </div>
                </Td>
              </TR>
            ))}
          </tbody>
        </Table>
        <Pagination
          page={page}
          totalPages={pageInfo?.totalPages}
          totalElements={pageInfo?.totalElements}
          onChange={setPage}
        />
        <p className="border-t border-white/[0.12] px-5 py-3 text-[11px] text-ink-faint">
          * 같은 구분·대상 조합은 하나만 둘 수 있습니다. 비활성화한 정책은 목록에서 사라지지만 이미
          접수된 신청의 근거는 그대로 남습니다.
        </p>
      </TableCard>

      {/* 생성·수정 공용 모달 */}
      {formOpen && (
        <Modal
          title={editing ? '정책 수정' : '정책 추가'}
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
            <Field label="구분" required error={errors.category} hint="예: 결혼, 출산, 조의">
              <TextInput
                value={form.category}
                onChange={(e) => updateField('category', e.target.value)}
                placeholder="예: 결혼"
                invalid={Boolean(errors.category)}
                autoFocus
              />
            </Field>

            <Field label="대상" required>
              <Select value={form.target} onChange={(e) => updateField('target', e.target.value)}>
                {Object.entries(WELFARE_TARGET_LABEL).map(([value, label]) => (
                  <option key={value} value={value}>
                    {label}
                  </option>
                ))}
              </Select>
            </Field>

            <Field
              label="부여 일수"
              required
              error={errors.defaultDays}
              hint="반일 단위로 지정할 수 있습니다 (예: 0.5)."
            >
              <TextInput
                type="number"
                min={MIN_DAYS}
                max={MAX_DAYS}
                step="0.5"
                value={form.defaultDays}
                onChange={(e) => updateField('defaultDays', e.target.value)}
                placeholder="예: 7"
                invalid={Boolean(errors.defaultDays)}
              />
            </Field>

            <Field label="제출자료 안내" required error={errors.defaultEvidence}>
              <TextInput
                value={form.defaultEvidence}
                onChange={(e) => updateField('defaultEvidence', e.target.value)}
                placeholder="예: 청첩장 또는 혼인관계증명서"
                invalid={Boolean(errors.defaultEvidence)}
              />
            </Field>

            <Field label="설명" required error={errors.description}>
              <Textarea
                value={form.description}
                onChange={(e) => updateField('description', e.target.value)}
                placeholder="사원에게 보일 안내 문구를 적어주세요."
                invalid={Boolean(errors.description)}
                rows={2}
              />
            </Field>
          </div>
        </Modal>
      )}

      {/* 비활성화 확인 — 소프트 삭제라 기존 신청의 근거는 남는다 */}
      <ConfirmDialog
        open={Boolean(deactivateTarget)}
        title="정책 비활성화"
        message={
          deactivateTarget &&
          `${deactivateTarget.category}(${
            WELFARE_TARGET_LABEL[deactivateTarget.target] ?? deactivateTarget.target
          }) 정책을 비활성화하시겠습니까? 신청 화면과 목록에서 사라지며, 이미 접수된 신청은 그대로 유지됩니다. 화면에서 되돌릴 수 없습니다.`
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
