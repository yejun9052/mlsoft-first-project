// 여러 줄 텍스트 입력 — 신청 사유 등 (WelfareApplyModal/LeaveApplyPanel 인풋 클래스 통일)
export default function Textarea({ rows = 3, invalid = false, className = '', ...rest }) {
  return (
    <textarea
      rows={rows}
      className={`w-full resize-none rounded-btn border bg-navy-btn2 px-3 py-2.5 text-[14px] text-ink-hi outline-none transition-colors placeholder:text-ink-dim focus:border-accent disabled:cursor-not-allowed disabled:opacity-50 ${
        invalid ? 'border-danger/50' : 'border-white/8'
      } ${className}`}
      {...rest}
    />
  );
}
