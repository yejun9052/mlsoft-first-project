import { describe, expect, it } from 'vitest';

import { paginate } from './paginate.js';

const items = (n) => Array.from({ length: n }, (_, i) => i + 1);

describe('paginate', () => {
  it('요청한 페이지 분량만 잘라 준다', () => {
    const result = paginate(items(25), 1, 10);

    expect(result.rows).toEqual([11, 12, 13, 14, 15, 16, 17, 18, 19, 20]);
    expect(result.page).toBe(1);
  });

  it('마지막 페이지는 남은 만큼만 담는다', () => {
    const result = paginate(items(25), 2, 10);

    expect(result.rows).toEqual([21, 22, 23, 24, 25]);
    expect(result.totalPages).toBe(3);
    expect(result.totalElements).toBe(25);
  });

  it('필터가 좁아져 페이지가 사라지면 마지막 페이지로 당긴다', () => {
    // 5페이지를 보던 중 필터를 걸어 12건만 남은 상황. 클램프하지 않으면 빈 표가 뜨고
    // 사용자는 "조건에 맞는 게 없다"로 읽는다.
    const result = paginate(items(12), 4, 10);

    expect(result.page).toBe(1);
    expect(result.rows).toEqual([11, 12]);
  });

  it('목록이 비면 1페이지로 두고 빈 배열을 준다', () => {
    const result = paginate([], 3, 10);

    expect(result.rows).toEqual([]);
    expect(result.page).toBe(0);
    expect(result.totalPages).toBe(1);
    expect(result.totalElements).toBe(0);
  });

  it('한 페이지에 다 들어가면 totalPages가 1이다 — 페이지바가 그려지지 않는 조건', () => {
    expect(paginate(items(7), 0, 10).totalPages).toBe(1);
  });

  it('음수 페이지는 첫 페이지로 본다', () => {
    expect(paginate(items(25), -1, 10).page).toBe(0);
  });

  it('원본 배열을 건드리지 않는다', () => {
    const input = items(25);

    paginate(input, 1, 10);

    expect(input).toEqual(items(25));
  });

  it('없는 값을 견딘다', () => {
    expect(paginate(undefined, 0, 10).rows).toEqual([]);
  });
});
