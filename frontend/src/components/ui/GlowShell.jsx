// 배경 블러 글로우 풀스크린 셸 — 로그인 화면에서 확정한 "카드 뒤 은은한 블러 원" 장치.
// 인증 인접 풀스크린 페이지(Login/Onboarding) 전용 — 데이터가 밀집된 일반 페이지에는 쓰지 않는다.
export default function GlowShell({ children, className = '' }) {
  return (
    <div
      className={`relative flex h-screen flex-col items-center justify-center overflow-hidden bg-navy-app px-6 ${className}`}
    >
      <div
        aria-hidden="true"
        className="pointer-events-none absolute left-1/2 top-1/2 h-[560px] w-[560px] -translate-x-1/2 -translate-y-1/2 rounded-full bg-accent/18 blur-[110px]"
      />
      <div className="relative flex w-full flex-col items-center">{children}</div>
    </div>
  );
}
