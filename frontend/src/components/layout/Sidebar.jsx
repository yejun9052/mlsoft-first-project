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
  Building2,
  Settings,
  ScrollText,
  LogOut,
} from 'lucide-react';
import { ROLE, ROLE_LABEL } from '../../constants/roles.js';
import { logout } from '../../api/auth.js';
import { useAllLeavesCount, useLeaveSummary, usePendingApprovals } from '../../hooks/useLeaves.js';
import Avatar from '../ui/Avatar.jsx';
import BrandMark from '../ui/BrandMark.jsx';

// MENU 섹션 (전 직원 공통 6개)
const MENU_ITEMS = [
  { to: '/dashboard', label: '대시보드', Icon: LayoutDashboard },
  { to: '/calendar', label: '팀 캘린더', Icon: CalendarDays },
  { to: '/history', label: '사용 내역', Icon: History },
  { to: '/welfare', label: '복리후생', Icon: Gift },
  { to: '/team', label: '팀 정보', Icon: Users },
  { to: '/myinfo', label: '내 정보', Icon: UserRound },
];

// 관리자 섹션 (역할별 노출 5개) — 결재 → 조직(구성원·부서) → 정책 → 로그 순
const ADMIN_ITEMS = [
  {
    to: '/approvals',
    label: '결재 관리',
    Icon: ClipboardCheck,
    roles: [ROLE.TEAM_LEADER, ROLE.SYSTEM_ADMIN],
  },
  { to: '/admin', label: '구성원 관리', Icon: UsersRound, roles: [ROLE.SYSTEM_ADMIN] },
  { to: '/admin/departments', label: '부서 관리', Icon: Building2, roles: [ROLE.SYSTEM_ADMIN] },
  { to: '/admin/policy', label: '연차 정책', Icon: Settings, roles: [ROLE.SYSTEM_ADMIN] },
  {
    to: '/admin/history',
    label: '처리 이력',
    Icon: ScrollText,
    roles: [ROLE.TEAM_LEADER, ROLE.SYSTEM_ADMIN],
  },
];

// 섹션 라벨 — 자간을 넓혀 메뉴 항목과 위계를 벌린다
const SECTION_LABEL_CLASS =
  'px-3 pb-2 text-[10px] font-semibold uppercase tracking-[0.18em] text-ink-faint';

// 사이드바 메뉴 한 줄
// 활성: 코발트 틴트 + 시안 텍스트 + 좌측 그라데이션 바(기존 점 대신 — 세로 바가 스캔하기 쉽다)
function SidebarLink({ to, label, Icon, badge }) {
  return (
    <NavLink
      to={to}
      end={to === '/admin'}
      className={({ isActive }) =>
        `relative flex items-center gap-2.5 rounded-btn px-3 py-2.5 text-[13px] font-medium transition-all ${
          isActive
            ? 'bg-accent/12 text-accent-light'
            : 'text-ink-mute hover:bg-white/[0.045] hover:text-ink-body'
        }`
      }
    >
      {({ isActive }) => (
        <>
          {/* 활성 표시 — 좌측 세로 그라데이션 바 */}
          {isActive && (
            <span className="absolute left-0 top-1/2 h-5 w-[3px] -translate-y-1/2 rounded-full bg-accent-cyan" />
          )}
          <Icon size={16} className={isActive ? 'text-accent-cyan' : undefined} />
          <span className="flex-1">{label}</span>
          {/* 대기 중인 결재 건수 — 0이면 숨김 */}
          {Boolean(badge) && (
            <span className="rounded-badge bg-accent-cyan/15 px-1.5 py-0.5 text-[11px] font-semibold text-accent-cyan tabular-nums">
              {badge}
            </span>
          )}
        </>
      )}
    </NavLink>
  );
}

// 내 연차 요약 패널 — 메뉴 아래가 비어 보여서 넣은 사용자 컨텍스트 블록.
// useLeaveSummary는 대시보드와 쿼리 키가 같아 캐시를 공유한다 — 페이지를 옮겨도 추가 요청이 없다.
// 요약이 아직 없거나(로딩) 온보딩 직후 null이면 렌더하지 않는다 — 빈 게이지가 더 어수선하다.
function LeaveSummaryPanel() {
  const { data: summary } = useLeaveSummary();
  if (!summary) return null;

  // 대시보드 게이지와 같은 계산식 (DashboardPage:75-78) — 대기분은 이미 useDays에 선차감돼 있다
  const totalDays = Number(summary.baseDays) + Number(summary.bonusDays);
  const confirmedUsed = Number(summary.useDays) - Number(summary.pendingDays);
  const usedPct = totalDays > 0 ? (confirmedUsed / totalDays) * 100 : 0;
  const pendingPct = totalDays > 0 ? (Number(summary.pendingDays) / totalDays) * 100 : 0;

  return (
    <div className="rounded-card border border-white/[0.12] bg-white/[0.03] p-3.5">
      <div className="flex items-baseline justify-between">
        <span className="text-[11px] font-semibold text-ink-faint">잔여 연차</span>
        <span className="text-[21px] font-extrabold text-accent-cyan tabular-nums">
          {Number(summary.remainingDays)}
          <span className="ml-0.5 text-[12px] font-semibold text-ink-mute">일</span>
        </span>
      </div>

      {/* 소진 게이지 — 사용(파랑) / 대기 선차감(주황) / 잔여(빈 트랙) */}
      <div className="mt-2.5 flex h-1.5 overflow-hidden rounded-full bg-navy-app/70">
        <div className="bg-accent" style={{ width: `${usedPct}%` }} />
        <div className="bg-warn/80" style={{ width: `${pendingPct}%` }} />
      </div>

      <div className="mt-2 flex items-center justify-between text-[11px] text-ink-faint tabular-nums">
        <span>사용 {confirmedUsed}일</span>
        {Number(summary.pendingDays) > 0 && <span className="text-warn">대기 {Number(summary.pendingDays)}일</span>}
      </div>
    </div>
  );
}

// 사이드바 — 236px 고정, 로고 → MENU → 관리자 → 연차 요약 → 하단 유저 카드 (docs/05 ①)
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

  // 결재 관리 배지 — 팀장은 "내가 승인자인 대기"(개인 스코프), 관리자는 회사 전체 대기(신규+취소)
  // 합계를 보여준다. 다른 role은 enabled=false라 두 쿼리 다 아예 호출되지 않는다.
  const isTeamLeader = role === ROLE.TEAM_LEADER;
  const isAdmin = role === ROLE.SYSTEM_ADMIN;
  const myPendingQuery = usePendingApprovals({ size: 1, enabled: isTeamLeader });
  const allPendingCountQuery = useAllLeavesCount('PENDING', isAdmin);
  const allCancelPendingCountQuery = useAllLeavesCount('CANCEL_PENDING', isAdmin);
  const approvalsBadge = isAdmin
    ? (allPendingCountQuery.data ?? 0) + (allCancelPendingCountQuery.data ?? 0)
    : (myPendingQuery.data?.page?.totalElements ?? 0);

  // 로그아웃 — 서버 쿠키 만료 후 로컬 정보 정리, 실패해도 로컬은 항상 정리하고 로그인으로
  async function handleLogout() {
    try {
      await logout();
    } catch {
      // 서버 오류여도 클라이언트 세션은 종료
    } finally {
      localStorage.removeItem('userInfo');
      navigate('/login', { replace: true });
    }
  }

  return (
    <aside className="glass-strong flex w-[236px] shrink-0 flex-col border-r border-white/[0.12] px-4 py-5">
      {/* 로고 — 그라데이션 모노그램 + 그라데이션 워드마크 */}
      <div className="flex items-center gap-2.5 px-2 pb-7">
        <BrandMark size="sm" />
        {/* 워드마크는 단색 — 바로 옆 BrandMark가 이미 그라데이션이라 둘 다 쓰면 과했다 */}
        <span className="text-[17px] font-extrabold tracking-[-0.02em] text-ink-hi">연차ON</span>
      </div>

      {/* 메뉴 + 요약 영역 — 관리자는 메뉴가 11개라 낮은 화면에서 넘칠 수 있어 이 영역만 스크롤시킨다.
          유저 카드는 밖에 두어 항상 바닥에 붙어 있게 한다(로그아웃 버튼이 가려지면 안 됨). */}
      <div className="flex min-h-0 flex-1 flex-col overflow-y-auto">
        {/* MENU 섹션 */}
        <p className={SECTION_LABEL_CLASS}>Menu</p>
        <nav className="flex flex-col gap-1">
          {MENU_ITEMS.map((item) => (
            <SidebarLink key={item.to} {...item} />
          ))}
        </nav>

        {/* 관리자 섹션 (권한 있는 메뉴만 노출) */}
        {adminItems.length > 0 && (
          <>
            <p className={`${SECTION_LABEL_CLASS} pt-7`}>관리자</p>
            <nav className="flex flex-col gap-1">
              {adminItems.map((item) => (
                <SidebarLink
                  key={item.to}
                  {...item}
                  badge={item.to === '/approvals' ? approvalsBadge : undefined}
                />
              ))}
            </nav>
          </>
        )}

        {/* 내 연차 요약 — mt-auto로 유저 카드 바로 위에 붙여, 메뉴가 짧은 일반 직원 화면에서
            아래쪽 빈 공간을 채운다 */}
        <div className="mt-auto pt-7">
          <LeaveSummaryPanel />
        </div>
      </div>

      {/* 하단 유저 카드 — 글래스 서피스 + 상단 하이라이트 */}
      <div className="glass glass-edge mt-3 flex shrink-0 items-center gap-3 rounded-card border border-white/[0.12] p-3">
        <Avatar name={userInfo?.name} size="md" />
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
          className="shrink-0 rounded-btn p-1.5 text-ink-mute transition-colors hover:bg-danger/10 hover:text-danger"
        >
          <LogOut size={16} />
        </button>
      </div>
    </aside>
  );
}
