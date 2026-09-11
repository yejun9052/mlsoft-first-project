import { render, screen } from '@testing-library/react';
import { beforeEach, describe, expect, it, vi } from 'vitest';
import { RouterProvider, createMemoryRouter } from 'react-router-dom';
import TeamPage from './TeamPage.jsx';
import { useCurrentUser } from '../hooks/useAuth.js';
import { useDepartments } from '../hooks/useDepartments.js';
import { useHolidays } from '../hooks/useHolidays.js';
import { useTeamMembers } from '../hooks/useUsers.js';
import { useTeamLeaves } from '../hooks/useLeaves.js';

vi.mock('../hooks/useAuth.js', () => ({ useCurrentUser: vi.fn() }));
vi.mock('../hooks/useDepartments.js', () => ({ useDepartments: vi.fn() }));
vi.mock('../hooks/useHolidays.js', () => ({ useHolidays: vi.fn() }));
vi.mock('../hooks/useUsers.js', () => ({ useTeamMembers: vi.fn() }));
vi.mock('../hooks/useLeaves.js', () => ({
  useTeamLeaves: vi.fn(),
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
  });

  // ===== 팀장 표시 (E-1) =====
  //
  // "부서 미배정"과 "팀장 공석"은 관리자가 해야 할 일이 서로 다른데
  // 지금까지 둘 다 "미지정"으로 보였다. 두 문구가 실제로 갈리는지 못박는다.

  it('팀장이 지정돼 있으면 그 이름을 보여준다', () => {
    renderPage();

    expect(screen.getByText('홍팀장')).toBeInTheDocument();
    expect(screen.queryByText('미지정')).not.toBeInTheDocument();
    expect(screen.queryByText('부서 미배정')).not.toBeInTheDocument();
  });

  it('실제 부서인데 팀장이 없으면 "미지정"으로 보여준다', () => {
    useDepartments.mockReturnValue({
      data: [{ id: 10, name: '개발팀', leaderName: null, unassigned: false }],
    });

    renderPage();

    expect(screen.getByText('미지정')).toBeInTheDocument();
    // 이쪽은 팀장을 지정하면 해결된다 — 부서 배정 문구가 나오면 안 된다
    expect(screen.queryByText('부서 미배정')).not.toBeInTheDocument();
  });

  it('소속이 미배정 부서면 "부서 미배정"으로 구분해 보여준다', () => {
    useDepartments.mockReturnValue({
      // 이름은 식별자가 아니다 — 관리자가 바꿔도 unassigned 플래그로 판별해야 한다
      data: [{ id: 10, name: '표시 이름은 식별자가 아님', leaderName: null, unassigned: true }],
    });

    renderPage();

    expect(screen.getByText('부서 미배정')).toBeInTheDocument();
    // 팀장 공석과 같은 문구로 보이면 관리자가 무엇을 해야 하는지 알 수 없다
    expect(screen.queryByText('미지정')).not.toBeInTheDocument();
  });

});
