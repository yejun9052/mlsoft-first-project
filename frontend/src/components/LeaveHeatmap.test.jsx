import { fireEvent, render, screen } from '@testing-library/react';
import { describe, expect, it } from 'vitest';
import LeaveHeatmap from './LeaveHeatmap.jsx';

describe('LeaveHeatmap', () => {
  it('값에 따라 서로 다른 색 단계를 적용한다', () => {
    render(
      <LeaveHeatmap
        year={2026}
        entries={[
          { date: '2026-01-05', days: '0.5' },
          { date: '2026-01-06', days: '1.0' },
        ]}
      />,
    );

    expect(screen.getByRole('button', { name: '2026-01-05 · 0.5일' })).toHaveClass('bg-heatmap-1');
    expect(screen.getByRole('button', { name: '2026-01-06 · 1일' })).toHaveClass('bg-heatmap-2');
  });

  it('사용 기록이 없는 주말과 공휴일을 회색으로 표시한다', () => {
    render(
      <LeaveHeatmap
        year={2026}
        entries={[]}
        holidays={[{ date: '2026-01-01', name: '신정' }]}
      />,
    );

    expect(screen.getByRole('button', { name: '2026-01-03 · 0일' })).toHaveClass('bg-heatmap-rest');
    expect(screen.getByRole('button', { name: '2026-01-01 · 0일' })).toHaveClass('bg-heatmap-rest');
  });

  it('툴팁을 호버와 키보드 포커스에서 모두 표시한다', () => {
    render(<LeaveHeatmap year={2026} entries={[{ date: '2026-01-05', days: '0.5' }]} />);
    const cell = screen.getByRole('button', { name: '2026-01-05 · 0.5일' });

    fireEvent.mouseEnter(cell);
    expect(screen.getByRole('tooltip')).toHaveTextContent('2026-01-05 · 0.5일');

    fireEvent.mouseLeave(cell);
    expect(screen.queryByRole('tooltip')).not.toBeInTheDocument();

    fireEvent.focus(cell);
    expect(screen.getByRole('tooltip')).toHaveTextContent('2026-01-05 · 0.5일');
  });

  it('팀 모드에서는 날짜별 인원 수를 표시한다', () => {
    render(
      <LeaveHeatmap
        year={2026}
        mode="team"
        entries={[{ date: '2026-01-05', memberCount: 3 }]}
      />,
    );

    expect(screen.getByRole('button', { name: '2026-01-05 · 3명' })).toHaveClass('bg-heatmap-3');
  });
});
