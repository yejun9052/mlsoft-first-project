import { render, screen } from '@testing-library/react';
import { describe, expect, it } from 'vitest';
import { Crown } from 'lucide-react';
import Stat from './Stat.jsx';

describe('Stat', () => {
  it('renders label, value and unit in the default vertical layout', () => {
    render(<Stat label="잔여 연차" value={12} unit="일" size="hero" />);
    expect(screen.getByText('잔여 연차')).toBeInTheDocument();
    expect(screen.getByText('12')).toBeInTheDocument();
    expect(screen.getByText('일')).toBeInTheDocument();
  });

  it('renders an icon-chip horizontal layout when Icon is given', () => {
    render(<Stat label="팀장" value="홍길동" Icon={Crown} />);
    expect(screen.getByText('팀장')).toBeInTheDocument();
    expect(screen.getByText('홍길동')).toBeInTheDocument();
  });

  it('renders a caption line when provided', () => {
    render(<Stat label="사용" value={13} unit="일" caption="확정 12일 · 대기 1일" />);
    expect(screen.getByText('확정 12일 · 대기 1일')).toBeInTheDocument();
  });
});
