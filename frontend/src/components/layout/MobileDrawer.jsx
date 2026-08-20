import { useEffect, useRef } from 'react';
import { X } from 'lucide-react';
import IconButton from '../ui/IconButton.jsx';

const FOCUSABLE_SELECTOR = [
  'a[href]',
  'button:not([disabled])',
  'input:not([disabled])',
  'select:not([disabled])',
  'textarea:not([disabled])',
  '[tabindex]:not([tabindex="-1"])',
].join(',');

// Modal과 같은 이유로 누른 곳과 뗀 곳이 모두 backdrop일 때만 닫는다.
// 메뉴를 스크롤하다 손가락이 바깥으로 나간 경우를 바깥 탭으로 오인하면 탐색 중인 서랍이 사라진다.
export default function MobileDrawer({ title, onClose, returnFocusRef, children }) {
  const panelRef = useRef(null);
  const closeButtonRef = useRef(null);
  const pressedOnBackdrop = useRef(false);

  useEffect(() => {
    const previousOverflow = document.body.style.overflow;
    document.body.style.overflow = 'hidden';
    closeButtonRef.current?.focus();

    function handleKeyDown(event) {
      if (event.key === 'Escape') {
        event.preventDefault();
        onClose();
        return;
      }

      if (event.key !== 'Tab') return;

      const focusable = Array.from(panelRef.current?.querySelectorAll(FOCUSABLE_SELECTOR) ?? []);
      if (focusable.length === 0) {
        event.preventDefault();
        panelRef.current?.focus();
        return;
      }

      const first = focusable[0];
      const last = focusable[focusable.length - 1];

      if (event.shiftKey && document.activeElement === first) {
        event.preventDefault();
        last.focus();
      } else if (!event.shiftKey && document.activeElement === last) {
        event.preventDefault();
        first.focus();
      }
    }

    window.addEventListener('keydown', handleKeyDown);

    // 열릴 때의 노드를 붙잡아 둔다 — 정리 시점에 ref를 다시 읽으면 그때 가리키는 것이
    // 무엇인지 보장할 수 없다(리액트가 다시 그렸을 수 있다). 서랍을 여는 버튼은
    // 서랍이 열려 있는 동안 계속 붙어 있으므로 여기서 잡아 두는 편이 안전하다.
    const returnFocusTarget = returnFocusRef?.current;

    return () => {
      document.body.style.overflow = previousOverflow;
      window.removeEventListener('keydown', handleKeyDown);
      returnFocusTarget?.focus();
    };
  }, [onClose, returnFocusRef]);

  function handleBackdropPointerDown(event) {
    pressedOnBackdrop.current = event.target === event.currentTarget;
  }

  function handleBackdropClick(event) {
    if (event.target !== event.currentTarget) return;
    if (!pressedOnBackdrop.current) return;

    pressedOnBackdrop.current = false;
    onClose();
  }

  return (
    <div
      className="fixed inset-0 z-50 bg-navy-app/75 backdrop-blur-sm"
      onPointerDown={handleBackdropPointerDown}
      onClick={handleBackdropClick}
    >
      <section
        ref={panelRef}
        role="dialog"
        aria-modal="true"
        aria-label={title}
        tabIndex={-1}
        className="glass-strong flex h-[100dvh] w-[min(88vw,320px)] flex-col border-r border-white/[0.12] shadow-card"
      >
        <div className="safe-area-top flex min-h-14 shrink-0 items-center justify-between border-b border-white/[0.12] px-4">
          <span className="text-[15px] font-semibold text-ink-hi">{title}</span>
          <IconButton ref={closeButtonRef} Icon={X} label="메뉴 닫기" onClick={onClose} />
        </div>
        <div className="min-h-0 flex-1">{children}</div>
      </section>
    </div>
  );
}
