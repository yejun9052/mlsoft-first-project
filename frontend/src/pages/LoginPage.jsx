import { useEffect } from 'react';
import { Navigate } from 'react-router-dom';
import toast from 'react-hot-toast';

// Google OAuth 진입 경로 (Spring Security 제공, vite proxy 경유)
const GOOGLE_LOGIN_URL = '/oauth2/authorization/google';

// 서버 리다이렉트 에러 코드 → 한글 안내 문구 (docs/01 §2-1)
const ERROR_MESSAGES = {
  unauthorized_domain: '허용되지 않은 도메인입니다. @mlsoft.com 계정으로 로그인해 주세요.',
  retired: '퇴직 처리된 계정입니다. 관리자에게 문의해 주세요.',
};

// Google "G" 로고 (공식 4색, 인라인 SVG — 외부 리소스 의존 없음)
function GoogleLogo() {
  return (
    <svg width="18" height="18" viewBox="0 0 48 48" aria-hidden="true">
      <path
        fill="#EA4335"
        d="M24 9.5c3.54 0 6.71 1.22 9.21 3.6l6.85-6.85C35.9 2.38 30.47 0 24 0 14.62 0 6.51 5.38 2.56 13.22l7.98 6.19C12.43 13.72 17.74 9.5 24 9.5z"
      />
      <path
        fill="#4285F4"
        d="M46.98 24.55c0-1.57-.15-3.09-.38-4.55H24v9.02h12.94c-.58 2.96-2.26 5.48-4.78 7.18l7.73 6c4.51-4.18 7.09-10.36 7.09-17.65z"
      />
      <path
        fill="#FBBC05"
        d="M10.53 28.59c-.48-1.45-.76-2.99-.76-4.59s.27-3.14.76-4.59l-7.98-6.19C.92 16.46 0 20.12 0 24c0 3.88.92 7.54 2.56 10.78l7.97-6.19z"
      />
      <path
        fill="#34A853"
        d="M24 48c6.48 0 11.93-2.13 15.89-5.81l-7.73-6c-2.15 1.45-4.92 2.3-8.16 2.3-6.26 0-11.57-4.22-13.47-9.91l-7.98 6.19C6.51 42.62 14.62 48 24 48z"
      />
    </svg>
  );
}

// 로그인 — 다크 네이비 풀스크린 브랜드 화면, Google OAuth 전용 (docs/01 §2-1)
export default function LoginPage() {
  // OAuth 실패 리다이렉트 쿼리(?error=...) 처리 → toast 후 주소창에서 쿼리 제거
  useEffect(() => {
    const params = new URLSearchParams(window.location.search);
    const error = params.get('error');
    if (!error) return;

    const message =
      ERROR_MESSAGES[error] || params.get('message') || '로그인에 실패했습니다. 다시 시도해 주세요.';
    toast.error(message);
    // 새로고침·StrictMode 재실행 시 중복 toast 방지를 위해 쿼리 제거
    window.history.replaceState(null, '', '/login');
  }, []);

  // 이미 로그인된 유저는 대시보드로 선차단 (온보딩 미완료면 RequireAuth가 재분기)
  if (localStorage.getItem('userInfo')) {
    return <Navigate to="/dashboard" replace />;
  }

  // Google 로그인 진입 — Spring OAuth2 엔드포인트로 전체 페이지 이동
  function handleGoogleLogin() {
    window.location.href = GOOGLE_LOGIN_URL;
  }

  return (
    <div className="relative flex h-screen flex-col items-center justify-center overflow-hidden bg-navy-app px-6">
      {/* 배경 글로우 — 카드 뒤 은은한 블러 원 (다크 SaaS 로그인 화면에서 자주 쓰는 깊이감 장치) */}
      <div
        aria-hidden="true"
        className="pointer-events-none absolute left-1/2 top-1/2 h-[560px] w-[560px] -translate-x-1/2 -translate-y-1/2 rounded-full bg-accent/18 blur-[110px]"
      />

      {/* 브랜드 — MLsoft 워드마크(eyebrow) + 사이드바와 동일한 '연' 모노그램 + 제품명 + 한 줄 소개 */}
      <div className="relative mb-9 flex flex-col items-center gap-3">
        <span className="text-[12px] font-bold uppercase tracking-[0.32em] text-ink-mute">MLsoft</span>
        <span className="flex h-14 w-14 items-center justify-center rounded-card bg-accent text-[22px] font-extrabold text-white shadow-btn">
          연
        </span>
        <h1 className="text-[30px] font-extrabold tracking-[-0.03em] text-ink-hi">연차ON</h1>
        <p className="max-w-[300px] text-center text-[13px] leading-relaxed text-ink-mute">
          연차·경조사 신청부터 결재까지 한 곳에서 —<br />MLsoft 임직원을 위한 연차 관리 시스템
        </p>
      </div>

      {/* 로그인 카드 */}
      <div className="relative w-full max-w-[380px] rounded-card border border-white/6 bg-navy-card p-8 shadow-card">
        <p className="mb-6 text-center text-[14px] font-semibold text-ink-hi">
          사내 계정으로 로그인하고 시작하세요
        </p>
        <button
          type="button"
          onClick={handleGoogleLogin}
          className="flex w-full items-center justify-center gap-3 rounded-btn bg-white px-4 py-3 text-[14px] font-semibold text-gray-800 shadow-sm transition-all hover:-translate-y-0.5 hover:shadow-md active:translate-y-0"
        >
          <GoogleLogo />
          Google 계정으로 로그인
        </button>
        <p className="mt-5 text-center text-[11px] leading-relaxed text-ink-faint">
          <span className="font-semibold text-accent-light">@mlsoft.com</span> 회사 Google Workspace 계정만 사용할 수 있습니다.
          <br />첫 로그인 시 자동으로 가입됩니다.
        </p>
      </div>

      <p className="relative mt-8 text-[11px] text-ink-dim">© {new Date().getFullYear()} MLsoft. All rights reserved.</p>
    </div>
  );
}
