// 아이콘 전용 버튼 — 캘린더 월이동, 테이블 행 액션, 모달 닫기(X) 등에 공용으로 사용.
// label은 title/aria-label로 그대로 노출(툴팁 겸 접근성 라벨).
const TONE_CLASS = {
  muted: 'text-ink-mute hover:bg-white/6 hover:text-ink-body',
  danger: 'text-ink-mute hover:bg-danger/12 hover:text-danger',
  accent: 'text-ink-mute hover:bg-accent/12 hover:text-accent-light',
};

const SIZE_PADDING_CLASS = { sm: 'p-1', md: 'p-1.5', lg: 'p-2' };
const ICON_SIZE = { sm: 13, md: 15, lg: 17 };

export default function IconButton({
  Icon,
  label,
  size = 'md',
  tone = 'muted',
  disabled = false,
  className = '',
  ...rest
}) {
  return (
    <button
      type="button"
      title={label}
      aria-label={label}
      disabled={disabled}
      className={`rounded-btn transition-colors disabled:cursor-not-allowed disabled:opacity-50 ${
        SIZE_PADDING_CLASS[size]
      } ${TONE_CLASS[tone] ?? TONE_CLASS.muted} ${className}`}
      {...rest}
    >
      <Icon size={ICON_SIZE[size]} />
    </button>
  );
}
