// "연" 모노그램 브랜드 마크 — Sidebar(32px)/Login(56px)/Onboarding(48px)가 공유하는 로고 조각.
// 단색 accent 면 + 딥네이비 글자(5.31:1). 그라데이션이었던 것을 2026-08-15에 내렸다 —
// 근거는 index.css의 "그라데이션 면은 이제 하나도 없다" 블록에 있다.
// text-navy-app을 떼지 말 것: accent 면 위에서 흰 글자는 3.2:1까지 떨어진다.
const SIZE_CLASS = {
  sm: 'h-8 w-8 rounded-btn text-sm',
  md: 'h-12 w-12 rounded-card text-[18px]',
  lg: 'h-14 w-14 rounded-card text-[22px]',
};

export default function BrandMark({ size = 'md', className = '' }) {
  return (
    <span
      className={`flex shrink-0 items-center justify-center bg-accent font-extrabold text-navy-app shadow-btn ${
        SIZE_CLASS[size] ?? SIZE_CLASS.md
      } ${className}`}
    >
      연
    </span>
  );
}
