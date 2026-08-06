// 필터 칩 — HistoryPage Chip / AdminMembersPage FilterChip 통일.
// 활성 시 시안 틴트 + 링 pill, 비활성은 글래스 표면.
export default function Chip({ active, onClick, children, className = '' }) {
  return (
    <button
      type="button"
      onClick={onClick}
      className={`rounded-badge px-3 py-1.5 text-[12px] font-semibold transition-all duration-150 ${
        active
          ? 'bg-accent-cyan/14 text-accent-cyan ring-1 ring-inset ring-accent-cyan/35'
          : 'border border-white/[0.12] bg-white/[0.03] text-ink-mute hover:border-white/[0.18] hover:bg-white/[0.07] hover:text-ink-body'
      } ${className}`}
    >
      {children}
    </button>
  );
}
