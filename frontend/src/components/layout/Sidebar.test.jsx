import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';
import { render, screen, waitFor } from '@testing-library/react';
import { MemoryRouter } from 'react-router-dom';
import { QueryClient, QueryClientProvider } from '@tanstack/react-query';
import Sidebar from './Sidebar.jsx';
import { me } from '../../api/auth.js';
import { getMySummary, getPendingApprovals } from '../../api/leaves.js';
import { getPendingWelfareApprovals } from '../../api/welfare.js';

vi.mock('../../api/auth.js', async (importOriginal) => {
  const actual = await importOriginal();

  return {
    ...actual,
    me: vi.fn(),
  };
});

vi.mock('../../api/leaves.js', async (importOriginal) => {
  const actual = await importOriginal();

  return {
    ...actual,
    getMySummary: vi.fn(),
    getPendingApprovals: vi.fn(),
  };
});

vi.mock('../../api/welfare.js', async (importOriginal) => {
  const actual = await importOriginal();

  return {
    ...actual,
    getPendingWelfareApprovals: vi.fn(),
  };
});

/** 사이드바는 목록이 아니라 PagedModel의 page.totalElements만 사용한다. */
function pageOf(totalElements) {
  return {
    content: [],
    page: {
      totalElements,
      totalPages: 1,
    },
  };
}

function renderSidebar() {
  const queryClient = new QueryClient({
    defaultOptions: {
      queries: {
        retry: false,
      },
    },
  });

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
  it('관리자 배지는 연차와 복리후생 대기 건수의 합계다', async () => {
    me.mockResolvedValue({
      name: '관리자',
      role: 'SYSTEM_ADMIN',
      onboarded: true,
    });
    getPendingApprovals.mockResolvedValue(pageOf(3));
    getPendingWelfareApprovals.mockResolvedValue(pageOf(2));

    renderSidebar();

    await waitFor(() =>
      expect(screen.getByText('5')).toBeInTheDocument(),
    );

    expect(getPendingApprovals).toHaveBeenCalledWith({
      page: 0,
      size: 1,
    });
    expect(getPendingWelfareApprovals).toHaveBeenCalledWith({
      page: 0,
      size: 1,
    });
  });

  it('팀장 배지도 같은 두 결재 쿼리의 합계다', async () => {
    me.mockResolvedValue({
      name: '팀장',
      role: 'TEAM_LEADER',
      onboarded: true,
    });
    getPendingApprovals.mockResolvedValue(pageOf(1));
    getPendingWelfareApprovals.mockResolvedValue(pageOf(4));

    renderSidebar();

    await waitFor(() =>
      expect(screen.getByText('5')).toBeInTheDocument(),
    );

    expect(getPendingApprovals).toHaveBeenCalledTimes(1);
    expect(getPendingWelfareApprovals).toHaveBeenCalledTimes(1);
  });

  it('사원은 결재 API를 호출하지 않는다', async () => {
    me.mockResolvedValue({
      name: '사원',
      role: 'EMPLOYEE',
      onboarded: true,
    });

    renderSidebar();

    await waitFor(() =>
      expect(screen.getByText('사원')).toBeInTheDocument(),
    );

    expect(getPendingApprovals).not.toHaveBeenCalled();
    expect(getPendingWelfareApprovals).not.toHaveBeenCalled();
  });

  it('DB에서 강등되면 저장된 관리자 역할과 관계없이 관리자 메뉴가 사라진다', async () => {
    localStorage.setItem(
      'userInfo',
      JSON.stringify({
        name: '강등된사람',
        role: 'SYSTEM_ADMIN',
        onboarded: true,
      }),
    );
    me.mockResolvedValue({
      name: '강등된사람',
      role: 'EMPLOYEE',
      onboarded: true,
    });

    renderSidebar();

    await waitFor(() =>
      expect(screen.queryByText('구성원 관리')).not.toBeInTheDocument(),
    );

    expect(screen.queryByText('복리후생 정책')).not.toBeInTheDocument();
    expect(screen.queryByText('결재 관리')).not.toBeInTheDocument();
  });

  it('DB에서 승격되면 재로그인하지 않아도 관리자 메뉴가 나타난다', async () => {
    localStorage.setItem(
      'userInfo',
      JSON.stringify({
        name: '승격된사람',
        role: 'EMPLOYEE',
        onboarded: true,
      }),
    );
    me.mockResolvedValue({
      name: '승격된사람',
      role: 'SYSTEM_ADMIN',
      onboarded: true,
    });
    getPendingApprovals.mockResolvedValue(pageOf(0));
    getPendingWelfareApprovals.mockResolvedValue(pageOf(0));

    renderSidebar();

    await waitFor(() =>
      expect(screen.getByText('구성원 관리')).toBeInTheDocument(),
    );

    expect(screen.getByText('복리후생 정책')).toBeInTheDocument();
    expect(screen.getByText('결재 관리')).toBeInTheDocument();
  });
});
