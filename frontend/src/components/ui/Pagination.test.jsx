import { render, screen, fireEvent } from '@testing-library/react';
import { describe, expect, it, vi } from 'vitest';
import Pagination from './Pagination.jsx';

describe('Pagination', () => {
  it('renders nothing when there is only one page', () => {
    const { container } = render(<Pagination page={0} totalPages={1} totalElements={5} onChange={vi.fn()} />);
    expect(container).toBeEmptyDOMElement();
  });

  it('shows the 1-base page position and total count', () => {
    render(<Pagination page={2} totalPages={13} totalElements={248} onChange={vi.fn()} />);
    expect(screen.getByText('3 / 13')).toBeInTheDocument();
    expect(screen.getByText('총 248건')).toBeInTheDocument();
  });

  it('reports the next 0-base page when 다음을 누르면', () => {
    const onChange = vi.fn();
    render(<Pagination page={0} totalPages={3} totalElements={50} onChange={onChange} />);
    fireEvent.click(screen.getByRole('button', { name: '다음 페이지' }));
    expect(onChange).toHaveBeenCalledWith(1);
  });

  it('disables 이전 on the first page and 다음 on the last page', () => {
    const { rerender } = render(<Pagination page={0} totalPages={3} totalElements={50} onChange={vi.fn()} />);
    expect(screen.getByRole('button', { name: '이전 페이지' })).toBeDisabled();
    expect(screen.getByRole('button', { name: '다음 페이지' })).not.toBeDisabled();

    rerender(<Pagination page={2} totalPages={3} totalElements={50} onChange={vi.fn()} />);
    expect(screen.getByRole('button', { name: '이전 페이지' })).not.toBeDisabled();
    expect(screen.getByRole('button', { name: '다음 페이지' })).toBeDisabled();
  });
});
