// 세그먼트 토글 — LeaveApplyPanel의 종류(연차/오전 반차/오후 반차) 선택 등 N지선다 버튼 그룹.
// 옵션 개수가 가변이라 grid-template-columns는 인라인 스타일로 계산(Tailwind는 동적 클래스명을 인식 못 함).
export default function SegmentedControl({ options, value, onChange, className = '' }) {
  return (
    <div
      className={`grid gap-1 rounded-btn bg-navy-app/50 p-1 ${className}`}
      style={{ gridTemplateColumns: `repeat(${options.length}, minmax(0, 1fr))` }}
    >
      {options.map((option) => (
        <button
          key={option.value}
          type="button"
          onClick={() => onChange(option.value)}
          className={`rounded-btn px-2 py-2 text-[13px] font-semibold transition-colors ${
            option.value === value ? 'bg-accent text-white' : 'text-ink-mute hover:text-ink-body'
          }`}
        >
          {option.label}
        </button>
      ))}
    </div>
  );
}
