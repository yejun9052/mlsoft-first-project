// 여러 줄 텍스트 입력 — 신청 사유 등 (WelfareApplyModal/LeaveApplyPanel 인풋 클래스 통일)
export default function Textarea({ rows = 3, invalid = false, className = '', ...rest }) {
  return (
    <textarea
      rows={rows}
      className={`w-full resize-none rounded-btn border bg-white/[0.04] px-3 py-2.5 text-[14px] text-ink-hi outline-none transition-all placeholder:text-ink-dim focus:border-accent-cyan/60 focus:bg-white/[0.06] focus:ring-2 focus:ring-accent-cyan/15 disabled:cursor-not-allowed disabled:opacity-50 ${
        invalid ? 'border-danger/55' : 'border-white/[0.09]'
      } ${className}`}
      {...rest}
    />
  );
}
