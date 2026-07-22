// 범례 항목 — 색 스와치 + 라벨. 캘린더 범례, Stat의 swatch 변형과 같은 스와치 렌더링을 공유한다.
// swatch는 배경/보더 클래스 문자열을 그대로 받는다(예: "border-2 border-accent bg-accent/12").
// Icon을 주면 스와치 중앙에 작은 표시(체크 등)를 겹쳐 그린다 — "내 연차"(채워진 pill)와
// "신청 선택"(굵은 보더 + 체크)처럼 색만으로 구분이 미묘한 두 항목을 형태로도 구분하기 위함.
export default function Legend({ swatch, label, Icon, size = 'md', className = '' }) {
  const dim = size === 'sm' ? 'h-2.5 w-2.5' : 'h-3 w-3';
  return (
    <span className={`flex items-center gap-1.5 text-[12px] text-ink-mute ${className}`}>
      <span className={`relative flex ${dim} shrink-0 items-center justify-center rounded ${swatch}`}>
        {Icon && <Icon size={size === 'sm' ? 7 : 8} className="text-accent-light" strokeWidth={3} />}
      </span>
      {label}
    </span>
  );
}
