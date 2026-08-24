// 페이지 헤더 — eyebrow(선택) + 타이틀(700/24px) + 서브텍스트 + 우측 액션 슬롯 (docs/05 공통)
// eyebrow는 로그인 화면의 "MLsoft" 워드마크와 동일한 스타일(12px/굵게/자간0.32em) — 인증 인접 페이지 등
// 브랜드 표기가 필요한 곳에서만 선택적으로 사용.
export default function PageHeader({ eyebrow, title, subtitle, children, className = '' }) {
  return (
    <div className={`mb-6 flex items-start justify-between gap-4 ${className}`}>
      <div>
        {eyebrow && (
          <p className="mb-1.5 text-[12px] font-bold uppercase tracking-[0.32em] text-accent-light">
            {eyebrow}
          </p>
        )}
        <h1 className="text-[24px] font-bold tracking-[-0.03em] text-ink-hi">{title}</h1>
        {subtitle && <p className="mt-1 text-[13px] text-ink-mute">{subtitle}</p>}
      </div>
      {children && <div className="flex shrink-0 items-center gap-2">{children}</div>}
    </div>
  );
}
