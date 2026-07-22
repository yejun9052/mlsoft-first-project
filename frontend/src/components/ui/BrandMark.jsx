// "연" 모노그램 브랜드 마크 — Sidebar(32px)/Login(56px)/Onboarding(48px)가 공유하는 로고 조각.
const SIZE_CLASS = {
  sm: 'h-8 w-8 rounded-btn text-sm',
  md: 'h-12 w-12 rounded-card text-[18px]',
  lg: 'h-14 w-14 rounded-card text-[22px]',
};

export default function BrandMark({ size = 'md', className = '' }) {
  return (
    <span
      className={`flex shrink-0 items-center justify-center bg-accent font-extrabold text-white shadow-btn ${
        SIZE_CLASS[size] ?? SIZE_CLASS.md
      } ${className}`}
    >
      연
    </span>
  );
}
