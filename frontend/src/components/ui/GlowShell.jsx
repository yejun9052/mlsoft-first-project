// 배경 글로우 풀스크린 셸 — 인증 인접 풀스크린 페이지(Login/Onboarding) 전용.
// 앱 셸(Layout)과 같은 앰비언트 글로우를 깔고, 그 위에 카드 뒤 중앙 광원을 하나 더 얹어
// 시선을 화면 한가운데로 모은다. 데이터가 밀집된 일반 페이지에는 쓰지 않는다.
export default function GlowShell({ children, className = '' }) {
  return (
    <div
      className={`relative flex h-screen flex-col items-center justify-center overflow-hidden px-6 ${className}`}
    >
      <div aria-hidden="true" className="ambient-glow" />
      {/* 중앙 광원 — 코발트 코어 + 시안 헤일로 2겹 */}
      <div
        aria-hidden="true"
        className="pointer-events-none absolute left-1/2 top-1/2 h-[620px] w-[620px] -translate-x-1/2 -translate-y-1/2 rounded-full bg-accent/16 blur-[120px]"
      />
      <div
        aria-hidden="true"
        className="pointer-events-none absolute left-1/2 top-1/2 h-[320px] w-[320px] -translate-x-1/2 -translate-y-1/2 rounded-full bg-accent-cyan/10 blur-[90px]"
      />
      <div className="relative flex w-full flex-col items-center">{children}</div>
    </div>
  );
}
