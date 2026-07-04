import { Navigate } from 'react-router-dom';

// 라우트 가드 — 렌더 전에 선차단 (렌더 후 리다이렉트 금지, docs/04)
// - 로그인 정보(localStorage userInfo) 없으면 /login
// - roles 배열이 주어지면 해당 역할만 통과, 아니면 /dashboard
export default function RequireAuth({ roles, children }) {
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

  // 권한 미달 → 대시보드로 차단
  if (roles && !roles.includes(userInfo.role)) {
    return <Navigate to="/dashboard" replace />;
  }

  return children;
}
