import { Loader2 } from 'lucide-react';

// 버튼 — variant(primary/secondary/danger/ghost/link) × size(sm/md/lg) 조합 + 로딩 스핀 내장
// 8개 페이지에 복붙되던 버튼 3종(Primary 파랑 / Secondary 회색 / Danger 아웃라인) 통일.
const VARIANT_CLASS = {
  primary: 'bg-accent text-white shadow-btn hover:bg-accent-dark',
  secondary: 'bg-navy-btn2 text-ink-body hover:bg-white/8',
  danger: 'border border-danger/50 bg-transparent text-danger hover:bg-danger/10',
  ghost: 'bg-transparent text-ink-mute hover:bg-white/6 hover:text-ink-body',
  link: 'bg-transparent p-0 text-accent-light hover:text-accent-dark',
};

const SIZE_TEXT_CLASS = { sm: 'text-[12px]', md: 'text-[13px]', lg: 'text-[14px]' };
const SIZE_PADDING_CLASS = { sm: 'px-3 py-2', md: 'px-3.5 py-2.5', lg: 'px-4 py-3' };
const ICON_SIZE = { sm: 13, md: 15, lg: 16 };

// 로그인 Google 버튼에서 확정한 hover-lift 마이크로 인터랙션 — Card.jsx의 hover prop에도 동일한 값으로
// 내장(각자 독립된 leaf 컴포넌트라 한 줄짜리 상수를 공유 유틸로 빼는 대신 그대로 복제).
const LIFT_CLASS = 'transition-all hover:-translate-y-0.5 hover:shadow-md active:translate-y-0';

export default function Button({
  children,
  variant = 'primary',
  size = 'md',
  Icon,
  iconPosition = 'left',
  loading = false,
  lift = true,
  disabled = false,
  type = 'button',
  className = '',
  ...rest
}) {
  const isLink = variant === 'link';
  const isDisabled = disabled || loading;

  return (
    <button
      type={type}
      disabled={isDisabled}
      className={`inline-flex shrink-0 items-center justify-center gap-1.5 rounded-btn font-semibold transition-colors disabled:cursor-not-allowed disabled:opacity-60 ${
        SIZE_TEXT_CLASS[size]
      } ${isLink ? '' : SIZE_PADDING_CLASS[size]} ${VARIANT_CLASS[variant] ?? VARIANT_CLASS.primary} ${
        lift && !isLink ? LIFT_CLASS : ''
      } ${className}`}
      {...rest}
    >
      {loading ? (
        <Loader2 size={ICON_SIZE[size]} className="animate-spin" />
      ) : (
        Icon && iconPosition === 'left' && <Icon size={ICON_SIZE[size]} />
      )}
      {children}
      {!loading && Icon && iconPosition === 'right' && <Icon size={ICON_SIZE[size]} />}
    </button>
  );
}
