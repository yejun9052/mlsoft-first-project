// 탭바 — ApprovalsPage/AdminMembersPage 각자 구현된 탭바(하단 2px accent 보더 + 선택적 카운트 배지) 통일.
// tabs: [{ value, label, count? }] — count가 없으면 배지는 렌더하지 않는다.
export default function Tabs({ tabs, value, onChange, className = '' }) {
  return (
    <div className={`flex items-center gap-1 border-b border-white/6 ${className}`}>
      {tabs.map((tab) => {
        const active = tab.value === value;
        return (
          <button
            key={tab.value}
            type="button"
            onClick={() => onChange(tab.value)}
            className={`relative px-4 py-2.5 text-[13px] font-semibold transition-colors ${
              active ? 'text-accent-light' : 'text-ink-mute hover:text-ink-body'
            }`}
          >
            {tab.label}
            {tab.count !== undefined && (
              <span
                className={`ml-1.5 rounded-full px-1.5 py-0.5 text-[11px] ${
                  active ? 'bg-accent/16 text-accent-light' : 'bg-white/6 text-ink-faint'
                }`}
              >
                {tab.count}
              </span>
            )}
            {active && <span className="absolute inset-x-0 -bottom-px h-0.5 rounded-full bg-accent" />}
          </button>
        );
      })}
    </div>
  );
}
