import { fireEvent, render, screen } from '@testing-library/react';
import { describe, expect, it, vi } from 'vitest';

import Modal from './Modal.jsx';

function renderModal(onClose = vi.fn()) {
  render(
    <Modal title="연차 신청" onClose={onClose}>
      <textarea aria-label="사유" defaultValue="사랑니 빼고 휴식" />
    </Modal>,
  );
  return {
    onClose,
    backdrop: screen.getByRole('dialog'),
    inside: screen.getByLabelText('사유'),
  };
}

describe('Modal 닫기', () => {
  it('backdrop을 누르고 backdrop에서 떼면 닫힌다', () => {
    const { onClose, backdrop } = renderModal();

    fireEvent.pointerDown(backdrop);
    fireEvent.click(backdrop);

    expect(onClose).toHaveBeenCalledTimes(1);
  });

  // 2026-08-17 지적. click은 mousedown과 mouseup의 공통 조상에서 발생하므로,
  // 안에서 누르고 밖에서 떼면 그 공통 조상인 backdrop이 클릭된 것으로 잡힌다.
  // 입력창의 글자를 드래그로 선택하다 손이 모달 밖으로 나가는 것이 정확히 이 경우다.
  it('모달 안에서 누르고 backdrop에서 떼면 닫히지 않는다', () => {
    const { onClose, backdrop, inside } = renderModal();

    fireEvent.pointerDown(inside);
    // 브라우저는 이때 click을 backdrop(공통 조상)에서 발생시킨다
    fireEvent.click(backdrop);

    expect(onClose).not.toHaveBeenCalled();
  });

  it('모달 안을 클릭해도 닫히지 않는다', () => {
    const { onClose, inside } = renderModal();

    fireEvent.pointerDown(inside);
    fireEvent.click(inside);

    expect(onClose).not.toHaveBeenCalled();
  });

  // 한 번 밖에서 뗀 뒤 곧바로 backdrop을 눌러 떼면 정상적으로 닫혀야 한다
  // (닫기 조건이 소진되지 않고 남아 있으면 반대로 안 닫히는 결함이 된다)
  it('안에서 눌러 밖에서 뗀 다음에도 backdrop 클릭은 그대로 닫는다', () => {
    const { onClose, backdrop, inside } = renderModal();
    fireEvent.pointerDown(inside);
    fireEvent.click(backdrop);
    expect(onClose).not.toHaveBeenCalled();

    fireEvent.pointerDown(backdrop);
    fireEvent.click(backdrop);

    expect(onClose).toHaveBeenCalledTimes(1);
  });

  it('Esc로 닫힌다', () => {
    const { onClose } = renderModal();

    fireEvent.keyDown(window, { key: 'Escape' });

    expect(onClose).toHaveBeenCalledTimes(1);
  });

  it('닫기 버튼으로 닫힌다', () => {
    const { onClose } = renderModal();

    fireEvent.click(screen.getByRole('button', { name: '닫기' }));

    expect(onClose).toHaveBeenCalledTimes(1);
  });
});
