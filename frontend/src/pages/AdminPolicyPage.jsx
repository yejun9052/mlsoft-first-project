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
import ErrorState from '../components/ui/ErrorState.jsx';
import {
  useLeavePolicies,
  useLeavePolicyConfigs,
  useResetHistories,
  useUpdateLeavePolicy,
  useUpdateLeavePolicyConfig,
} from '../hooks/usePolicies.js';

// 설정 항목의 라벨·타입·범위·설명은 전부 서버가 내려준다 (GET /api/admin/configs).
// 한때 이 파일에 CONFIG_META로 하드코딩돼 있었는데, 그러면 서버에 설정을 추가할 때마다 여기도
// 고쳐야 하고 빼먹으면 그 설정이 화면에서 조용히 사라졌다. 지금은 프론트가 키 이름을 모른다.

// 서버 값 검증 — 저장 전에 범위를 확인해 즉시 안내한다 (서버도 같은 검증을 하지만 왕복을 아낀다)
function validateConfigValue(config, value) {
  if (config.type === 'BOOLEAN' || config.type === 'ENUM') return null;
  const trimmed = String(value).trim();
  if (trimmed === '' || Number.isNaN(Number(trimmed))) {
    return `${config.label}: 숫자를 입력해주세요.`;
  }
  const num = Number(trimmed);
  if (config.type === 'INTEGER' && !Number.isInteger(num)) {
    return `${config.label}: 정수를 입력해주세요.`;
  }
  if (config.min !== null && num < Number(config.min)) {
    return `${config.label}: ${Number(config.min)} 이상이어야 합니다.`;
  }
  if (config.max !== null && num > Number(config.max)) {
    return `${config.label}: ${Number(config.max)} 이하여야 합니다.`;
  }
  return null;
}

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

  // ② 시스템 설정 — name→value 로컬 편집 상태.
  // 재조회 때 편집 중인 값은 유지하고 **로컬에 없는 키만** 서버 값으로 채운다.
  // 처음 한 번만 초기화하면 서버 카탈로그에 설정이 추가됐을 때 그 키가 로컬 상태에 영원히 없어서,
  // 저장 시 value=undefined가 전송되고 다른 정상 변경까지 400으로 막힌다.
  const [configValues, setConfigValues] = useState(null);
  const [savingConfigs, setSavingConfigs] = useState(false);

  useEffect(() => {
    if (!configsQuery.data) return;
    setConfigValues((prev) => {
      const next = { ...(prev ?? {}) };
      configsQuery.data.forEach((c) => {
        if (!(c.name in next)) next[c.name] = c.value;
      });
      return next;
    });
  }, [configsQuery.data]);

  function updateConfigValue(name, value) {
    setConfigValues((prev) => ({ ...prev, [name]: value }));
  }

  // 표시·비교·전송이 항상 같은 값을 쓰게 한다 (한쪽만 fallback하면 위 버그가 재발한다)
  function currentValue(config) {
    return configValues?.[config.name] ?? config.value;
  }

  // 저장 — PUT이 name 단건 갱신뿐이라 항목마다 호출한다. 단 서버 값과 다른 항목만 보낸다
  // (설정이 늘어날수록 전체 재전송은 낭비이고, 로그에도 바꾸지 않은 설정 변경이 남는다).
  async function handleSaveConfigs() {
    if (!configValues) return;

    const changed = (configsQuery.data ?? []).filter((c) => currentValue(c) !== c.value);
    if (changed.length === 0) {
      toast.success('변경된 설정이 없습니다.');
      return;
    }

    const firstError = changed
      .map((c) => validateConfigValue(c, currentValue(c)))
      .find((message) => message !== null);
    if (firstError) {
      toast.error(firstError);
      return;
    }

    setSavingConfigs(true);
    try {
      await Promise.all(
        changed.map((c) => updateConfigMutation.mutateAsync({ name: c.name, value: currentValue(c) })),
      );
      toast.success(`설정 ${changed.length}건을 저장했습니다.`);
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
          error={policiesQuery.isError}
          errorLabel="연차 정책을 불러오지 못했습니다."
          onRetry={policiesQuery.refetch}
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
                          {/* 저장은 초록 타일이지만 취소는 빨간 "윤곽선"이다 — 채우면 반려 버튼과
                              같은 무게가 되는데, 여기 X는 편집을 접는 것일 뿐 파괴적이지 않다.
                              채워진 타일은 되돌리기 어려운 이지선다에만 쓴다 (IconButton 주석) */}
                          <IconButton
                            Icon={Check}
                            label="저장"
                            tone="confirm"
                            disabled={updatePolicyMutation.isPending}
                            onClick={() => saveEdit(p)}
                          />
                          <IconButton
                            Icon={X}
                            label="취소"
                            tone="danger"
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
          {/* configValues === null만 보면 조회 실패 시 스피너가 영원히 돈다 (리뷰 F-6) */}
          {configsQuery.isError ? (
            <ErrorState label="시스템 설정을 불러오지 못했습니다." onRetry={configsQuery.refetch} />
          ) : configValues === null ? (
            <LoadingState />
          ) : (
            <>
              <div className="flex flex-col">
                {configsQuery.data.map((c) => (
                  <div
                    key={c.name}
                    className="flex items-start justify-between gap-4 border-b border-white/[0.10] py-3.5 first:pt-0 last:border-0 last:pb-0"
                  >
                    <div className="min-w-0">
                      <div className="flex flex-wrap items-center gap-2">
                        <span className="text-[13px] font-medium text-ink-hi">{c.label}</span>
                        {c.status === 'PENDING_FEATURE' && (
                          <span
                            className="rounded-badge border border-warn/30 bg-warn/10 px-2 py-0.5 text-[10px] font-semibold text-warn"
                            title="값은 저장되지만 이 값을 읽는 기능이 아직 구현되지 않았습니다."
                          >
                            미동작
                          </span>
                        )}
                      </div>
                      <div className="mt-0.5 text-[12px] text-ink-mute">{c.description}</div>
                      {(c.min !== null || c.max !== null) && (
                        <div className="mt-1 text-[11px] text-ink-faint tabular-nums">
                          허용 범위 {Number(c.min)}
                          {c.unit ?? ''} ~ {Number(c.max)}
                          {c.unit ?? ''} · 기본값 {c.defaultValue}
                          {c.unit ?? ''}
                        </div>
                      )}
                    </div>
                    <div className="flex shrink-0 items-center gap-1.5 pt-0.5">
                      <ConfigControl
                        config={c}
                        value={currentValue(c)}
                        onChange={(v) => updateConfigValue(c.name, v)}
                      />
                      {c.unit && c.type !== 'BOOLEAN' && (
                        <span className="text-[12px] text-ink-mute">{c.unit}</span>
                      )}
                    </div>
                  </div>
                ))}
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
        error={historiesQuery.isError}
        errorLabel="리셋 이력을 불러오지 못했습니다."
        onRetry={historiesQuery.refetch}
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

// 설정 항목 컨트롤 — 서버가 내려준 type으로 결정한다 (키 이름을 보지 않는다).
// BOOLEAN=토글 / ENUM=드롭다운 / INTEGER·DECIMAL=숫자입력(범위·증분은 서버 메타에서)
function ConfigControl({ config, value, onChange }) {
  if (config.type === 'BOOLEAN') {
    return <Toggle checked={value === 'true'} onChange={(v) => onChange(v ? 'true' : 'false')} label={config.label} />;
  }

  if (config.type === 'ENUM') {
    return (
      <Select value={value} onChange={(e) => onChange(e.target.value)} aria-label={config.label} className="w-32">
        {config.options.map((o) => (
          <option key={o} value={o}>
            {o}
          </option>
        ))}
      </Select>
    );
  }

  // INTEGER · DECIMAL — DECIMAL은 연차 일수 도메인이라 0.5일 단위를 허용한다
  return (
    <TextInput
      type="number"
      min={config.min !== null ? Number(config.min) : undefined}
      max={config.max !== null ? Number(config.max) : undefined}
      step={config.type === 'DECIMAL' ? '0.5' : '1'}
      value={value}
      onChange={(e) => onChange(e.target.value)}
      aria-label={config.label}
      className="w-24 text-right tabular-nums"
    />
  );
}
