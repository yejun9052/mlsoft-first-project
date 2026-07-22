// 드롭다운 선택 — children으로 <option> 그대로 전달 (WelfareApplyModal/LeaveApplyPanel 셀렉트 통일)
export default function Select({ invalid = false, className = '', children, ...rest }) {
  return (
    <select
      className={`w-full rounded-btn border bg-navy-btn2 px-2.5 py-2.5 text-[14px] text-ink-hi outline-none transition-colors focus:border-accent disabled:cursor-not-allowed disabled:opacity-50 ${
        invalid ? 'border-danger/50' : 'border-white/8'
      } ${className}`}
      {...rest}
    >
      {children}
    </select>
  );
}
