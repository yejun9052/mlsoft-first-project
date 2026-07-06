// 페이지 헤더 — 타이틀(700/22px) + 서브텍스트 + 우측 액션 슬롯 (docs/05 공통)
export default function PageHeader({ title, subtitle, children }) {
  return (
    <div className="mb-6 flex items-start justify-between gap-4">
      <div>
        <h1 className="text-[22px] font-bold tracking-[-0.02em] text-ink-hi">{title}</h1>
        {subtitle && <p className="mt-1 text-[13px] text-ink-mute">{subtitle}</p>}
      </div>
      {children && <div className="flex shrink-0 items-center gap-2">{children}</div>}
    </div>
  );
}
