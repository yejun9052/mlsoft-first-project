import { afterEach, describe, expect, it, vi } from 'vitest';
import { render, screen } from '@testing-library/react';

import ResponsiveTable from './ResponsiveTable.jsx';

/**
 * 이 컴포넌트는 <b>나머지 14개 화면이 올라탈 토대</b>라 두 방향을 모두 못박는다.
 *
 * <p>모바일에서 카드가 나오는 것만 검증하면, 구현이 <b>항상</b> 카드를 그려도 통과한다 —
 * 그러면 "데스크톱 모습을 바꾸지 않는다"는 이번 작업의 전제가 조용히 깨진다.
 * 실제로 뮤테이션에서 그 구멍이 드러나 이 파일을 만들었다 (2026-08-20).
 */
const REAL_MATCH_MEDIA = window.matchMedia;

function setViewport(isMobile) {
  window.matchMedia = vi.fn().mockImplementation((query) => ({
    matches: isMobile,
    media: query,
    addEventListener: vi.fn(),
    removeEventListener: vi.fn(),
  }));
}

function renderTable() {
  return render(
    <ResponsiveTable
      table={<div>표 본문</div>}
      cards={<div>카드 본문</div>}
    />,
  );
}

describe('ResponsiveTable', () => {
  afterEach(() => {
    window.matchMedia = REAL_MATCH_MEDIA;
    vi.restoreAllMocks();
  });

  it('좁은 화면에서는 카드만 그린다', () => {
    setViewport(true);

    renderTable();

    expect(screen.getByText('카드 본문')).toBeInTheDocument();
    // 숨기는 게 아니라 아예 그리지 않는다 — 보이지 않는 복제본이 스크린리더에 남으면 안 된다
    expect(screen.queryByText('표 본문')).not.toBeInTheDocument();
  });

  it('넓은 화면에서는 표만 그린다 — 데스크톱 모습이 바뀌면 안 된다', () => {
    setViewport(false);

    renderTable();

    expect(screen.getByText('표 본문')).toBeInTheDocument();
    expect(screen.queryByText('카드 본문')).not.toBeInTheDocument();
  });

  // jsdom에는 matchMedia가 없다. 기존 187건은 뷰포트를 지정하지 않으므로,
  // 없을 때 모바일로 기울면 그 테스트들이 통째로 카드를 보게 된다.
  it('matchMedia가 없는 환경은 데스크톱으로 본다', () => {
    delete window.matchMedia;

    renderTable();

    expect(screen.getByText('표 본문')).toBeInTheDocument();
    expect(screen.queryByText('카드 본문')).not.toBeInTheDocument();
  });
});
