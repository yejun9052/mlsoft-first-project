import { STATUS_LABEL, STATUS_TONE } from '../../constants/status.js';

// 상태 배지 — 틴트 배경 pill (docs/05 배지 틴트 규칙)
// 승인=초록 / 대기·취소대기=주황 / 반려=빨강 / 취소=회색
// 다른 컴포넌트(Chip/Stat 등)가 같은 틴트 규칙을 쓸 수 있게 별도 export — "상태 의미" 전용 팔레트이므로
// 장식적 용도(예: 복리후생 카테고리 아이콘 색)로는 재사용하지 않는다.
export const TONE_CLASS = {
  ok: 'bg-ok/12 text-ok',
  warn: 'bg-warn/13 text-warn',
  danger: 'bg-danger/13 text-danger',
  muted: 'bg-white/6 text-ink-mute',
  accent: 'bg-accent/16 text-accent-light',
};

export default function StatusBadge({ status, label, tone }) {
  const resolvedTone = tone ?? STATUS_TONE[status] ?? 'muted';
  const text = label ?? STATUS_LABEL[status] ?? status;
  return (
    <span
      className={`inline-flex items-center rounded-badge px-2.5 py-1 text-[11px] font-semibold ${
        TONE_CLASS[resolvedTone] ?? TONE_CLASS.muted
      }`}
    >
      {text}
    </span>
  );
}
