// 배경 글로우 풀스크린 셸 — 인증 인접 풀스크린 페이지(Login/Onboarding) 전용.
// 앱 셸(Layout)과 같은 앰비언트 글로우를 깔고, 그 위에 카드 뒤 중앙 광원을 하나 더 얹어
// 시선을 화면 한가운데로 모은다. 데이터가 밀집된 일반 페이지에는 쓰지 않는다.
// 셸에 `h-screen overflow-hidden`을 걸면 뷰포트보다 긴 콘텐츠가 잘리고 스크롤도 막힌다 —
// 온보딩 폼(입력 2개 + 버튼)에서 세로가 짧은 화면(가로 모드 폰, 브라우저 확대)이면 제출 버튼에
// 닿을 수 없고, 온보딩은 OnboardingCheckInterceptor가 강제하는 관문이라 앱 전체가 잠긴다.
// 그래서 `min-h-screen`으로 콘텐츠만큼 늘어나게 하고, clip은 셸이 아니라 광원 레이어에만 건다.
export default function GlowShell({ children, className = '' }) {
  return (
    <div
      className={`relative flex min-h-screen flex-col items-center justify-center overflow-x-hidden px-6 py-12 ${className}`}
    >
      <div aria-hidden="true" className="ambient-glow" />
      {/* 중앙 광원 — 코발트 코어 + 시안 헤일로 2겹.
          620px 블러 원이 셸 밖으로 삐져나가 스크롤바를 만들지 않도록 이 레이어에서 clip한다. */}
      <div aria-hidden="true" className="pointer-events-none absolute inset-0 overflow-hidden">
        <div className="absolute left-1/2 top-1/2 h-[620px] w-[620px] -translate-x-1/2 -translate-y-1/2 rounded-full bg-accent/16 blur-[120px]" />
        <div className="absolute left-1/2 top-1/2 h-[320px] w-[320px] -translate-x-1/2 -translate-y-1/2 rounded-full bg-accent-cyan/10 blur-[90px]" />
      </div>
      <div className="relative flex w-full flex-col items-center">{children}</div>
    </div>
  );
}
