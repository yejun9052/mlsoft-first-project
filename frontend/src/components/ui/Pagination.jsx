import { ChevronLeft, ChevronRight } from 'lucide-react';
import IconButton from './IconButton.jsx';

// 서버 사이드 페이지네이션 바 — 페이지를 넘길 때마다 그 페이지 분량만 서버에서 가져오는 목록용.
// page는 Spring Page와 동일하게 0-base로 주고받고, 표시만 1-base로 바꾼다.
// 총 1페이지 이하면 넘길 곳이 없으므로 렌더하지 않는다.
export default function Pagination({ page, totalPages, totalElements, onChange, className = '' }) {
  if (!totalPages || totalPages <= 1) return null;

  const isFirst = page <= 0;
  const isLast = page >= totalPages - 1;

  return (
    <div
      className={`flex items-center justify-between gap-3 border-t border-white/6 px-5 py-3 ${className}`}
    >
      <span className="text-[11px] text-ink-faint tabular-nums">
        {totalElements !== undefined && `총 ${totalElements}건`}
      </span>
      <div className="flex items-center gap-2">
        <IconButton
          Icon={ChevronLeft}
          label="이전 페이지"
          disabled={isFirst}
          onClick={() => onChange(page - 1)}
        />
        <span className="text-[12px] text-ink-mute tabular-nums">
          {page + 1} / {totalPages}
        </span>
        <IconButton
          Icon={ChevronRight}
          label="다음 페이지"
          disabled={isLast}
          onClick={() => onChange(page + 1)}
        />
      </div>
    </div>
  );
}
