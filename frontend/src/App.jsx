import { Routes, Route, Navigate } from 'react-router-dom';
import RequireAuth from './components/RequireAuth.jsx';
import Layout from './components/layout/Layout.jsx';
import { ROLE } from './constants/roles.js';
import LoginPage from './pages/LoginPage.jsx';
import OAuthCallbackPage from './pages/OAuthCallbackPage.jsx';
import OnboardingPage from './pages/OnboardingPage.jsx';
import DashboardPage from './pages/DashboardPage.jsx';
import CalendarPage from './pages/CalendarPage.jsx';
import HistoryPage from './pages/HistoryPage.jsx';
import WelfarePage from './pages/WelfarePage.jsx';
import TeamPage from './pages/TeamPage.jsx';
import MyInfoPage from './pages/MyInfoPage.jsx';
import ApprovalsPage from './pages/ApprovalsPage.jsx';
import AdminMembersPage from './pages/AdminMembersPage.jsx';
import AdminPolicyPage from './pages/AdminPolicyPage.jsx';

// 결재 관리 접근 가능 역할 (팀장·총관리자)
const APPROVER_ROLES = [ROLE.TEAM_LEADER, ROLE.SYSTEM_ADMIN];
// 관리자 전용 역할
const ADMIN_ROLES = [ROLE.SYSTEM_ADMIN];

export default function App() {
  return (
    <Routes>
      {/* 인증 불필요 */}
      <Route path="/login" element={<LoginPage />} />
      <Route path="/oauth-callback" element={<OAuthCallbackPage />} />

      {/* 인증 필요 — 레이아웃 없는 단독 페이지 */}
      <Route
        path="/onboarding"
        element={
          <RequireAuth>
            <OnboardingPage />
          </RequireAuth>
        }
      />

      {/* 인증 필요 — 사이드바 레이아웃 */}
      <Route
        element={
          <RequireAuth>
            <Layout />
          </RequireAuth>
        }
      >
        <Route path="/dashboard" element={<DashboardPage />} />
        <Route path="/calendar" element={<CalendarPage />} />
        <Route path="/history" element={<HistoryPage />} />
        <Route path="/welfare" element={<WelfarePage />} />
        <Route path="/team" element={<TeamPage />} />
        <Route path="/myinfo" element={<MyInfoPage />} />
      </Route>

      {/* 결재 관리 — 팀장·총관리자만 */}
      <Route
        element={
          <RequireAuth roles={APPROVER_ROLES}>
            <Layout />
          </RequireAuth>
        }
      >
        <Route path="/approvals" element={<ApprovalsPage />} />
      </Route>

      {/* 관리자 — 총관리자만 */}
      <Route
        element={
          <RequireAuth roles={ADMIN_ROLES}>
            <Layout />
          </RequireAuth>
        }
      >
        <Route path="/admin" element={<AdminMembersPage />} />
        <Route path="/admin/policy" element={<AdminPolicyPage />} />
      </Route>

      {/* 기본 진입·미정의 경로는 대시보드로 */}
      <Route path="/" element={<Navigate to="/dashboard" replace />} />
      <Route path="*" element={<Navigate to="/dashboard" replace />} />
    </Routes>
  );
}
