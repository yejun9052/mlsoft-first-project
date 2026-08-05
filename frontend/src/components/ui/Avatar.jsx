// 이니셜 아바타 — 원형, 이름 첫 글자 (Approvals/Team/AdminMembers/MyInfo/Sidebar 5중복 제거)
// 글래스 톤에 맞춰 단색 대신 코발트 틴트 + 얇은 링 — 아바타가 여러 개 나열될 때 서로 분리돼 보인다.
const SIZE_CLASS = {
  sm: 'h-8 w-8 text-[12px]',
  md: 'h-9 w-9 text-[13px]',
  lg: 'h-16 w-16 text-[26px]',
};

export default function Avatar({ name, size = 'md', className = '' }) {
  return (
    <span
      className={`flex shrink-0 items-center justify-center rounded-full bg-navy-avatar/70 font-semibold text-accent-label ring-1 ring-inset ring-accent-cyan/20 ${
        SIZE_CLASS[size] ?? SIZE_CLASS.md
      } ${className}`}
    >
      {name ? name.charAt(0) : '?'}
    </span>
  );
}
