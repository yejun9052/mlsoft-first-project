import { act, render, screen, fireEvent } from '@testing-library/react';
import { BrowserRouter, Link } from 'react-router-dom';
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';

import { useUnsavedGuard } from './useUnsavedGuard.js';

// jsdom은 실제 문서 이동을 구현하지 않아 외부 링크 클릭마다 "Not implemented: navigation"을 뱉는다.
// **버블 단계**에서 기본 동작만 죽인다 — 가드는 캡처 단계라 이미 지나간 뒤이고,
// react-router Link의 핸들러(#root 버블)도 이보다 먼저 돈다. 검증 대상은 그대로 둔 채 소음만 없앤다.
function swallowNavigation(e) {
  e.preventDefault();
}

function Harness({ dirty, onSideEffect = () => {} }) {
  const guard = useUnsavedGuard(dirty);
  return (
    <>
      <Link to="/dashboard">대시보드</Link>
      <Link to="/team" onClick={onSideEffect}>
        부수효과 링크
      </Link>
      <a href="/team" target="_blank" rel="noreferrer">
        새 탭
      </a>
      <a href="https://example.com/docs">외부 문서</a>
      <a href="/admin/policy">현재 화면</a>
      <span data-testid="blocked">{String(guard.blocked)}</span>
      <button type="button" onClick={guard.leave}>
        그냥 나가기
      </button>
      <button type="button" onClick={guard.stay}>
        취소
      </button>
    </>
  );
}

function renderHarness(dirty, props = {}) {
  window.history.pushState({}, '', '/admin/policy');
  return render(
    <BrowserRouter>
      <Harness dirty={dirty} {...props} />
    </BrowserRouter>,
  );
}

const blocked = () => screen.getByTestId('blocked').textContent;

describe('useUnsavedGuard', () => {
  beforeEach(() => document.addEventListener('click', swallowNavigation));
  afterEach(() => document.removeEventListener('click', swallowNavigation));

  it('저장할 게 없으면 링크 클릭이 그대로 이동한다', () => {
    renderHarness(false);

    fireEvent.click(screen.getByText('대시보드'));

    expect(blocked()).toBe('false');
    expect(window.location.pathname).toBe('/dashboard');
  });

  it('저장 안 한 변경이 있으면 링크 클릭을 붙잡고 이동하지 않는다', () => {
    renderHarness(true);

    fireEvent.click(screen.getByText('대시보드'));

    expect(blocked()).toBe('true');
    expect(window.location.pathname).toBe('/admin/policy');
  });

  // preventDefault가 빠지면 **브라우저 기본 동작인 문서 이동**이 그대로 일어난다 —
  // SPA 이동이 아니라 전체 페이지 언로드라 화면째 날아간다. jsdom은 그 이동을 구현하지 않아
  // 눈에 보이는 흔적이 없으므로, 이벤트가 취소됐는지를 직접 본다
  // (dispatchEvent는 preventDefault가 호출됐을 때만 false를 돌려준다).
  it('붙잡은 클릭은 기본 이동까지 취소한다', () => {
    // 이 검증만은 소음 제거 리스너를 뗀다 — 그게 대신 취소해 버리면 가드가 일한 걸로 보인다
    document.removeEventListener('click', swallowNavigation);
    renderHarness(true);

    const event = new MouseEvent('click', { bubbles: true, cancelable: true });
    // fireEvent와 달리 직접 dispatch는 act로 감싸지 않아 상태 반영이 DOM에 늦는다
    let notCanceled;
    act(() => {
      notCanceled = screen.getByText('대시보드').dispatchEvent(event);
    });

    expect(notCanceled).toBe(false);
    expect(blocked()).toBe('true');
  });

  // preventDefault만으로도 react-router Link는 멈춘다(Link가 defaultPrevented를 먼저 본다).
  // stopPropagation이 지키는 건 그 너머다 — 링크에 달린 onClick은 이동과 무관하게 실행되므로,
  // 끊지 않으면 **이동은 막혔는데 부수효과만 일어난 반쪽 상태**가 된다.
  it('붙잡은 클릭은 링크에 달린 onClick도 실행시키지 않는다', () => {
    const onSideEffect = vi.fn();
    renderHarness(true, { onSideEffect });

    fireEvent.click(screen.getByText('부수효과 링크'));

    expect(blocked()).toBe('true');
    expect(onSideEffect).not.toHaveBeenCalled();
  });

  it('그냥 나가기를 누르면 원래 누른 곳으로 보낸다', () => {
    renderHarness(true);
    fireEvent.click(screen.getByText('대시보드'));

    fireEvent.click(screen.getByText('그냥 나가기'));

    expect(blocked()).toBe('false');
    expect(window.location.pathname).toBe('/dashboard');
  });

  it('취소를 누르면 화면에 남고 다음 이동도 다시 붙잡는다', () => {
    renderHarness(true);
    fireEvent.click(screen.getByText('대시보드'));

    fireEvent.click(screen.getByText('취소'));

    expect(blocked()).toBe('false');
    expect(window.location.pathname).toBe('/admin/policy');

    // 저장한 게 아니므로 가드는 계속 살아 있어야 한다 — 한 번 취소하면 풀리는 그물은 그물이 아니다
    fireEvent.click(screen.getByText('대시보드'));
    expect(blocked()).toBe('true');
  });

  it('새 탭으로 여는 클릭은 붙잡지 않는다 — 이 화면을 떠나지 않는다', () => {
    renderHarness(true);

    // target="_blank" 링크
    fireEvent.click(screen.getByText('새 탭'));
    expect(blocked()).toBe('false');

    // Ctrl+클릭 (같은 링크를 새 탭으로 여는 다른 경로)
    fireEvent.click(screen.getByText('대시보드'), { ctrlKey: true });
    expect(blocked()).toBe('false');
  });

  it('외부 링크는 붙잡지 않는다 — 문서 이동이라 beforeunload가 받는다', () => {
    renderHarness(true);

    fireEvent.click(screen.getByText('외부 문서'));

    expect(blocked()).toBe('false');
  });

  it('지금 보고 있는 화면으로 가는 링크는 붙잡지 않는다', () => {
    renderHarness(true);

    fireEvent.click(screen.getByText('현재 화면'));

    expect(blocked()).toBe('false');
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

  it('저장을 마쳐 dirty가 풀리면 가드가 걷힌다', () => {
    const { rerender } = renderHarness(true);

    rerender(
      <BrowserRouter>
        <Harness dirty={false} />
      </BrowserRouter>,
    );
    fireEvent.click(screen.getByText('대시보드'));

    expect(blocked()).toBe('false');
    expect(window.location.pathname).toBe('/dashboard');
  });
});
