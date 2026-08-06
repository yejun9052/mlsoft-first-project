// 통계 한 칸 — Dashboard/History의 세로형 Stat, Approvals AdminStat, Team SummaryStat(아이콘),
// Dashboard 게이지 하단의 스와치형 미니 통계까지 4변종을 하나로 통합.
//
// - Icon 또는 swatch를 주면: 가로형(아이콘/색점 + 라벨·값) — Team 아이콘 통계, 게이지 범례성 통계용.
// - 둘 다 없으면: 세로형(라벨 위 / 값 아래, 박스 없음) — size로 강조도 조절.
//   size='hero' → 대시보드 잔여 연차처럼 52px 그라데이션 강조(1페이지에 1개만 쓰는 용도, tone 무시).
//   size='lg'(기본) → 28px, size='md' → 22px. tone은 값 텍스트 색 클래스(예: 'text-warn')로 오버라이드.
// - caption: 값 아래 보조 설명 한 줄(History의 "확정 12일 · 대기 1일" 같은 선차감 구분 표기에 사용).
//
// 숫자는 전부 tabular-nums — 값이 갱신될 때 자릿수가 흔들리지 않게 한다.
export default function Stat({ label, value, unit, size = 'lg', tone, caption, Icon, swatch, className = '' }) {
  const hasLeading = Boolean(Icon) || Boolean(swatch);

  if (hasLeading) {
    return (
      <div className={`flex items-center gap-2.5 ${className}`}>
        {Icon ? (
          <span className="flex h-9 w-9 shrink-0 items-center justify-center rounded-btn border border-white/[0.12] bg-white/[0.04] text-accent-light">
            <Icon size={16} />
          </span>
        ) : (
          <span className={`h-3 w-3 shrink-0 rounded-sm ${swatch}`} />
        )}
        <div className="flex flex-col">
          <span className="text-[11px] font-medium text-ink-mute">{label}</span>
          <span className={`text-[14px] font-semibold tabular-nums ${tone ?? 'text-ink-hi'}`}>
            {value}
            {unit && <span className="ml-0.5 text-[12px] font-medium text-ink-mute">{unit}</span>}
          </span>
        </div>
      </div>
    );
  }

  const valueClass =
    size === 'hero'
      ? 'accent-gradient-text text-[52px] font-extrabold leading-none tracking-[-0.04em] tabular-nums'
      : size === 'md'
        ? `text-[22px] font-bold leading-none tabular-nums ${tone ?? 'text-ink-hi'}`
        : `text-[28px] font-bold leading-none tabular-nums ${tone ?? 'text-ink-hi'}`;

  return (
    <div className={`flex flex-col gap-1.5 ${className}`}>
      <span className="text-[12px] font-medium text-ink-mute">{label}</span>
      {/* 단위는 값 span 밖에 형제로 둔다 — hero의 accent-gradient-text가 -webkit-text-fill-color:transparent를
          쓰는데 이 속성은 상속되므로, 단위를 값 안에 중첩하면 단위 색까지 투명해져 버린다. */}
      <span className="flex items-baseline">
        <span className={valueClass}>{value}</span>
        {unit && <span className="ml-1 text-[13px] font-medium text-ink-mute">{unit}</span>}
      </span>
      {caption && <span className="text-[11px] text-ink-faint">{caption}</span>}
    </div>
  );
}
