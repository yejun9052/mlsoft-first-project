import { Outlet } from 'react-router-dom';
import Sidebar from './Sidebar.jsx';

// 앱 셸 — 앰비언트 글로우 + 사이드바(236px 고정) + 콘텐츠(flex:1)
//
// 배경은 body의 bg-navy-app 하나로만 칠하고 셸/콘텐츠는 투명하게 둔다 — ambient-glow가
// z-index:-10 fixed 레이어라, 중간에 불투명 배경을 깔면 글로우가 가려지기 때문.
export default function Layout() {
  return (
    <div className="flex h-screen">
      <div aria-hidden="true" className="ambient-glow" />
      <Sidebar />
      <main className="flex-1 overflow-y-auto px-[30px] py-[26px]">
        <Outlet />
      </main>
    </div>
  );
}
