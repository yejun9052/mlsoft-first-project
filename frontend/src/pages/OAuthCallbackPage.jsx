import { useEffect } from 'react';
import { useNavigate } from 'react-router-dom';
import { Loader2 } from 'lucide-react';
import { me } from '../api/auth.js';

// OAuth 콜백 — JWT 쿠키 발급 후 진입, 내 정보 조회 → 온보딩 여부에 따라 분기 (docs/03 인증)
export default function OAuthCallbackPage() {
  const navigate = useNavigate();

  useEffect(() => {
    // StrictMode 이중 실행 대비 — 언마운트된 요청 결과는 무시
    let ignore = false;

    async function fetchUserInfo() {
      try {
        const data = await me();
        if (ignore) return;
        // 유저 정보 저장 후 온보딩 미완료면 온보딩, 완료면 대시보드로
        localStorage.setItem('userInfo', JSON.stringify(data));
        navigate(data.onboarded ? '/dashboard' : '/onboarding', { replace: true });
      } catch {
        // 조회 실패(쿠키 미발급 등) → 로그인 페이지로 복귀
        if (!ignore) navigate('/login', { replace: true });
      }
    }
    fetchUserInfo();

    return () => {
      ignore = true;
    };
  }, [navigate]);

  return (
    <div className="flex h-screen flex-col items-center justify-center gap-4 bg-navy-app">
      <Loader2 size={36} className="animate-spin text-accent" />
      <p className="text-[14px] font-medium text-ink-mute">로그인 처리 중입니다…</p>
    </div>
  );
}
