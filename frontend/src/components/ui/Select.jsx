// 드롭다운 선택 — children으로 <option> 그대로 전달 (WelfareApplyModal/LeaveApplyPanel 셀렉트 통일)
// 네이티브 옵션 목록은 OS가 그리므로 [color-scheme:dark]로 다크 팔레트를 강제한다.
export default function Select({ invalid = false, className = '', children, ...rest }) {
  return (
    <select
      className={`w-full rounded-btn border bg-white/[0.04] px-2.5 py-2.5 text-[14px] text-ink-hi outline-none transition-all focus:border-accent-cyan/60 focus:bg-white/[0.06] focus:ring-2 focus:ring-accent-cyan/15 disabled:cursor-not-allowed disabled:opacity-50 [color-scheme:dark] ${
        invalid ? 'border-danger/55' : 'border-white/[0.09]'
      } ${className}`}
      {...rest}
    >
      {children}
    </select>
  );
}
