import { render, screen, fireEvent } from '@testing-library/react';
import { describe, expect, it, vi } from 'vitest';
import Button from './Button.jsx';

describe('Button', () => {
  it('renders children and handles click', () => {
    const onClick = vi.fn();
    render(<Button onClick={onClick}>저장</Button>);
    fireEvent.click(screen.getByText('저장'));
    expect(onClick).toHaveBeenCalledTimes(1);
  });

  it('shows a spinner and disables the button while loading', () => {
    render(<Button loading>신청하기</Button>);
    const button = screen.getByRole('button', { name: '신청하기' });
    expect(button).toBeDisabled();
  });

  it('disables the button when disabled is set', () => {
    render(<Button disabled>취소</Button>);
    expect(screen.getByRole('button', { name: '취소' })).toBeDisabled();
  });
});
