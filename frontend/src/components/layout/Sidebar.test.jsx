import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';
import { render, screen, waitFor } from '@testing-library/react';
import { MemoryRouter } from 'react-router-dom';
import { QueryClient, QueryClientProvider } from '@tanstack/react-query';
import Sidebar from './Sidebar.jsx';
import { me } from '../../api/auth.js';
import { getMySummary, getPendingApprovals } from '../../api/leaves.js';
import { getPendingWelfareApprovals } from '../../api/welfare.js';

vi.mock('../../api/auth.js', () => ({ me: vi.fn(), logout: vi.fn() }));
vi.mock('../../api/leaves.js', () => ({
  getMySummary: vi.fn(),
  getPendingApprovals: vi.fn(),
  getAllLeaves: vi.fn(),
}));
vi.mock('../../api/welfare.js', () => ({ getPendingWelfareApprovals: vi.fn() }));

/** 페이지 응답 껍데기 — 사이드바는 목록이 아니라 page.totalElements만 본다 */
function pageOf(totalElements) {
  return { content: [], page: { totalElements, totalPages: 1 } };
}

function renderSidebar() {
  const queryClient = new QueryClient({ defaultOptions: { queries: { retry: false } } });
  return render(
    <QueryClientProvider client={queryClient}>
      <MemoryRouter>
        <Sidebar />
      </MemoryRouter>
    </QueryClientProvider>,
  );
}

beforeEach(() => {
  localStorage.clear();
  me.mockReset();
  getMySummary.mockReset();
  getPendingApprovals.mockReset();
  getPendingWelfareApprovals.mockReset();
  getMySummary.mockResolvedValue({
    baseDays: '15.0',
    bonusDays: '0.0',
    useDays: '0.0',
    advanceDays: '0.0',
    pendingDays: '0.0',
    remainingDays: '15.0',
  });
});

afterEach(() => {
  localStorage.clear();
});

describe('Sidebar 결재 배지 — 결재 화면 목록과 같은 기준 (리뷰 F-8)', () => {
  it('관리자 배지는 연차 + 복리후생 대기 합계다 (전사 건수가 아니다)', async () => {
    // 예전에는 관리자에게 전사 연차 건수를 보여줬다. 남이 결재할 건까지 세면서 정작
    // 복리후생은 빠져 있어, 배지를 눌러 들어간 목록과 숫자가 겹치는 구석이 없었다.
    me.mockResolvedValue({ name: '관리자', role: 'SYSTEM_ADMIN', onboarded: true });
    getPendingApprovals.mockResolvedValue(pageOf(3));
    getPendingWelfareApprovals.mockResolvedValue(pageOf(2));

    renderSidebar();

    await waitFor(() => expect(screen.getByText('5')).toBeInTheDocument());
    // 전사 건수 조회는 더 이상 사이드바에서 쓰지 않는다
    await waitFor(() => expect(getPendingApprovals).toHaveBeenCalled());
  });

  it('팀장 배지도 같은 두 쿼리의 합계다 — 역할로 기준이 갈리지 않는다', async () => {
    me.mockResolvedValue({ name: '팀장', role: 'TEAM_LEADER', onboarded: true });
    getPendingApprovals.mockResolvedValue(pageOf(1));
    getPendingWelfareApprovals.mockResolvedValue(pageOf(4));

    renderSidebar();

    await waitFor(() => expect(screen.getByText('5')).toBeInTheDocument());
  });

  it('사원은 결재 쿼리를 아예 호출하지 않는다 — 403 토스트 방지', async () => {
    me.mockResolvedValue({ name: '사원', role: 'EMPLOYEE', onboarded: true });

    renderSidebar();

    await waitFor(() => expect(screen.getByText('사원')).toBeInTheDocument());
    expect(getPendingApprovals).not.toHaveBeenCalled();
    expect(getPendingWelfareApprovals).not.toHaveBeenCalled();
  });

  it('강등되면 관리자 메뉴가 사라진다 — 저장값이 아니라 서버 응답을 본다 (리뷰 F-7)', async () => {
    localStorage.setItem(
      'userInfo',
      JSON.stringify({ name: '강등된사람', role: 'SYSTEM_ADMIN', onboarded: true }),
    );
    me.mockResolvedValue({ name: '강등된사람', role: 'EMPLOYEE', onboarded: true });

    renderSidebar();

    // 첫 렌더는 저장값으로 관리자 메뉴가 보이지만, 재검증 뒤에는 사라진다
    await waitFor(() => expect(screen.queryByText('구성원 관리')).not.toBeInTheDocument());
    expect(screen.queryByText('복리후생 정책')).not.toBeInTheDocument();
  });

  it('승격되면 관리자 메뉴가 나타난다 — 재로그인이 필요하지 않다 (리뷰 F-7)', async () => {
    localStorage.setItem(
      'userInfo',
      JSON.stringify({ name: '승격된사람', role: 'EMPLOYEE', onboarded: true }),
    );
    me.mockResolvedValue({ name: '승격된사람', role: 'SYSTEM_ADMIN', onboarded: true });
    getPendingApprovals.mockResolvedValue(pageOf(0));
    getPendingWelfareApprovals.mockResolvedValue(pageOf(0));

    renderSidebar();

    await waitFor(() => expect(screen.getByText('구성원 관리')).toBeInTheDocument());
  });
});
