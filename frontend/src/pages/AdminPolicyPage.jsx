import { useEffect, useState } from 'react';
import toast from 'react-hot-toast';
import { Check, Pencil, Save, X } from 'lucide-react';
import PageHeader from '../components/ui/PageHeader.jsx';
import Card from '../components/ui/Card.jsx';
import TableCard from '../components/ui/TableCard.jsx';
import Table, { THead, Th, TR, Td } from '../components/ui/Table.jsx';
import IconButton from '../components/ui/IconButton.jsx';
import TextInput from '../components/ui/TextInput.jsx';
import Select from '../components/ui/Select.jsx';
import Toggle from '../components/ui/Toggle.jsx';
import Button from '../components/ui/Button.jsx';
import LoadingState from '../components/ui/LoadingState.jsx';
import {
  useLeavePolicies,
  useLeavePolicyConfigs,
  useResetHistories,
  useUpdateLeavePolicy,
  useUpdateLeavePolicyConfig,
} from '../hooks/usePolicies.js';

// 설정 항목(config.name) → 화면 표시 메타 — 실 API(GET /api/admin/configs)는 {id,name,value} 문자열만
// 내려주고 라벨·타입·설명 메타데이터가 없다. 3개 고정 키에 한해 프론트에서 하드코딩해 보강한다.
const CONFIG_META = {
  advance_leave_enabled: {
    label: '연차 당겨쓰기 허용',
    type: 'boolean',
    description: '잔여가 부족해도 당겨쓰기로 접수 (다음 기산일 정산)',
  },
  reminder_list_days: {
    label: '소진 안내 기준일',
    type: 'number',
    description: '기산일 N일 전부터 소진 안내 대상에 표시',
  },
  reminder_auto_cycle: {
    label: '자동 발송 주기',
    type: 'select',
    options: ['NONE', 'D30', 'D60', 'D90', 'QUARTER'],
    description: '기산일 임박 사원에게 자동 메일 발송 주기',
  },
};

// 연차 정책 — 근속년수별 정책(인라인 수정) / 시스템 설정(일괄 저장) / 리셋·소멸 이력 (docs/03, SYSTEM_ADMIN 전용)
export default function AdminPolicyPage() {
  const policiesQuery = useLeavePolicies();
  const configsQuery = useLeavePolicyConfigs();
  const historiesQuery = useResetHistories({ size: 50 });

  const updatePolicyMutation = useUpdateLeavePolicy();
  const updateConfigMutation = useUpdateLeavePolicyConfig();

  const policies = policiesQuery.data ?? [];
  const histories = historiesQuery.data?.content ?? [];

  // ① 근속년수별 정책 — 행별 인라인 수정(한 번에 한 행만)
  const [editingId, setEditingId] = useState(null);
  const [editValue, setEditValue] = useState('');

  function startEdit(policy) {
    setEditingId(policy.id);
    setEditValue(String(policy.annualLeaveDays));
  }
  function cancelEdit() {
    setEditingId(null);
    setEditValue('');
  }
  function saveEdit(policy) {
    const days = Number(editValue);
    if (editValue.trim() === '' || Number.isNaN(days) || days < 0) {
      toast.error('0 이상의 숫자를 입력해주세요.');
      return;
    }
    updatePolicyMutation.mutate(
      { id: policy.id, annualLeaveDays: days },
      {
        onSuccess: () => {
          toast.success(`${policy.yearsOfService}년차 정책을 수정했습니다.`);
          cancelEdit();
        },
      },
    );
  }

  // ② 시스템 설정 — name→value 로컬 편집 상태. 서버 응답이 처음 도착했을 때 한 번만 초기화하고,
  // 이후에는 사용자가 편집 중인 값을 그대로 유지한다(재조회로 덮어쓰지 않음).
  const [configValues, setConfigValues] = useState(null);
  const [savingConfigs, setSavingConfigs] = useState(false);

  useEffect(() => {
    if (configsQuery.data && configValues === null) {
      setConfigValues(Object.fromEntries(configsQuery.data.map((c) => [c.name, c.value])));
    }
  }, [configsQuery.data, configValues]);

  function updateConfigValue(name, value) {
    setConfigValues((prev) => ({ ...prev, [name]: value }));
  }

  // 저장 — PUT이 name 단건 갱신뿐이라 항목 수(3개)만큼 병렬 호출하고, 결과는 토스트 하나로 안내한다.
  async function handleSaveConfigs() {
    if (!configValues) return;
    setSavingConfigs(true);
    try {
      await Promise.all(
        Object.entries(configValues).map(([name, value]) => updateConfigMutation.mutateAsync({ name, value })),
      );
      toast.success('설정이 저장되었습니다.');
    } catch {
      // 실패는 api 인터셉터가 토스트로 일괄 처리
    } finally {
      setSavingConfigs(false);
    }
  }

  return (
    <div>
      <PageHeader title="연차 정책" subtitle="근속년수별 연차·시스템 설정·소멸 이력 관리" />

      {/* 상단 2단: 근속년수별 정책 | 시스템 설정 */}
      <div className="grid grid-cols-1 gap-5 lg:grid-cols-2">
        {/* ① 근속년수별 연차 정책 */}
        <TableCard
          title="근속년수별 연차 정책"
          right={<span className="text-[11px] text-ink-faint">근로기준법 §60 기준</span>}
          loading={policiesQuery.isLoading}
          empty={!policiesQuery.isLoading && policies.length === 0}
          emptyLabel="등록된 정책이 없습니다."
        >
          <div className="max-h-[420px] overflow-y-auto">
            <Table>
              <THead>
                <Th>근속 년차</Th>
                <Th right>부여 일수</Th>
              </THead>
              <tbody>
                {policies.map((p) => (
                  <TR key={p.id}>
                    <Td>{p.yearsOfService}년차</Td>
                    <Td right>
                      {editingId === p.id ? (
                        <div className="inline-flex items-center gap-1.5">
                          <TextInput
                            type="number"
                            min="0"
                            step="0.5"
                            value={editValue}
                            onChange={(e) => setEditValue(e.target.value)}
                            onKeyDown={(e) => {
                              if (e.key === 'Enter') saveEdit(p);
                              if (e.key === 'Escape') cancelEdit();
                            }}
                            autoFocus
                            className="w-20 !py-1.5 text-right tabular-nums"
                          />
                          <IconButton
                            Icon={Check}
                            label="저장"
                            tone="accent"
                            disabled={updatePolicyMutation.isPending}
                            onClick={() => saveEdit(p)}
                          />
                          <IconButton
                            Icon={X}
                            label="취소"
                            disabled={updatePolicyMutation.isPending}
                            onClick={cancelEdit}
                          />
                        </div>
                      ) : (
                        <div className="inline-flex items-center gap-2">
                          <span className="font-semibold text-ink-hi tabular-nums">
                            {Number(p.annualLeaveDays)}일
                          </span>
                          <IconButton
                            Icon={Pencil}
                            label={`${p.yearsOfService}년차 일수 수정`}
                            onClick={() => startEdit(p)}
                          />
                        </div>
                      )}
                    </Td>
                  </TR>
                ))}
              </tbody>
            </Table>
          </div>
          <p className="border-t border-white/[0.12] px-5 py-3 text-[11px] text-ink-faint">
            * 21년차 25일이 법정 상한이며, 이후 근속에도 연차는 25일로 고정됩니다.
          </p>
        </TableCard>

        {/* ② 연차 시스템 설정 */}
        <Card title="연차 시스템 설정">
          {configValues === null ? (
            <LoadingState />
          ) : (
            <>
              <div className="flex flex-col">
                {configsQuery.data
                  .filter((c) => CONFIG_META[c.name])
                  .map((c) => {
                    const meta = CONFIG_META[c.name];
                    return (
                      <div
                        key={c.name}
                        className="flex items-center justify-between gap-4 border-b border-white/[0.10] py-3.5 first:pt-0 last:border-0 last:pb-0"
                      >
                        <div className="min-w-0">
                          <div className="text-[13px] font-medium text-ink-hi">{meta.label}</div>
                          <div className="mt-0.5 text-[12px] text-ink-mute">{meta.description}</div>
                        </div>
                        <div className="shrink-0">
                          <ConfigControl
                            meta={meta}
                            value={configValues[c.name]}
                            onChange={(v) => updateConfigValue(c.name, v)}
                          />
                        </div>
                      </div>
                    );
                  })}
              </div>
              <div className="mt-5 flex justify-end">
                <Button Icon={Save} onClick={handleSaveConfigs} loading={savingConfigs}>
                  설정 저장
                </Button>
              </div>
            </>
          )}
        </Card>
      </div>

      {/* ③ 기산일 리셋·소멸 이력 */}
      <TableCard
        title="기산일 리셋 · 소멸 이력"
        className="mt-5"
        loading={historiesQuery.isLoading}
        empty={!historiesQuery.isLoading && histories.length === 0}
        emptyLabel="리셋 이력이 없습니다."
      >
        <Table className="min-w-[560px]">
          <THead>
            <Th>사원</Th>
            <Th>리셋일</Th>
            <Th right>이전 부여</Th>
            <Th right>이전 사용</Th>
            <Th right>소멸 일수</Th>
          </THead>
          <tbody>
            {histories.map((h) => (
              <TR key={h.id}>
                <Td className="font-medium text-ink-hi">{h.userName}</Td>
                <Td className="text-ink-mute tabular-nums">{h.resetDate}</Td>
                <Td right className="text-ink-body tabular-nums">
                  {Number(h.prevBaseDays)}일
                </Td>
                <Td right className="text-ink-body tabular-nums">
                  {Number(h.prevUseDays)}일
                </Td>
                <Td right className="tabular-nums">
                  <span className={Number(h.expiredDays) > 0 ? 'font-semibold text-warn' : 'text-ink-mute'}>
                    {Number(h.expiredDays)}일
                  </span>
                </Td>
              </TR>
            ))}
          </tbody>
        </Table>
      </TableCard>
    </div>
  );
}

// 설정 항목 컨트롤 — boolean=토글 / number=숫자입력 / select=드롭다운 (CONFIG_META.type 기준)
function ConfigControl({ meta, value, onChange }) {
  if (meta.type === 'boolean') {
    return <Toggle checked={value === 'true'} onChange={(v) => onChange(v ? 'true' : 'false')} label={meta.label} />;
  }

  if (meta.type === 'select') {
    return (
      <Select value={value} onChange={(e) => onChange(e.target.value)} aria-label={meta.label} className="w-32">
        {meta.options.map((o) => (
          <option key={o} value={o}>
            {o}
          </option>
        ))}
      </Select>
    );
  }

  // number
  return (
    <TextInput
      type="number"
      min="0"
      value={value}
      onChange={(e) => onChange(e.target.value)}
      aria-label={meta.label}
      className="w-24 text-right tabular-nums"
    />
  );
}
