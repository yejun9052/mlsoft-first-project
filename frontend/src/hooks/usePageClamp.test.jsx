import { renderHook } from '@testing-library/react';
import { describe, expect, it, vi } from 'vitest';

import { usePageClamp } from './usePageClamp.js';

describe('usePageClamp', () => {
  it('총 페이지가 줄면 마지막 페이지로 당긴다', () => {
    // 재직자 11명에서 2페이지(page=1)의 유일한 사원을 퇴직 처리 → 10명 → totalPages=1
    const setPage = vi.fn();

    renderHook(() => usePageClamp(1, setPage, 1));

    expect(setPage).toHaveBeenCalledWith(0);
  });

  it('범위 안이면 건드리지 않는다', () => {
    const setPage = vi.fn();

    renderHook(() => usePageClamp(1, setPage, 3));

    expect(setPage).not.toHaveBeenCalled();
  });

  it('목록이 통째로 비면(totalPages 0) 첫 페이지로 둔다', () => {
    const setPage = vi.fn();

    renderHook(() => usePageClamp(2, setPage, 0));

    expect(setPage).toHaveBeenCalledWith(0);
  });

  it('조회 전에는 손대지 않는다 — 첫 응답 전에 페이지가 튀면 안 된다', () => {
    const setPage = vi.fn();

    renderHook(() => usePageClamp(2, setPage, undefined));

    expect(setPage).not.toHaveBeenCalled();
  });

  // null을 따로 보는 이유: `2 >= undefined`는 거짓이라 가드가 없어도 지나가지만,
  // `2 >= null`은 null이 0으로 강제 변환돼 **참**이 된다. 가드를 지우면 여기서만 터진다.
  it('totalPages가 null이어도 손대지 않는다', () => {
    const setPage = vi.fn();

    renderHook(() => usePageClamp(2, setPage, null));

    expect(setPage).not.toHaveBeenCalled();
  });

  it('응답이 도착한 뒤에야 당긴다', () => {
    const setPage = vi.fn();
    const { rerender } = renderHook(({ total }) => usePageClamp(3, setPage, total), {
      initialProps: { total: undefined },
    });
    expect(setPage).not.toHaveBeenCalled();

    rerender({ total: 2 });

    expect(setPage).toHaveBeenCalledExactlyOnceWith(1);
  });
});
