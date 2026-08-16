// 아이콘 전용 버튼 — 캘린더 월이동, 테이블 행 액션, 모달 닫기(X) 등에 공용으로 사용.
// label은 title/aria-label로 그대로 노출(툴팁 겸 접근성 라벨).
//
// **쉬는 상태에도 테두리와 옅은 면을 준다** (2026-08-16). 예전에는 글리프만 떠 있고 hover에서야
// 배경이 생겨서, 마우스를 올려보기 전에는 버튼인지 그냥 아이콘인지 구분할 단서가 없었다.
// 커서(손 모양)는 index.css가 앱 전역으로 되살렸지만 그것도 올려봐야 알 수 있다 —
// 한눈에 보이는 단서는 테두리다.
//
// tone은 두 갈래다:
//   - 윤곽선 계열(muted/danger/accent) — 목록 행의 보조 동작. 여러 개가 나란히 놓여도 시끄럽지 않다
//   - 채워진 타일(confirm/deny) — 승인·반려처럼 **되돌리기 어려운 이지선다** 전용.
//     남발하면 채워진 타일이 흔해져서 "이건 무거운 결정"이라는 신호가 죽는다
const TONE_CLASS = {
  muted:
    'border-white/[0.12] bg-white/[0.04] text-ink-mute hover:border-white/[0.22] hover:bg-white/[0.09] hover:text-ink-hi',
  danger:
    'border-danger/35 bg-danger/[0.06] text-danger/85 hover:border-danger/60 hover:bg-danger/15 hover:text-danger',
  accent:
    'border-accent-cyan/35 bg-accent-cyan/[0.06] text-accent-cyan/85 hover:border-accent-cyan/60 hover:bg-accent-cyan/15 hover:text-accent-cyan',
  // 글리프를 text-navy-app으로 뒤집는 이유 — ok(#2ee6a8) 위의 흰 글리프는 1.6:1,
  // danger(#fb7185) 위는 2.7:1로 아이콘 최소 기준(3:1)에도 못 미친다.
  // 딥네이비로 뒤집으면 각각 12.4:1 · 7.5:1이다. Button의 primary가 같은 이유로 같은 선택을 했다.
  confirm: 'border-ok bg-ok text-navy-app shadow-btn hover:bg-ok/85',
  deny: 'border-danger bg-danger text-navy-app hover:bg-danger/85',
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
      className={`rounded-btn border transition-colors disabled:cursor-not-allowed disabled:opacity-40 ${
        SIZE_PADDING_CLASS[size]
      } ${TONE_CLASS[tone] ?? TONE_CLASS.muted} ${className}`}
      {...rest}
    >
      <Icon size={ICON_SIZE[size]} />
    </button>
  );
}
