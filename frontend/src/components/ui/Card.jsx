// 카드 서피스 — bg-navy-card + radius 16 + card 섀도우 (docs/05 카드/서피스)
// title 을 주면 상단에 카드 타이틀(600/14px), 우측 액션 슬롯(right) 노출.
export default function Card({ title, right, className = '', bodyClassName = '', children }) {
  return (
    <section className={`rounded-card bg-navy-card shadow-card ${className}`}>
      {(title || right) && (
        <header className="flex items-center justify-between gap-3 border-b border-white/6 px-5 py-4">
          {title && <h2 className="text-[15px] font-semibold text-ink-hi">{title}</h2>}
          {right}
        </header>
      )}
      <div className={`px-5 py-4 ${bodyClassName}`}>{children}</div>
    </section>
  );
}
