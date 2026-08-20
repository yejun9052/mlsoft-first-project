import { useEffect, useRef } from 'react';
import { X } from 'lucide-react';
import IconButton from './IconButton.jsx';

// 중앙 오버레이 모달 — backdrop 클릭·Esc로 닫기 + 헤더(title+닫기)/본문/푸터 슬롯.
// backdrop도 블러 처리해 뒤 화면이 유리 너머로 물러나 보이게 한다(글래스 톤의 깊이 장치).
// WelfareApplyModal의 backdrop·Esc·클릭아웃 로직을 재사용 가능하게 컴포넌트화한 것
// (캘린더의 드래그 가능 플로팅 패널과는 의도적으로 다른 패턴이라 그대로 유지, 통일하지 않음).
export default function Modal({ title, onClose, children, footer, maxWidth = 440, className = '' }) {
  // Esc로 닫기
  useEffect(() => {
    const previousOverflow = document.body.style.overflow;
    document.body.style.overflow = 'hidden';

    function onKeyDown(e) {
      if (e.key === 'Escape') onClose();
    }

    window.addEventListener('keydown', onKeyDown);
    return () => {
      document.body.style.overflow = previousOverflow;
      window.removeEventListener('keydown', onKeyDown);
    };
  }, [onClose]);

  // 눌림이 backdrop에서 시작했는지 — 아래 onClick 주석 참고
  const pressedOnBackdrop = useRef(false);

  /**
   * backdrop 클릭으로 닫기.
   *
   * <p><b>누른 곳과 뗀 곳이 모두 backdrop일 때만</b> 닫는다. `click`은 mousedown과 mouseup의
   * <b>공통 조상</b>에서 발생하므로, 모달 안에서 누르고 밖에서 떼면 그 공통 조상이 backdrop이 되어
   * "backdrop을 클릭했다"로 잡힌다. 안쪽 요소의 `stopPropagation`은 이때 아예 실행되지 않는다 —
   * 이벤트가 안쪽에서 시작한 게 아니기 때문이다.
   *
   * <p>입력창의 글자를 드래그로 선택하다 손이 모달 밖으로 나가면 그대로 창이 닫히면서
   * <b>입력하던 내용이 사라졌다</b> (2026-08-17 지적). 신청 사유·결재 의견처럼 길게 쓰는 칸이 많다.
   *
   * <p>`pointerdown`을 쓰는 이유는 마우스·터치·펜을 함께 덮기 위해서다.
   */
  function handleBackdropPointerDown(e) {
    pressedOnBackdrop.current = e.target === e.currentTarget;
  }

  function handleBackdropClick(e) {
    // 안쪽에서 올라온 클릭은 여기서 끊는다 (안쪽 stopPropagation을 대신한다 — 장치를 둘로 두지 않는다)
    if (e.target !== e.currentTarget) return;
    if (!pressedOnBackdrop.current) return;
    pressedOnBackdrop.current = false;
    onClose();
  }

  return (
    <div
      role="dialog"
      aria-label={title}
      className="fixed inset-0 z-50 flex items-center justify-center bg-navy-app/75 p-0 backdrop-blur-sm sm:p-4"
      onPointerDown={handleBackdropPointerDown}
      onClick={handleBackdropClick}
    >
      <div
        className={`glass-strong glass-edge flex h-[100dvh] max-h-none w-full flex-col overflow-hidden rounded-none border border-white/[0.15] shadow-card sm:h-auto sm:max-h-[90vh] sm:rounded-card ${className}`}
        style={{ maxWidth }}
      >
        {title && (
          <div className="flex items-center justify-between border-b border-white/[0.12] px-5 py-4">
            <h2 className="text-[16px] font-semibold tracking-[-0.01em] text-ink-hi">{title}</h2>
            <IconButton Icon={X} label="닫기" onClick={onClose} />
          </div>
        )}
        <div className="flex-1 overflow-y-auto px-5 py-4">{children}</div>
        {footer && (
          <div className="safe-area-bottom flex items-center justify-end gap-2 border-t border-white/[0.12] px-5 py-4">
            {footer}
          </div>
        )}
      </div>
    </div>
  );
}
