import { useState } from 'react';
import { act, fireEvent, render, screen } from '@testing-library/react';
import {
  Link,
  Navigate,
  RouterProvider,
  createMemoryRouter,
} from 'react-router-dom';
import { describe, expect, it } from 'vitest';

import { useUnsavedGuard } from './useUnsavedGuard.js';

function Harness({ initialDirty }) {
  const [dirty, setDirty] = useState(initialDirty);
  const guard = useUnsavedGuard(dirty);

  return (
    <>
      <Link to="/dashboard">대시보드</Link>
      <span data-testid="blocked">{String(guard.blocked)}</span>
      <button type="button" onClick={guard.leave}>
        그냥 나가기
      </button>
      <button type="button" onClick={guard.stay}>
        취소
      </button>
      <button type="button" onClick={() => setDirty(false)}>
        저장 완료
      </button>
    </>
  );
}

function renderHarness(initialDirty, options = {}) {
  const {
    initialEntries = ['/admin/policy'],
    initialIndex = initialEntries.length - 1,
  } = options;

  const router = createMemoryRouter(
    [
      {
        path: '/admin/policy',
        element: <Harness initialDirty={initialDirty} />,
      },
      {
        path: '/dashboard',
        element: <div>대시보드 화면</div>,
      },
    ],
    {
      initialEntries,
      initialIndex,
    },
  );

  return {
    router,
    ...render(<RouterProvider router={router} />),
  };
}

function blocked() {
  return screen.getByTestId('blocked').textContent;
}

describe('useUnsavedGuard', () => {
  it('저장할 게 없으면 링크 클릭이 그대로 이동한다', async () => {
    const { router } = renderHarness(false);

    fireEvent.click(screen.getByText('대시보드'));

    expect(await screen.findByText('대시보드 화면')).toBeInTheDocument();
    expect(router.state.location.pathname).toBe('/dashboard');
  });

  it('저장 안 한 변경이 있으면 링크 이동을 붙잡는다', () => {
    const { router } = renderHarness(true);

    fireEvent.click(screen.getByText('대시보드'));

    expect(blocked()).toBe('true');
    expect(router.state.location.pathname).toBe('/admin/policy');
  });

  it('그냥 나가기를 누르면 붙잡아 둔 곳으로 이동한다', async () => {
    const { router } = renderHarness(true);
    fireEvent.click(screen.getByText('대시보드'));

    fireEvent.click(screen.getByText('그냥 나가기'));

    expect(await screen.findByText('대시보드 화면')).toBeInTheDocument();
    expect(router.state.location.pathname).toBe('/dashboard');
  });

  it('취소를 누르면 화면에 남고 다음 이동도 다시 붙잡는다', () => {
    const { router } = renderHarness(true);
    fireEvent.click(screen.getByText('대시보드'));

    fireEvent.click(screen.getByText('취소'));

    expect(blocked()).toBe('false');
    expect(router.state.location.pathname).toBe('/admin/policy');

    // 저장한 게 아니므로 가드는 계속 살아 있어야 한다 — 한 번 취소하면 풀리는 그물은 그물이 아니다
    fireEvent.click(screen.getByText('대시보드'));

    expect(blocked()).toBe('true');
    expect(router.state.location.pathname).toBe('/admin/policy');
  });

  it('저장 안 한 변경이 있으면 브라우저 뒤로가기를 붙잡는다', async () => {
    const { router } = renderHarness(true, {
      initialEntries: ['/dashboard', '/admin/policy'],
      initialIndex: 1,
    });

    await act(() => router.navigate(-1));

    expect(blocked()).toBe('true');
    expect(router.state.location.pathname).toBe('/admin/policy');

    fireEvent.click(screen.getByText('그냥 나가기'));

    expect(await screen.findByText('대시보드 화면')).toBeInTheDocument();
    expect(router.state.location.pathname).toBe('/dashboard');
  });

  it('저장 안 한 변경이 있으면 새로고침·탭 닫기에 브라우저 경고를 건다', () => {
    renderHarness(true);

    const event = new Event('beforeunload', { cancelable: true });
    window.dispatchEvent(event);

    expect(event.defaultPrevented).toBe(true);
  });

  it('저장할 게 없으면 새로고침을 막지 않는다', () => {
    renderHarness(false);

    const event = new Event('beforeunload', { cancelable: true });
    window.dispatchEvent(event);

    expect(event.defaultPrevented).toBe(false);
  });

  it('저장을 마쳐 dirty가 풀리면 가드가 걷힌다', async () => {
    const { router } = renderHarness(true);

    fireEvent.click(screen.getByText('저장 완료'));
    fireEvent.click(screen.getByText('대시보드'));

    expect(await screen.findByText('대시보드 화면')).toBeInTheDocument();
    expect(router.state.location.pathname).toBe('/dashboard');
  });

  /**
   * 이 검증이 이 파일에서 가장 중요하다.
   *
   * <p>{@code useBlocker}는 <b>replace 이동과 리다이렉트까지</b> 붙잡는다. 그런데 이 앱의
   * {@code RequireAuth}는 로그인 만료·온보딩 미완료·권한 강등을 렌더 중 {@code <Navigate replace>}로
   * 처리한다. 그게 붙잡히면 <b>정책 설정을 만지다 세션이 끊긴 사용자가 로그인 화면으로 못 가고
   * "저장하지 않고 나가시겠습니까?"에 갇힌다</b> — 저장할 서버도 이미 없는데.
   *
   * <p>갇히지 않는 이유는 트리 구조다. {@code RequireAuth}가 <b>페이지의 부모</b>라서,
   * 자식 대신 {@code <Navigate>}를 반환하는 순간 페이지가 먼저 언마운트되고 그때 blocker가
   * 등록 해제된다. 이동은 그 뒤 effect에서 일어난다. <b>말이 되는 이유지만 말로 끝낼 수는 없어서</b>
   * 같은 모양을 세워 확인한다 — 나중에 가드를 페이지 안쪽으로 옮기면 여기서 깨진다.
   */
  it('부모가 리다이렉트로 바뀌면 붙잡지 않는다 — 로그인 만료가 확인 창에 갇히면 안 된다', async () => {
    let expireSession = () => {};

    function AuthGate() {
      const [signedIn, setSignedIn] = useState(true);
      expireSession = () => setSignedIn(false);
      // RequireAuth와 같은 모양 — 자식 대신 Navigate를 반환한다
      return signedIn ? <Harness initialDirty /> : <Navigate to="/login" replace />;
    }

    const router = createMemoryRouter(
      [
        { path: '/admin/policy', element: <AuthGate /> },
        { path: '/dashboard', element: <div>대시보드 화면</div> },
        { path: '/login', element: <div>로그인 화면</div> },
      ],
      { initialEntries: ['/admin/policy'] },
    );
    render(<RouterProvider router={router} />);

    // 먼저 가드가 **실제로 붙잡는 상태**임을 확인한다. blocked가 false인 것만 보면
    // 가드가 통째로 죽어 있어도 아래가 통과해 버린다
    fireEvent.click(screen.getByText('대시보드'));
    expect(blocked()).toBe('true');
    fireEvent.click(screen.getByText('취소'));
    expect(router.state.location.pathname).toBe('/admin/policy');

    act(() => expireSession());

    expect(await screen.findByText('로그인 화면')).toBeInTheDocument();
    expect(router.state.location.pathname).toBe('/login');
  });
});
