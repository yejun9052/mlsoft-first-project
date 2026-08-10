import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';
import { render, screen, waitFor } from '@testing-library/react';
import { MemoryRouter, Route, Routes } from 'react-router-dom';
import { QueryClient, QueryClientProvider } from '@tanstack/react-query';
import RequireAuth from './RequireAuth.jsx';
import { me } from '../api/auth.js';

vi.mock('../api/auth.js', () => ({ me: vi.fn() }));

const ADMIN_ROLES = ['SYSTEM_ADMIN'];

function renderGuard({ roles = ADMIN_ROLES, initialPath = '/admin' } = {}) {
  // retry: false — 실패를 재시도하면 테스트가 느려지고 타이밍이 흔들린다
  const queryClient = new QueryClient({ defaultOptions: { queries: { retry: false } } });
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
    // 서버는 OnboardingCheckInterceptor가 매 요청 DB role로 권한을 재구성해 즉시 403을 준다.
    // 화면이 localStorage만 보면 재로그인 전까지 관리자 메뉴가 계속 열려 있었다.
    storeUserInfo({ name: '강등된사람', role: 'SYSTEM_ADMIN', onboarded: true });
    me.mockResolvedValue({ name: '강등된사람', role: 'EMPLOYEE', onboarded: true });

    renderGuard();

    // 첫 렌더는 저장값(initialData)으로 통과하지만, 응답이 오면 대시보드로 밀려난다
    await waitFor(() => expect(screen.getByText('대시보드')).toBeInTheDocument());
    expect(screen.queryByText('관리자 화면')).not.toBeInTheDocument();
  });

  it('서버 확인 전에는 관리자 화면을 렌더하지 않는다 (Codex 리뷰 2026-08-10)', async () => {
    // 거부 쪽만 막아 뒀더니 허용 쪽이 낡은 저장값으로 열렸다 — 강등된 관리자가 /admin을
    // 새로고침하면 응답 전까지 관리자 화면이 마운트되고 그 화면의 API가 줄줄이 403을 받았다.
    storeUserInfo({ name: '강등된사람', role: 'SYSTEM_ADMIN', onboarded: true });
    let resolveMe;
    me.mockReturnValue(new Promise((resolve) => { resolveMe = resolve; }));

    renderGuard();

    // 응답이 오기 전 — 아무것도 렌더되지 않는다
    expect(screen.queryByText('관리자 화면')).not.toBeInTheDocument();
    expect(screen.queryByText('대시보드')).not.toBeInTheDocument();

    resolveMe({ name: '강등된사람', role: 'EMPLOYEE', onboarded: true });
    await waitFor(() => expect(screen.getByText('대시보드')).toBeInTheDocument());
  });

  it('DB에서 승격되면 저장값이 사원이어도 통과한다', async () => {
    storeUserInfo({ name: '승격된사람', role: 'EMPLOYEE', onboarded: true });
    me.mockResolvedValue({ name: '승격된사람', role: 'SYSTEM_ADMIN', onboarded: true });

    renderGuard();

    await waitFor(() => expect(screen.getByText('관리자 화면')).toBeInTheDocument());
  });

  it('서버 응답을 localStorage에 반영한다 — 새로고침 직후 첫 프레임이 낡지 않게', async () => {
    storeUserInfo({ name: '강등된사람', role: 'SYSTEM_ADMIN', onboarded: true });
    me.mockResolvedValue({ name: '강등된사람', role: 'EMPLOYEE', onboarded: true });

    renderGuard();

    await waitFor(() =>
      expect(JSON.parse(localStorage.getItem('userInfo')).role).toBe('EMPLOYEE'),
    );
  });

  it('저장값이 없으면 로그인으로 보낸다', async () => {
    me.mockRejectedValue(new Error('401'));

    renderGuard();

    await waitFor(() => expect(screen.getByText('로그인')).toBeInTheDocument());
  });

  it('저장값이 손상돼도 서버 응답으로 복구된다', async () => {
    localStorage.setItem('userInfo', '{깨진 JSON');
    me.mockResolvedValue({ name: '정상', role: 'SYSTEM_ADMIN', onboarded: true });

    renderGuard();

    await waitFor(() => expect(screen.getByText('관리자 화면')).toBeInTheDocument());
  });

  it('온보딩 미완료는 온보딩 페이지로 보낸다 (검증 Y-2)', async () => {
    storeUserInfo({ name: '신입', role: 'EMPLOYEE', onboarded: false });
    me.mockResolvedValue({ name: '신입', role: 'EMPLOYEE', onboarded: false });

    renderGuard({ roles: undefined });

    await waitFor(() => expect(screen.getByText('온보딩')).toBeInTheDocument());
  });
});
