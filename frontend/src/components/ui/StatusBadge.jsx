import { STATUS_LABEL, STATUS_TONE } from '../../constants/status.js';
import { TONE_CLASS } from './statusTone.js';

// 상태 배지 — 틴트 배경 + 얇은 링 pill (docs/05 배지 틴트 규칙)
// 승인=초록 / 대기·취소대기=주황 / 반려=빨강 / 취소=회색
// 글래스 표면 위에서는 틴트 배경만으로 경계가 흐려져, 같은 색 링을 둘러 형태를 잡아준다.
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
