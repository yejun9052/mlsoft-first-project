import { render, screen } from '@testing-library/react';
import { describe, expect, it } from 'vitest';
import StatusBadge from './StatusBadge.jsx';

describe('StatusBadge', () => {
  it('renders the mapped label for a known status', () => {
    render(<StatusBadge status="APPROVED" />);
    expect(screen.getByText('승인')).toBeInTheDocument();
  });

  it('falls back to the raw status when no mapping exists', () => {
    render(<StatusBadge status="UNKNOWN_STATUS" />);
    expect(screen.getByText('UNKNOWN_STATUS')).toBeInTheDocument();
  });

  it('lets an explicit label/tone override the status mapping', () => {
    render(<StatusBadge status="APPROVED" label="커스텀" tone="danger" />);
    const badge = screen.getByText('커스텀');
    expect(badge.className).toContain('text-danger');
  });
});
