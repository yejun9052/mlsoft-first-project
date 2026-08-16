import { ChevronDown } from 'lucide-react';

// 표 안에서 값을 바로 고치는 셀렉트 — 구성원 관리의 부서·역할 열.
//
// 폼용 `Select`와 나눈 이유: 그쪽은 모달 세로 폼 기준이라 `w-full` · 14px · `py-2.5`로,
// 표에 넣으면 행 높이가 무너지고 열이 입력 폼처럼 보인다. 여기 목표는 반대다 —
// **배지가 있던 자리를 그대로 물려받는 것**. pill 모양·11px·틴트 배경·인셋 링까지
// `StatusBadge`와 같은 값을 쓰므로 표를 훑던 눈이 하던 대로 훑는다.
// 다른 점은 오른쪽의 셰브론 하나뿐이고, 그게 "이건 고칠 수 있는 값"이라는 유일한 신호다.
//
// `appearance-none`으로 OS 기본 화살표를 지운다 — 네이티브 화살표는 크기·색을 정할 수 없어
// 11px pill 안에서 혼자 커 보인다. 대신 lucide 셰브론을 얹고 select에 오른쪽 여백을 준다.
//
// 색을 **바깥 span**에 주고 select는 투명 + `text-inherit`으로 둔다. 셰브론이 select의 자식이
// 아니라 형제라서, 색이 select에 있으면 셰브론만 따로 물려받지 못한다.
//
// 옵션 목록(펼친 상태)의 색은 여기가 아니라 index.css의 전역 `option` 규칙이 담당한다.
// 목록은 OS 팝업이라 이 클래스들이 닿지 않는다 (Select.jsx 주석과 같은 이유).
const TONE_CLASS = {
  muted: 'bg-white/[0.06] text-ink-body ring-white/10 hover:bg-white/[0.1] hover:ring-white/20',
  accent:
    'bg-accent-cyan/12 text-accent-cyan ring-accent-cyan/25 hover:bg-accent-cyan/[0.18] hover:ring-accent-cyan/45',
};

export default function InlineSelect({
  value,
  onChange,
  label,
  tone = 'muted',
  disabled = false,
  className = '',
  children,
}) {
  return (
    <span
      className={`relative inline-flex items-center rounded-badge ring-1 ring-inset transition-all focus-within:ring-2 focus-within:ring-accent-cyan/60 ${
        TONE_CLASS[tone] ?? TONE_CLASS.muted
      } ${disabled ? 'opacity-50' : ''} ${className}`}
    >
      <select
        value={value}
        onChange={onChange}
        disabled={disabled}
        aria-label={label}
        title={label}
        className="appearance-none rounded-badge bg-transparent py-1 pl-2.5 pr-6 text-[11px] font-semibold text-inherit outline-none disabled:cursor-not-allowed [color-scheme:dark]"
      >
        {children}
      </select>
      <ChevronDown
        size={12}
        aria-hidden="true"
        className="pointer-events-none absolute right-1.5 opacity-70"
      />
    </span>
  );
}
