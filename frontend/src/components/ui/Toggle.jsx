// 스위치 토글 — AdminPolicyPage 시스템 설정 boolean 컨트롤
export default function Toggle({ checked, onChange, label, disabled = false, className = '' }) {
  return (
    <button
      type="button"
      role="switch"
      aria-checked={checked}
      aria-label={label}
      disabled={disabled}
      onClick={() => onChange(!checked)}
      className={`relative h-6 w-11 shrink-0 rounded-full transition-all duration-200 disabled:cursor-not-allowed disabled:opacity-50 ${
        checked
          ? 'bg-accent shadow-btn'
          : 'border border-white/[0.15] bg-navy-app/70'
      } ${className}`}
    >
      {/* left-0.5를 지우지 말 것 (2026-08-16 수정).
          absolute에 가로 앵커가 없으면 브라우저는 "정적 위치"를 쓰는데, <button>은 UA 스타일시트가
          text-align: center를 주므로 그 정적 위치가 트랙 가운데(12px)가 된다. 거기서 translate가
          얹혀 OFF는 오른쪽으로 밀리고 ON은 32~52px이 되어 44px 트랙 밖으로 손잡이가 튀어나갔다.
          앵커를 박으면 translate는 순수한 이동량이 된다 — 2px → 22px, 양쪽 여백이 2px로 대칭이다. */}
      <span
        className={`absolute left-0.5 top-0.5 h-5 w-5 rounded-full bg-white transition-transform duration-200 ${
          checked ? 'translate-x-5' : 'translate-x-0'
        }`}
      />
    </button>
  );
}
