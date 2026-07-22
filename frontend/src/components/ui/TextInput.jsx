// 텍스트/날짜 입력 공통 스타일 (MyInfo·Onboarding의 로컬 TEXT_INPUT_CLASS/DATE_INPUT_CLASS 통일).
// type="date"는 [color-scheme:dark]로 다크 테마 캘린더 아이콘까지 함께 반전.
export default function TextInput({ invalid = false, className = '', type = 'text', ...rest }) {
  return (
    <input
      type={type}
      className={`w-full rounded-btn border bg-navy-btn2 px-3.5 py-2.5 text-[14px] text-ink-hi outline-none transition-colors placeholder:text-ink-dim focus:border-accent disabled:cursor-not-allowed disabled:opacity-50 [color-scheme:dark] ${
        invalid ? 'border-danger/50' : 'border-white/8'
      } ${className}`}
      {...rest}
    />
  );
}
