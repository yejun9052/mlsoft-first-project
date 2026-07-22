// 필터 칩 그룹 — 라벨(선택) + Chip 여러 개. children으로 <Chip>(./Chip.jsx)을 그대로 넣어 조합한다
// (HistoryPage의 연도/종류/상태 필터 행 패턴).
export default function FilterGroup({ label, children, className = '' }) {
  return (
    <div className={`flex items-center gap-3 ${className}`}>
      {label && <span className="w-9 shrink-0 text-[12px] font-semibold text-ink-faint">{label}</span>}
      <div className="flex flex-wrap items-center gap-2">{children}</div>
    </div>
  );
}
