// 텍스트/날짜 입력 공통 스타일 (MyInfo·Onboarding의 로컬 TEXT_INPUT_CLASS/DATE_INPUT_CLASS 통일).
// 포커스 시 시안 보더 + 발광 링 — 글래스 표면에서 "지금 여기 입력 중"이 확실히 보이게 한다.
// type="date"는 [color-scheme:dark]로 다크 테마 캘린더 아이콘까지 함께 반전.
export default function TextInput({ invalid = false, className = '', type = 'text', ...rest }) {
  return (
    <input
      type={type}
      className={`w-full rounded-btn border bg-white/[0.04] px-3.5 py-2.5 text-[14px] text-ink-hi outline-none transition-all placeholder:text-ink-faint focus:border-accent-cyan/60 focus:bg-white/[0.06] focus:ring-2 focus:ring-accent-cyan/15 disabled:cursor-not-allowed disabled:opacity-50 [color-scheme:dark] ${
        invalid ? 'border-danger/55' : 'border-white/[0.15]'
      } ${className}`}
      {...rest}
    />
  );
}
