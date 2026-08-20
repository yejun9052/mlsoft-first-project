import { beforeEach, describe, expect, it, vi } from 'vitest';
import { fireEvent, render, screen, waitFor, within } from '@testing-library/react';
import { QueryClient, QueryClientProvider } from '@tanstack/react-query';
import { createMemoryRouter, RouterProvider } from 'react-router-dom';
import Layout from './Layout.jsx';
import { useCurrentUser } from '../../hooks/useAuth.js';
import { useLeaveSummary, usePendingApprovals } from '../../hooks/useLeaves.js';
import { usePendingWelfareApprovals } from '../../hooks/useWelfare.js';

vi.mock('../../hooks/useAuth.js', () => ({
  useCurrentUser: vi.fn(),
}));

vi.mock('../../hooks/useLeaves.js', () => ({
  useLeaveSummary: vi.fn(),
  usePendingApprovals: vi.fn(),
}));

vi.mock('../../hooks/useWelfare.js', () => ({
  usePendingWelfareApprovals: vi.fn(),
}));

function installMatchMedia(matches) {
  window.matchMedia = vi.fn().mockImplementation((query) => ({
    matches,
    media: query,
    onchange: null,
    addEventListener: vi.fn(),
    removeEventListener: vi.fn(),
    dispatchEvent: vi.fn(),
  }));
}

function renderLayout(role = 'EMPLOYEE') {
  useCurrentUser.mockReturnValue({
    data: {
      id: 1,
      name: '테스트 사용자',
      departmentName: '개발팀',
      role,
      onboarded: true,
    },
  });
  useLeaveSummary.mockReturnValue({ data: null });
  usePendingApprovals.mockReturnValue({ data: undefined });
  usePendingWelfareApprovals.mockReturnValue({ data: undefined });

  const queryClient = new QueryClient({
    defaultOptions: {
      queries: { retry: false },
    },
  });

  const routes = [
    {
      path: '/',
      element: <Layout />,
      children: [
        { index: true, element: <p>첫 화면</p> },
        { path: 'dashboard', element: <p>대시보드 화면</p> },
        { path: 'calendar', element: <p>캘린더 화면</p> },
        { path: 'history', element: <p>사용 내역 화면</p> },
        { path: 'welfare', element: <p>복리후생 화면</p> },
        { path: 'team', element: <p>팀 정보 화면</p> },
        { path: 'myinfo', element: <p>내 정보 화면</p> },
        { path: 'approvals', element: <p>결재 화면</p> },
        { path: 'admin', element: <p>구성원 화면</p> },
        { path: 'admin/departments', element: <p>부서 화면</p> },
        { path: 'admin/policy', element: <p>정책 화면</p> },
        { path: 'admin/welfare-policies', element: <p>복리후생 정책 화면</p> },
        { path: 'admin/history', element: <p>처리 이력 화면</p> },
      ],
    },
  ];

  const router = createMemoryRouter(routes, { initialEntries: ['/'] });

  return render(
    <QueryClientProvider client={queryClient}>
      <RouterProvider router={router} />
    </QueryClientProvider>,
  );
}

beforeEach(() => {
  vi.clearAllMocks();
  document.body.style.overflow = '';
});

describe('Layout 모바일 서랍', () => {
  it('좁은 화면에서 메뉴 버튼을 누르면 서랍이 열린다', () => {
    installMatchMedia(true);
    renderLayout();

    fireEvent.click(screen.getByRole('button', { name: '메뉴 열기' }));

    expect(screen.getByRole('dialog', { name: '전체 메뉴' })).toBeInTheDocument();
    expect(screen.getByRole('button', { name: '메뉴 닫기' })).toHaveFocus();
    expect(document.body.style.overflow).toBe('hidden');
  });

  it('열린 서랍은 Esc로 닫히고 메뉴 버튼으로 포커스를 돌려보낸다', async () => {
    installMatchMedia(true);
    renderLayout();

    const openButton = screen.getByRole('button', { name: '메뉴 열기' });
    fireEvent.click(openButton);
    fireEvent.keyDown(window, { key: 'Escape' });

    expect(screen.queryByRole('dialog', { name: '전체 메뉴' })).not.toBeInTheDocument();
    await waitFor(() => expect(openButton).toHaveFocus());
    expect(document.body.style.overflow).toBe('');
  });

  it('메뉴 항목을 누르면 이동하면서 서랍을 닫는다', async () => {
    installMatchMedia(true);
    renderLayout();

    fireEvent.click(screen.getByRole('button', { name: '메뉴 열기' }));
    const drawer = screen.getByRole('dialog', { name: '전체 메뉴' });
    fireEvent.click(within(drawer).getByRole('link', { name: '팀 캘린더' }));

    expect(await screen.findByText('캘린더 화면')).toBeInTheDocument();
    expect(screen.queryByRole('dialog', { name: '전체 메뉴' })).not.toBeInTheDocument();
  });

  it.each([
    ['EMPLOYEE', 6],
    ['TEAM_LEADER', 8],
    ['SYSTEM_ADMIN', 12],
  ])('%s 역할은 좁은 화면에서도 허용된 메뉴 %i개만 본다', (role, expectedCount) => {
    installMatchMedia(true);
    renderLayout(role);

    fireEvent.click(screen.getByRole('button', { name: '메뉴 열기' }));
    const drawer = screen.getByRole('dialog', { name: '전체 메뉴' });

    expect(within(drawer).getAllByRole('link')).toHaveLength(expectedCount);
  });

  it('넓은 화면에서는 기존 사이드바를 보이고 메뉴 버튼을 만들지 않는다', () => {
    installMatchMedia(false);
    renderLayout('SYSTEM_ADMIN');

    expect(screen.queryByRole('button', { name: '메뉴 열기' })).not.toBeInTheDocument();
    expect(screen.getByRole('link', { name: '대시보드' })).toBeInTheDocument();
    expect(screen.getByRole('link', { name: '구성원 관리' })).toBeInTheDocument();
  });
});
