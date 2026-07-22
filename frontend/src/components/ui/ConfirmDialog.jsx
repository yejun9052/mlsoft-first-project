import Modal from './Modal.jsx';
import Button from './Button.jsx';

// 확인 다이얼로그 — Modal 위에 조립한 2단계 확인 패턴.
// ApprovalsPage의 "카드마다 상시 노출되던 결재의견 인풋 + 인라인 2단계 확인"을 대체 — 승인/반려 클릭 시
// 이 다이얼로그 하나만 뜨도록 한다. children으로 결재 의견 Textarea 등 추가 입력을 끼워 넣을 수 있다.
export default function ConfirmDialog({
  open,
  title,
  message,
  tone = 'default',
  confirmLabel = '확인',
  cancelLabel = '취소',
  onConfirm,
  onCancel,
  loading = false,
  children,
}) {
  if (!open) return null;

  return (
    <Modal
      title={title}
      onClose={onCancel}
      footer={
        <>
          <Button variant="secondary" onClick={onCancel} disabled={loading} lift={false}>
            {cancelLabel}
          </Button>
          <Button
            variant={tone === 'danger' ? 'danger' : 'primary'}
            onClick={onConfirm}
            loading={loading}
            lift={false}
          >
            {confirmLabel}
          </Button>
        </>
      }
    >
      {message && <p className="text-[13px] leading-relaxed text-ink-body">{message}</p>}
      {children}
    </Modal>
  );
}
