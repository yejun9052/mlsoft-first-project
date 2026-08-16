import { render, screen, fireEvent } from '@testing-library/react';
import { describe, expect, it, vi } from 'vitest';
import Toggle from './Toggle.jsx';

describe('Toggle', () => {
  it('reflects the checked state on the switch role', () => {
    const { rerender } = render(<Toggle checked={false} onChange={() => {}} label="당겨쓰기 허용" />);
    expect(screen.getByRole('switch', { name: '당겨쓰기 허용' })).toHaveAttribute('aria-checked', 'false');

    rerender(<Toggle checked onChange={() => {}} label="당겨쓰기 허용" />);
    expect(screen.getByRole('switch', { name: '당겨쓰기 허용' })).toHaveAttribute('aria-checked', 'true');
  });

  it('hands the inverted value to onChange', () => {
    const onChange = vi.fn();
    render(<Toggle checked onChange={onChange} label="당겨쓰기 허용" />);

    fireEvent.click(screen.getByRole('switch'));

    expect(onChange).toHaveBeenCalledWith(false);
  });

  it('does not call onChange while disabled', () => {
    const onChange = vi.fn();
    render(<Toggle checked={false} onChange={onChange} label="당겨쓰기 허용" disabled />);

    fireEvent.click(screen.getByRole('switch'));

    expect(onChange).not.toHaveBeenCalled();
  });

  // 아래 둘은 클래스 단정이다. 평소에는 피할 방식이지만 여기서는 그것 말고 방법이 없다 —
  // jsdom에는 레이아웃 엔진이 없어 손잡이가 트랙 밖으로 나갔는지를 좌표로 확인할 수 없다.
  // 실제로 2026-08-16에 가로 앵커가 없어 손잡이가 트랙 밖으로 튀어나가 있었다(Toggle.jsx 주석).
  // 그 한 가지 회귀만 잡는 것이 목적이므로 앵커의 존재만 본다.
  it('anchors the knob horizontally so translate stays a pure offset', () => {
    render(<Toggle checked={false} onChange={() => {}} label="당겨쓰기 허용" />);

    const knob = screen.getByRole('switch').querySelector('span');
    expect(knob.className).toMatch(/(^|\s)left-/);
  });

  it('moves the knob only when checked', () => {
    const { rerender } = render(<Toggle checked={false} onChange={() => {}} label="당겨쓰기 허용" />);
    expect(screen.getByRole('switch').querySelector('span').className).toContain('translate-x-0');

    rerender(<Toggle checked onChange={() => {}} label="당겨쓰기 허용" />);
    expect(screen.getByRole('switch').querySelector('span').className).toContain('translate-x-5');
  });
});
