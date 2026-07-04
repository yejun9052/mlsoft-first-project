import { Navigate, useLocation } from 'react-router-dom';

// 온보딩 경로 (온보딩 미완료 리다이렉트의 예외 지점)
const ONBOARDING_PATH = '/onboarding';

// 라우트 가드 — 렌더 전에 선차단 (렌더 후 리다이렉트 금지, docs/04)
// - 로그인 정보(localStorage userInfo) 없으면 /login
// - 온보딩 미완료(onboarded=false)면 /onboarding으로 강제 (온보딩 페이지 자체는 예외)
// - 온보딩 완료 유저의 /onboarding 재진입은 /dashboard로 차단
// - roles 배열이 주어지면 해당 역할만 통과, 아니면 /dashboard
export default function RequireAuth({ roles, children }) {
  const { pathname } = useLocation();
  const stored = localStorage.getItem('userInfo');

  // 미로그인 → 로그인 페이지로
  if (!stored) {
    return <Navigate to="/login" replace />;
  }

  // 저장값이 손상된 경우 정리 후 로그인 페이지로
  let userInfo = null;
  try {
    userInfo = JSON.parse(stored);
  } catch {
    localStorage.removeItem('userInfo');
    return <Navigate to="/login" replace />;
  }

  const isOnboardingRoute = pathname === ONBOARDING_PATH;

  // 온보딩 미완료 → 온보딩 페이지 외 접근 차단 (검증 Y-2)
  if (!userInfo.onboarded && !isOnboardingRoute) {
    return <Navigate to={ONBOARDING_PATH} replace />;
  }

  // 온보딩 완료 유저가 온보딩 페이지 재진입 → 대시보드로
  if (userInfo.onboarded && isOnboardingRoute) {
    return <Navigate to="/dashboard" replace />;
  }

  // 권한 미달 → 대시보드로 차단
  if (roles && !roles.includes(userInfo.role)) {
    return <Navigate to="/dashboard" replace />;
  }

  return children;
}
