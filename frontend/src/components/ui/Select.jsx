// 드롭다운 선택 — children으로 <option> 그대로 전달 (WelfareApplyModal/LeaveApplyPanel 셀렉트 통일)
//
// 옵션 목록의 색은 여기가 아니라 index.css의 `option` 규칙이 담당한다.
// 목록은 OS 팝업이라 이 클래스들이 닿지 않는다 — [color-scheme:dark]는 패널 배경만 어둡게 할 뿐
// 글자색을 정해 주지 않아서, 그것만 믿었다가 선택지가 안 보였다(2026-08-15).
// 여기에 옵션 색을 다시 주려 하지 말 것.
export default function Select({ invalid = false, className = '', children, ...rest }) {
  return (
    <select
      className={`w-full rounded-btn border bg-white/[0.04] px-2.5 py-2.5 text-[14px] text-ink-hi outline-none transition-all focus:border-accent-cyan/60 focus:bg-white/[0.06] focus:ring-2 focus:ring-accent-cyan/15 disabled:cursor-not-allowed disabled:opacity-50 [color-scheme:dark] ${
        invalid ? 'border-danger/55' : 'border-white/[0.15]'
      } ${className}`}
      {...rest}
    >
      {children}
    </select>
  );
}
