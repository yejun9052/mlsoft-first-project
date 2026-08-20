import { fireEvent, render, screen } from '@testing-library/react';
import { beforeEach, describe, expect, it, vi } from 'vitest';
import { RouterProvider, createMemoryRouter } from 'react-router-dom';
import TeamPage from './TeamPage.jsx';
import { useCurrentUser } from '../hooks/useAuth.js';
import { useDepartments } from '../hooks/useDepartments.js';
import { useHolidays } from '../hooks/useHolidays.js';
import { useTeamMembers } from '../hooks/useUsers.js';
import { useTeamAnnualUsage, useTeamLeaves } from '../hooks/useLeaves.js';

vi.mock('../hooks/useAuth.js', () => ({ useCurrentUser: vi.fn() }));
vi.mock('../hooks/useDepartments.js', () => ({ useDepartments: vi.fn() }));
vi.mock('../hooks/useHolidays.js', () => ({ useHolidays: vi.fn() }));
vi.mock('../hooks/useUsers.js', () => ({ useTeamMembers: vi.fn() }));
vi.mock('../hooks/useLeaves.js', () => ({
  useTeamLeaves: vi.fn(),
  useTeamAnnualUsage: vi.fn(),
}));

function renderPage() {
  const router = createMemoryRouter(
    [{ path: '/team', element: <TeamPage /> }],
    { initialEntries: ['/team'] },
  );
  return render(<RouterProvider router={router} />);
}

describe('TeamPage', () => {
  beforeEach(() => {
    vi.clearAllMocks();
    useCurrentUser.mockReturnValue({
      data: { id: 1, departmentId: 10, departmentName: '개발팀' },
    });
    useDepartments.mockReturnValue({
      data: [{ id: 10, name: '개발팀', leaderName: '홍팀장' }],
    });
    useTeamMembers.mockReturnValue({ data: [], isLoading: false, isError: false, refetch: vi.fn() });
    useTeamLeaves.mockReturnValue({ data: [] });
    useHolidays.mockReturnValue({ data: [], isError: false });
    useTeamAnnualUsage.mockReturnValue({
      data: [{ date: '2026-01-05', memberCount: 2 }],
      isLoading: false,
      isError: false,
      refetch: vi.fn(),
    });
  });

  it('연도 선택을 바꾸면 선택한 연도로 다시 조회한다', () => {
    renderPage();

    fireEvent.change(screen.getByLabelText('히트맵 연도'), { target: { value: '2025' } });

    expect(useTeamAnnualUsage).toHaveBeenLastCalledWith(2025);
    expect(useHolidays).toHaveBeenLastCalledWith(2025);
  });

  it('팀 히트맵에는 이름이나 사유 대신 인원 수만 표시한다', () => {
    renderPage();

    expect(screen.getByRole('button', { name: '2026-01-05 · 2명' })).toBeInTheDocument();
    expect(screen.queryByText(/홍길동|개인 사유/)).not.toBeInTheDocument();
  });
});
