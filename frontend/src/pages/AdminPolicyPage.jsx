import { useState } from 'react';
import toast from 'react-hot-toast';
import { Pencil, Save } from 'lucide-react';
import { leavePolicies, adminConfigs, resetHistories } from '../mocks/data.js';
import PageHeader from '../components/ui/PageHeader.jsx';
import Card from '../components/ui/Card.jsx';

export default function AdminPolicyPage() {
  // 시스템 설정 값 (config.name → 문자열 value) 로컬 상태
  const [configValues, setConfigValues] = useState(() =>
    Object.fromEntries(adminConfigs.map((c) => [c.name, c.value])),
  );

  // 설정 값 변경 헬퍼
  const updateConfig = (name, value) =>
    setConfigValues((prev) => ({ ...prev, [name]: value }));

  return (
    <div>
      <PageHeader title="연차 정책" subtitle="근속년수별 연차·시스템 설정·소멸 이력 관리" />

      {/* 상단 2단: 근속년수별 정책 | 시스템 설정 */}
      <div className="grid grid-cols-1 gap-5 lg:grid-cols-2">
        {/* ① 근속년수별 연차 정책 */}
        <Card
          title="근속년수별 연차 정책"
          right={<span className="text-[11px] text-ink-faint">근로기준법 §60 기준</span>}
          bodyClassName="!p-0"
        >
          <div className="max-h-[420px] overflow-y-auto">
            <table className="w-full text-left text-[13px]">
              <thead className="sticky top-0 z-10">
                <tr className="border-b border-white/6 bg-navy-header">
                  <th className="px-5 py-3 text-[11px] font-semibold uppercase tracking-wider text-ink-faint">
                    근속 년차
                  </th>
                  <th className="px-5 py-3 text-right text-[11px] font-semibold uppercase tracking-wider text-ink-faint">
                    부여 일수
                  </th>
                </tr>
              </thead>
              <tbody>
                {leavePolicies.map((p) => (
                  <tr
                    key={p.id}
                    className="border-b border-white/5 transition-colors last:border-0 hover:bg-white/[0.02]"
                  >
                    <td className="whitespace-nowrap px-5 py-2.5 text-ink-body">{p.years}년차</td>
                    <td className="whitespace-nowrap px-5 py-2.5 text-right">
                      <div className="inline-flex items-center gap-2">
                        <span className="font-semibold text-ink-hi tabular-nums">{p.days}일</span>
                        <button
                          type="button"
                          onClick={() => toast('정책 수정 (준비 중)')}
                          title="일수 수정"
                          aria-label={`${p.years}년차 일수 수정`}
                          className="rounded-btn p-1 text-ink-faint transition-colors hover:bg-white/6 hover:text-ink-body"
                        >
                          <Pencil size={13} />
                        </button>
                      </div>
                    </td>
                  </tr>
                ))}
              </tbody>
            </table>
          </div>
          <p className="border-t border-white/6 px-5 py-3 text-[11px] text-ink-faint">
            * 21년차 25일이 법정 상한이며, 이후 근속에도 연차는 25일로 고정됩니다.
          </p>
        </Card>

        {/* ② 연차 시스템 설정 */}
        <Card title="연차 시스템 설정">
          <div className="flex flex-col">
            {adminConfigs.map((c) => (
              <div
                key={c.name}
                className="flex items-center justify-between gap-4 border-b border-white/6 py-3.5 first:pt-0 last:border-0 last:pb-0"
              >
                <div className="min-w-0">
                  <div className="text-[13px] font-medium text-ink-hi">{c.label}</div>
                  <div className="mt-0.5 text-[12px] text-ink-mute">{c.description}</div>
                </div>
                <div className="shrink-0">
                  <ConfigControl
                    config={c}
                    value={configValues[c.name]}
                    onChange={(v) => updateConfig(c.name, v)}
                  />
                </div>
              </div>
            ))}
          </div>
          <div className="mt-5 flex justify-end">
            <button
              type="button"
              onClick={() => toast.success('설정이 저장되었습니다. (데모)')}
              className="flex items-center gap-1.5 rounded-btn bg-accent px-4 py-2.5 text-[13px] font-semibold text-white shadow-btn transition-colors hover:bg-accent-dark"
            >
              <Save size={15} />
              설정 저장
            </button>
          </div>
        </Card>
      </div>

      {/* ③ 기산일 리셋·소멸 이력 */}
      <Card title="기산일 리셋 · 소멸 이력" bodyClassName="!p-0" className="mt-5">
        <div className="overflow-x-auto">
          <table className="w-full min-w-[560px] text-left text-[13px]">
            <thead>
              <tr className="border-b border-white/6 bg-navy-header">
                <th className="px-5 py-3 text-[11px] font-semibold uppercase tracking-wider text-ink-faint">
                  사원
                </th>
                <th className="px-5 py-3 text-[11px] font-semibold uppercase tracking-wider text-ink-faint">
                  리셋일
                </th>
                <th className="px-5 py-3 text-right text-[11px] font-semibold uppercase tracking-wider text-ink-faint">
                  이전 부여
                </th>
                <th className="px-5 py-3 text-right text-[11px] font-semibold uppercase tracking-wider text-ink-faint">
                  이전 사용
                </th>
                <th className="px-5 py-3 text-right text-[11px] font-semibold uppercase tracking-wider text-ink-faint">
                  소멸 일수
                </th>
              </tr>
            </thead>
            <tbody>
              {resetHistories.map((h) => (
                <tr
                  key={h.id}
                  className="border-b border-white/5 transition-colors last:border-0 hover:bg-white/[0.02]"
                >
                  <td className="whitespace-nowrap px-5 py-3 font-medium text-ink-hi">
                    {h.userName}
                  </td>
                  <td className="whitespace-nowrap px-5 py-3 text-ink-mute tabular-nums">
                    {h.resetDate}
                  </td>
                  <td className="whitespace-nowrap px-5 py-3 text-right text-ink-body tabular-nums">
                    {h.prevBase}일
                  </td>
                  <td className="whitespace-nowrap px-5 py-3 text-right text-ink-body tabular-nums">
                    {h.prevUsed}일
                  </td>
                  <td className="whitespace-nowrap px-5 py-3 text-right tabular-nums">
                    <span className={h.expiredDays > 0 ? 'font-semibold text-warn' : 'text-ink-mute'}>
                      {h.expiredDays}일
                    </span>
                  </td>
                </tr>
              ))}
            </tbody>
          </table>
        </div>
      </Card>
    </div>
  );
}

// 설정 항목 컨트롤 — boolean=토글 / number=숫자입력 / select=드롭다운
function ConfigControl({ config, value, onChange }) {
  if (config.type === 'boolean') {
    const on = value === 'true';
    return (
      <button
        type="button"
        role="switch"
        aria-checked={on}
        aria-label={config.label}
        onClick={() => onChange(on ? 'false' : 'true')}
        className={`relative h-6 w-11 rounded-full transition-colors ${
          on ? 'bg-accent' : 'bg-navy-btn2'
        }`}
      >
        <span
          className={`absolute top-0.5 h-5 w-5 rounded-full bg-white transition-transform ${
            on ? 'translate-x-5' : 'translate-x-0.5'
          }`}
        />
      </button>
    );
  }

  if (config.type === 'select') {
    return (
      <select
        value={value}
        onChange={(e) => onChange(e.target.value)}
        aria-label={config.label}
        className="rounded-btn border border-white/8 bg-navy-btn2 px-3 py-2 text-[13px] text-ink-body focus:border-accent/50 focus:outline-none"
      >
        {config.options.map((o) => (
          <option key={o} value={o}>
            {o}
          </option>
        ))}
      </select>
    );
  }

  // number
  return (
    <input
      type="number"
      value={value}
      onChange={(e) => onChange(e.target.value)}
      aria-label={config.label}
      className="w-24 rounded-btn border border-white/8 bg-navy-btn2 px-3 py-2 text-right text-[13px] text-ink-hi tabular-nums focus:border-accent/50 focus:outline-none"
    />
  );
}
