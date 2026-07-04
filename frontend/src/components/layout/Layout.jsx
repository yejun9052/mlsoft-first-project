import { Outlet } from 'react-router-dom';
import Sidebar from './Sidebar.jsx';

// 앱 셸 — 사이드바(236px 고정) + 콘텐츠(flex:1) 다크 네이비 레이아웃
export default function Layout() {
  return (
    <div className="flex h-screen bg-navy-app">
      <Sidebar />
      <main className="flex-1 overflow-y-auto bg-navy-content px-[30px] py-[26px]">
        <Outlet />
      </main>
    </div>
  );
}
