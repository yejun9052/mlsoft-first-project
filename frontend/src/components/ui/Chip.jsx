// 필터 칩 — HistoryPage Chip / AdminMembersPage FilterChip 통일. 활성 시 accent 틴트 pill.
export default function Chip({ active, onClick, children, className = '' }) {
  return (
    <button
      type="button"
      onClick={onClick}
      className={`rounded-badge px-3 py-1.5 text-[12px] font-semibold transition-colors ${
        active
          ? 'bg-accent/16 text-accent-light'
          : 'bg-navy-btn2 text-ink-mute hover:bg-white/8 hover:text-ink-body'
      } ${className}`}
    >
      {children}
    </button>
  );
}
