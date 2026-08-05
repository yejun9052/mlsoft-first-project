import { Inbox } from 'lucide-react';

// 빈 상태 — Dashboard/History/Welfare/Approvals에 흩어진 "데이터 없음" 안내 통일.
// 아이콘을 글래스 원판에 넣어 허전한 영역에 최소한의 시각적 무게를 준다.
export default function EmptyState({ label = '데이터가 없습니다.', Icon = Inbox, className = '' }) {
  return (
    <div className={`flex flex-col items-center justify-center gap-3 py-12 text-center ${className}`}>
      <span className="flex h-12 w-12 items-center justify-center rounded-full border border-white/[0.07] bg-white/[0.03] text-ink-dim">
        <Icon size={22} />
      </span>
      <p className="text-[13px] text-ink-mute">{label}</p>
    </div>
  );
}
