import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';
import { render, screen, waitFor } from '@testing-library/react';
import { MemoryRouter, Route, Routes } from 'react-router-dom';
import { QueryClient, QueryClientProvider } from '@tanstack/react-query';
import RequireAuth from './RequireAuth.jsx';
import { me } from '../api/auth.js';

vi.mock('../api/auth.js', async (importOriginal) => {
  const actual = await importOriginal();

  return {
    ...actual,
    me: vi.fn(),
  };
});

const ADMIN_ROLES = ['SYSTEM_ADMIN'];

function renderGuard({ roles = ADMIN_ROLES, initialPath = '/admin' } = {}) {
  // 실패 재시도를 끄지 않으면 401 판정 시점이 늦어져 테스트 결과가 타이밍에 따라 흔들린다.
  const queryClient = new QueryClient({
    defaultOptions: {
      queries: {
        retry: false,
      },
    },
  });

  return render(
    <QueryClientProvider client={queryClient}>
      <MemoryRouter initialEntries={[initialPath]}>
        <Routes>
          <Route
            path="/admin"
            element={
              <RequireAuth roles={roles}>
                <div>관리자 화면</div>
              </RequireAuth>
            }
          />
          <Route path="/dashboard" element={<div>대시보드</div>} />
          <Route path="/login" element={<div>로그인</div>} />
          <Route path="/onboarding" element={<div>온보딩</div>} />
        </Routes>
      </MemoryRouter>
    </QueryClientProvider>,
  );
}

function storeUserInfo(userInfo) {
  localStorage.setItem('userInfo', JSON.stringify(userInfo));
}

beforeEach(() => {
  localStorage.clear();
  me.mockReset();
});

afterEach(() => {
  localStorage.clear();
});

describe('RequireAuth — 권한 판정은 서버 응답 기준 (리뷰 F-7)', () => {
  it('DB에서 강등되면 저장값이 관리자여도 재로그인 없이 차단된다', async () => {
    storeUserInfo({
      name: '강등된사람',
      role: 'SYSTEM_ADMIN',
      onboarded: true,
    });
    me.mockResolvedValue({
      name: '강등된사람',
      role: 'EMPLOYEE',
      onboarded: true,
    });

    renderGuard();

    await waitFor(() =>
      expect(screen.getByText('대시보드')).toBeInTheDocument(),
    );
    expect(screen.queryByText('관리자 화면')).not.toBeInTheDocument();
  });

  it('서버 확인 전에는 관리자 화면을 렌더하지 않는다 (Codex 리뷰 2026-08-10)', async () => {
    storeUserInfo({
      name: '강등된사람',
      role: 'SYSTEM_ADMIN',
      onboarded: true,
    });

    let resolveMe;
    me.mockReturnValue(
      new Promise((resolve) => {
        resolveMe = resolve;
      }),
    );

    renderGuard();

    expect(screen.queryByText('관리자 화면')).not.toBeInTheDocument();
    expect(screen.queryByText('대시보드')).not.toBeInTheDocument();

    resolveMe({
      name: '강등된사람',
      role: 'EMPLOYEE',
      onboarded: true,
    });

    await waitFor(() =>
      expect(screen.getByText('대시보드')).toBeInTheDocument(),
    );
  });

  it('DB에서 승격되면 저장값이 사원이어도 통과한다', async () => {
    storeUserInfo({
      name: '승격된사람',
      role: 'EMPLOYEE',
      onboarded: true,
    });
    me.mockResolvedValue({
      name: '승격된사람',
      role: 'SYSTEM_ADMIN',
      onboarded: true,
    });

    renderGuard();

    await waitFor(() =>
      expect(screen.getByText('관리자 화면')).toBeInTheDocument(),
    );
  });

  it('서버 응답을 localStorage에 반영한다', async () => {
    storeUserInfo({
      name: '강등된사람',
      role: 'SYSTEM_ADMIN',
      onboarded: true,
    });
    me.mockResolvedValue({
      name: '강등된사람',
      role: 'EMPLOYEE',
      onboarded: true,
    });

    renderGuard();

    await waitFor(() => {
      const stored = JSON.parse(localStorage.getItem('userInfo'));
      expect(stored.role).toBe('EMPLOYEE');
    });
  });

  it('저장값이 없고 서버 인증도 실패하면 로그인으로 보낸다', async () => {
    me.mockRejectedValue(new Error('401'));

    renderGuard();

    await waitFor(() =>
      expect(screen.getByText('로그인')).toBeInTheDocument(),
    );
  });

  it('저장값이 손상돼도 서버 응답으로 복구된다', async () => {
    localStorage.setItem('userInfo', '{깨진 JSON');
    me.mockResolvedValue({
      name: '정상',
      role: 'SYSTEM_ADMIN',
      onboarded: true,
    });

    renderGuard();

    await waitFor(() =>
      expect(screen.getByText('관리자 화면')).toBeInTheDocument(),
    );

    expect(JSON.parse(localStorage.getItem('userInfo'))).toEqual({
      name: '정상',
      role: 'SYSTEM_ADMIN',
      onboarded: true,
    });
  });

  it('역할 제한이 없는 경로에서도 온보딩 미완료 사용자는 온보딩 페이지로 보낸다 (검증 Y-2)', async () => {
    storeUserInfo({
      name: '신입',
      role: 'EMPLOYEE',
      onboarded: false,
    });
    me.mockResolvedValue({
      name: '신입',
      role: 'EMPLOYEE',
      onboarded: false,
    });

    // undefined는 renderGuard의 기본 관리자 역할을 적용하므로 명시적으로 null을 넘긴다.
    renderGuard({ roles: null });

    await waitFor(() =>
      expect(screen.getByText('온보딩')).toBeInTheDocument(),
    );
  });
});
