import { Inbox } from 'lucide-react';

// 빈 상태 — Dashboard/History/Welfare/Approvals에 흩어진 "데이터 없음" 안내 통일
export default function EmptyState({ label = '데이터가 없습니다.', Icon = Inbox, className = '' }) {
  return (
    <div className={`flex flex-col items-center justify-center gap-2 py-12 text-center ${className}`}>
      <Icon size={28} className="text-ink-dim" />
      <p className="text-[13px] text-ink-mute">{label}</p>
    </div>
  );
}
