import { useRef, useState } from 'react';
import { Menu } from 'lucide-react';
import { Outlet } from 'react-router-dom';
import useMediaQuery from '../../hooks/useMediaQuery.js';
import IconButton from '../ui/IconButton.jsx';
import MobileDrawer from './MobileDrawer.jsx';
import Sidebar from './Sidebar.jsx';

// 앱 셸 — 앰비언트 글로우 + 사이드바(236px 고정) + 콘텐츠(flex:1)
//
// 배경은 body의 bg-navy-app 하나로만 칠하고 셸/콘텐츠는 투명하게 둔다 — ambient-glow가
// z-index:-10 fixed 레이어라, 중간에 불투명 배경을 깔면 글로우가 가려지기 때문.
export default function Layout() {
  const isNarrow = useMediaQuery('(max-width: 1023px)');
  const [menuOpen, setMenuOpen] = useState(false);
  const menuButtonRef = useRef(null);

  function closeMenu() {
    setMenuOpen(false);
  }

  return (
    <div className={isNarrow ? 'flex min-h-[100dvh] flex-col' : 'flex h-screen'}>
      <div aria-hidden="true" className="ambient-glow" />

      {isNarrow ? (
        <>
          <header className="glass-strong safe-area-top sticky top-0 z-40 flex min-h-14 items-center border-b border-white/[0.12] px-4">
            <IconButton
              ref={menuButtonRef}
              Icon={Menu}
              label="메뉴 열기"
              onClick={() => setMenuOpen(true)}
            />
            <span className="ml-3 text-[15px] font-extrabold tracking-[-0.02em] text-ink-hi">
              연차ON
            </span>
          </header>

          {menuOpen && (
            <MobileDrawer title="전체 메뉴" onClose={closeMenu} returnFocusRef={menuButtonRef}>
              <Sidebar className="w-full border-r-0" onNavigate={closeMenu} />
            </MobileDrawer>
          )}

          <main className="safe-area-bottom flex-1 overflow-y-auto px-4 py-5">
            <Outlet />
          </main>
        </>
      ) : (
        <>
          <Sidebar />
          <main className="flex-1 overflow-y-auto px-[30px] py-[26px]">
            <Outlet />
          </main>
        </>
      )}
    </div>
  );
}
