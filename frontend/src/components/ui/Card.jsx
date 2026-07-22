// 카드 서피스 — bg-navy-card + radius 16 + 얇은 보더 + card 섀도우 (docs/05 카드/서피스, 로그인 카드 톤 통일)
// title 을 주면 상단에 카드 타이틀(600/14px), 우측 액션 슬롯(right) 노출.

// 본문 패딩 프리셋 — default(일반) / none(테이블 전용, TableCard가 사용) / tight(캘린더 등 여백 최소화)
// scroll=true 인 경우 세로 패딩은 0으로(리스트 아이템 자체 패딩에 맡기고, 스크롤 영역 상하단에 이중 여백이
// 생기지 않게) — 기존 페이지들이 bodyClassName="... !py-0"으로 수동 처리하던 패턴을 그대로 옮긴 것.
const PADDING_CLASS = {
  default: { body: 'px-5 py-4', scroll: 'px-5 py-0' },
  none: { body: '', scroll: '' },
  tight: { body: 'p-3', scroll: 'px-3 py-0' },
};

// 로그인에서 확정한 hover-lift 마이크로 인터랙션 — Button.jsx에도 동일한 값으로 내장(각자 독립된 leaf
// 컴포넌트라 한 줄짜리 상수를 공유 유틸로 빼는 대신 그대로 복제).
const HOVER_LIFT_CLASS = 'cursor-pointer transition-all hover:-translate-y-0.5 hover:shadow-md active:translate-y-0';

export default function Card({
  title,
  right,
  className = '',
  padding = 'default',
  fill = false,
  scroll = false,
  hover = false,
  as: As = 'section',
  children,
  ...rest
}) {
  const paddingSet = PADDING_CLASS[padding] ?? PADDING_CLASS.default;
  // fill && !scroll(예: 캘린더 카드) — 본문 자체를 flex-col로 만들어 내부에서 flex-1 자식(날짜 그리드 등)이
  // 남은 세로 공간을 채울 수 있게 한다. fill && scroll(기존 Dashboard/History 패턴)은 그대로 유지.
  const bodyClass = scroll
    ? `min-h-0 flex-1 overflow-y-auto ${paddingSet.scroll}`
    : fill
      ? `flex min-h-0 flex-1 flex-col ${paddingSet.body}`
      : paddingSet.body;

  return (
    <As
      type={As === 'button' ? 'button' : undefined}
      className={`rounded-card border border-white/6 bg-navy-card text-left shadow-card ${
        fill ? 'flex min-h-0 flex-1 flex-col' : ''
      } ${hover ? HOVER_LIFT_CLASS : ''} ${className}`}
      {...rest}
    >
      {(title || right) && (
        <header className="flex items-center justify-between gap-3 border-b border-white/6 px-5 py-4">
          {title && <h2 className="text-[15px] font-semibold text-ink-hi">{title}</h2>}
          {right}
        </header>
      )}
      <div className={bodyClass}>{children}</div>
    </As>
  );
}
