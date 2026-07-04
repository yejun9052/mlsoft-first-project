import { NavLink, useNavigate } from 'react-router-dom';
import {
  LayoutDashboard,
  CalendarDays,
  History,
  Gift,
  Users,
  UserRound,
  ClipboardCheck,
  UsersRound,
  Settings,
  LogOut,
} from 'lucide-react';
import { ROLE, ROLE_LABEL } from '../../constants/roles.js';
import { logout } from '../../api/auth.js';

// MENU 섹션 (전 직원 공통 6개)
const MENU_ITEMS = [
  { to: '/dashboard', label: '대시보드', Icon: LayoutDashboard },
  { to: '/calendar', label: '팀 캘린더', Icon: CalendarDays },
  { to: '/history', label: '사용 내역', Icon: History },
  { to: '/welfare', label: '복리후생', Icon: Gift },
  { to: '/team', label: '팀 정보', Icon: Users },
  { to: '/myinfo', label: '내 정보', Icon: UserRound },
];

// 관리자 섹션 (역할별 노출 3개)
const ADMIN_ITEMS = [
  {
    to: '/approvals',
    label: '결재 관리',
    Icon: ClipboardCheck,
    roles: [ROLE.TEAM_LEADER, ROLE.SYSTEM_ADMIN],
  },
  { to: '/admin', label: '구성원 관리', Icon: UsersRound, roles: [ROLE.SYSTEM_ADMIN] },
  { to: '/admin/policy', label: '연차 정책', Icon: Settings, roles: [ROLE.SYSTEM_ADMIN] },
];

// 사이드바 메뉴 한 줄 (활성: 파랑 틴트 배경 + accent-light 텍스트 + 좌측 도트)
function SidebarLink({ to, label, Icon }) {
  return (
    <NavLink
      to={to}
      end={to === '/admin'}
      className={({ isActive }) =>
        `relative flex items-center gap-2.5 rounded-btn px-3 py-2.5 text-[13px] font-medium transition-colors ${
          isActive
            ? 'bg-accent/15 text-accent-light'
            : 'text-ink-mute hover:bg-white/5 hover:text-ink-body'
        }`
      }
    >
      {({ isActive }) => (
        <>
          {/* 활성 표시 도트 */}
          {isActive && (
            <span className="absolute left-0 h-1.5 w-1.5 rounded-full bg-accent" />
          )}
          <Icon size={16} />
          <span>{label}</span>
        </>
      )}
    </NavLink>
  );
}

// 사이드바 — 236px 고정, 로고 → MENU → 관리자 → 하단 유저 카드 (docs/05 ①)
export default function Sidebar() {
  const navigate = useNavigate();

  // 로그인 유저 정보 (RequireAuth 통과 후 렌더되므로 존재 전제, 방어적 파싱만)
  let userInfo = null;
  try {
    userInfo = JSON.parse(localStorage.getItem('userInfo'));
  } catch {
    userInfo = null;
  }
  const role = userInfo?.role;
  const adminItems = ADMIN_ITEMS.filter((item) => item.roles.includes(role));

  // 로그아웃 — 서버 쿠키 만료 후 로컬 정보 정리, 실패해도 로컬은 항상 정리하고 로그인으로
  async function handleLogout() {
    try {
      await logout();
    } catch {
      // 서버 오류여도 클라이언트 세션은 종료
    } finally {
      localStorage.clear();
      navigate('/login', { replace: true });
    }
  }

  return (
    <aside className="flex w-[236px] shrink-0 flex-col border-r border-white/6 bg-navy-app px-4 py-5">
      {/* 로고 */}
      <div className="flex items-center gap-2 px-2 pb-6">
        <span className="flex h-8 w-8 items-center justify-center rounded-btn bg-accent text-sm font-extrabold text-white">
          연
        </span>
        <span className="text-[17px] font-bold tracking-[-0.02em] text-ink-hi">연차ON</span>
      </div>

      {/* MENU 섹션 */}
      <p className="px-3 pb-2 text-[10px] font-semibold tracking-[0.08em] text-ink-dim">MENU</p>
      <nav className="flex flex-col gap-1">
        {MENU_ITEMS.map((item) => (
          <SidebarLink key={item.to} {...item} />
        ))}
      </nav>

      {/* 관리자 섹션 (권한 있는 메뉴만 노출) */}
      {adminItems.length > 0 && (
        <>
          <p className="px-3 pt-6 pb-2 text-[10px] font-semibold tracking-[0.08em] text-ink-dim">
            관리자
          </p>
          <nav className="flex flex-col gap-1">
            {adminItems.map((item) => (
              <SidebarLink key={item.to} {...item} />
            ))}
          </nav>
        </>
      )}

      {/* 하단 유저 카드 */}
      <div className="mt-auto flex items-center gap-3 rounded-card bg-navy-card p-3">
        <span className="flex h-9 w-9 items-center justify-center rounded-full bg-navy-avatar text-sm font-semibold text-accent-label">
          {userInfo?.name?.charAt(0) ?? '?'}
        </span>
        <div className="min-w-0 flex-1">
          <p className="truncate text-[13px] font-semibold text-ink-hi">
            {userInfo?.name ?? '이름 없음'}
          </p>
          <p className="truncate text-[11px] text-ink-mute">
            {userInfo?.departmentName ?? '부서 미배정'} · {ROLE_LABEL[role] ?? '-'}
          </p>
        </div>
        {/* 로그아웃 버튼 */}
        <button
          type="button"
          onClick={handleLogout}
          title="로그아웃"
          aria-label="로그아웃"
          className="shrink-0 rounded-btn p-1.5 text-ink-mute transition-colors hover:bg-white/5 hover:text-danger"
        >
          <LogOut size={16} />
        </button>
      </div>
    </aside>
  );
}
