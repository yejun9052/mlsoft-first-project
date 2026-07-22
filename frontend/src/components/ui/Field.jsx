// 필드 래퍼 — 라벨(+필수 표시) + 입력 슬롯(children) + 힌트/에러 텍스트.
// TextInput/Textarea/Select 어느 것이든 children으로 감싸 라벨·안내문을 통일된 자리에 배치한다.
export default function Field({ label, required = false, hint, error, children, className = '' }) {
  return (
    <div className={className}>
      {label && (
        <label className="mb-1.5 block text-[13px] font-medium text-ink-mute">
          {label}
          {required && <span className="ml-0.5 text-danger">*</span>}
        </label>
      )}
      {children}
      {error ? (
        <p className="mt-1.5 text-[11px] text-danger">{error}</p>
      ) : (
        hint && <p className="mt-1.5 text-[11px] text-ink-faint">{hint}</p>
      )}
    </div>
  );
}
