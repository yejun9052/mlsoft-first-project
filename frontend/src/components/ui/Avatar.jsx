// 이니셜 아바타 — 원형, 이름 첫 글자 (Approvals/Team/AdminMembers/MyInfo/Sidebar 5중복 제거)
const SIZE_CLASS = {
  sm: 'h-8 w-8 text-[12px]',
  md: 'h-9 w-9 text-[13px]',
  lg: 'h-16 w-16 text-[26px]',
};

export default function Avatar({ name, size = 'md', className = '' }) {
  return (
    <span
      className={`flex shrink-0 items-center justify-center rounded-full bg-navy-avatar font-semibold text-accent-label ${
        SIZE_CLASS[size] ?? SIZE_CLASS.md
      } ${className}`}
    >
      {name ? name.charAt(0) : '?'}
    </span>
  );
}
