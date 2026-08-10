import { Navigate, useLocation } from 'react-router-dom';
import { readStoredUserInfo, useCurrentUser } from '../hooks/useAuth.js';

// 온보딩 경로 (온보딩 미완료 리다이렉트의 예외 지점)
const ONBOARDING_PATH = '/onboarding';

// 라우트 가드 — 렌더 전에 선차단 (렌더 후 리다이렉트 금지, docs/04)
// - 로그인 정보 없으면 /login
// - 온보딩 미완료(onboarded=false)면 /onboarding으로 강제 (온보딩 페이지 자체는 예외)
// - 온보딩 완료 유저의 /onboarding 재진입은 /dashboard로 차단
// - roles 배열이 주어지면 해당 역할만 통과, 아니면 /dashboard
//
// 권한 판정은 **서버 응답 기준**이다 (리뷰 F-7). useCurrentUser가 localStorage를 initialData로
// 쓰므로 첫 렌더에도 값이 있어 깜박임이 없고, 백그라운드 재검증이 끝나면 강등·승격이
// 재로그인 없이 이 가드에 반영된다. 저장값은 "아직 응답이 없을 때"의 부트스트랩일 뿐이다.
export default function RequireAuth({ roles, children }) {
  const { pathname } = useLocation();
  const { data: fetched, isPending, dataUpdatedAt } = useCurrentUser();
  const stored = readStoredUserInfo();
  const userInfo = fetched ?? stored;
  // 서버 응답으로 한 번이라도 확인됐는가. useCurrentUser가 initialDataUpdatedAt: 0을 주므로
  // 저장값만 있는 동안에는 0이고, 첫 재검증이 끝나면 그 시각이 들어온다.
  const serverConfirmed = dataUpdatedAt > 0;

  // 판단 근거가 아직 없다 — 저장값도 없고(또는 손상됐고) 응답도 오지 않았다.
  // 여기서 곧바로 /login으로 보내면 **쿠키는 살아 있는데 저장값만 없는 사용자가 튕긴다**
  // (사이트 데이터 일부 삭제, 다른 탭에서의 정리 등). 응답을 기다린 뒤 판정한다.
  // 쿠키가 죽었으면 /api/auth/me가 401을 받고 api 인터셉터가 로그인으로 보낸다.
  if (!userInfo) {
    return isPending ? null : <Navigate to="/login" replace />;
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

  // 권한 미달 → 대시보드로 차단.
  //
  // **아직 서버로 확인되지 않은 저장값으로는 거부하지 않는다.** 거부하면 이 가드가 언마운트돼
  // 재검증 결과가 도착해도 돌아올 경로가 없다 — 승격된 사용자가 /admin 링크로 들어오면
  // 낡은 저장값(EMPLOYEE) 때문에 대시보드로 튕기고, 목적지를 잃는다.
  // 확인 전에는 아무것도 렌더하지 않으므로 보호된 화면이 새지 않고, 실제 게이트는 서버다
  // (OnboardingCheckInterceptor + @PreAuthorize).
  if (roles && !roles.includes(userInfo.role)) {
    return serverConfirmed ? <Navigate to="/dashboard" replace /> : null;
  }

  return children;
}
