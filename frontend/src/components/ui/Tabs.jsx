// 탭바 — ApprovalsPage/AdminMembersPage 각자 구현된 탭바 통일.
// 활성 탭 밑줄은 코발트→시안 그라데이션 바 + 발광으로 처리해 사이드바 활성 표시와 같은 언어를 쓴다.
// tabs: [{ value, label, count? }] — count가 없으면 배지는 렌더하지 않는다.
export default function Tabs({ tabs, value, onChange, className = '' }) {
  return (
    <div className={`flex items-center gap-1 border-b border-white/[0.12] ${className}`}>
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
                className={`ml-1.5 rounded-full px-1.5 py-0.5 text-[11px] tabular-nums ${
                  active ? 'bg-accent-cyan/14 text-accent-cyan' : 'bg-white/[0.06] text-ink-faint'
                }`}
              >
                {tab.count}
              </span>
            )}
            {active && (
              <span className="absolute inset-x-0 -bottom-px h-0.5 rounded-full bg-accent-cyan shadow-glow" />
            )}
          </button>
        );
      })}
    </div>
  );
}
