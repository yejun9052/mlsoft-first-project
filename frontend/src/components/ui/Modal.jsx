import { useEffect } from 'react';
import { X } from 'lucide-react';
import IconButton from './IconButton.jsx';

// 중앙 오버레이 모달 — backdrop 클릭·Esc로 닫기 + 헤더(title+닫기)/본문/푸터 슬롯.
// WelfareApplyModal의 backdrop·Esc·클릭아웃 로직을 재사용 가능하게 컴포넌트화한 것
// (캘린더의 드래그 가능 플로팅 패널과는 의도적으로 다른 패턴이라 그대로 유지, 통일하지 않음).
export default function Modal({ title, onClose, children, footer, maxWidth = 440, className = '' }) {
  // Esc로 닫기
  useEffect(() => {
    function onKeyDown(e) {
      if (e.key === 'Escape') onClose();
    }
    window.addEventListener('keydown', onKeyDown);
    return () => window.removeEventListener('keydown', onKeyDown);
  }, [onClose]);

  return (
    <div
      role="dialog"
      aria-label={title}
      className="fixed inset-0 z-50 flex items-center justify-center bg-black/60 p-4"
      onClick={onClose}
    >
      <div
        className={`flex max-h-[90vh] w-full flex-col overflow-hidden rounded-card border border-white/6 bg-navy-card shadow-card ${className}`}
        style={{ maxWidth }}
        onClick={(e) => e.stopPropagation()}
      >
        {title && (
          <div className="flex items-center justify-between border-b border-white/6 px-5 py-4">
            <h2 className="text-[16px] font-semibold text-ink-hi">{title}</h2>
            <IconButton Icon={X} label="닫기" onClick={onClose} />
          </div>
        )}
        <div className="flex-1 overflow-y-auto px-5 py-4">{children}</div>
        {footer && (
          <div className="flex items-center justify-end gap-2 border-t border-white/6 px-5 py-4">{footer}</div>
        )}
      </div>
    </div>
  );
}
