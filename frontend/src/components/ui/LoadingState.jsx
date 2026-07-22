import { Loader2 } from 'lucide-react';

// 로딩 상태 — Dashboard/History/Welfare/Approvals에 흩어진 스피너 안내 통일
export default function LoadingState({ label = '불러오는 중…', className = '' }) {
  return (
    <div className={`flex items-center justify-center gap-2 py-12 text-ink-mute ${className}`}>
      <Loader2 size={18} className="animate-spin" />
      <span className="text-[13px]">{label}</span>
    </div>
  );
}
