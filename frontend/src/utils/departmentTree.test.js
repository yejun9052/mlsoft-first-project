import { describe, expect, it } from 'vitest';

import { departmentOptionLabel, orderByHierarchy } from './departmentTree.js';

const 개발팀 = { id: 1, name: '개발팀', parentId: null };
const 디자인팀 = { id: 2, name: '디자인팀', parentId: null };
const 경영지원팀 = { id: 3, name: '경영지원팀', parentId: null };
const 개발1팀 = { id: 4, name: '개발 1팀', parentId: 1 };
const 개발2팀 = { id: 5, name: '개발 2팀', parentId: 1 };

const names = (list) => list.map((d) => d.name);

describe('orderByHierarchy', () => {
  it('나중에 만든 하위 부서를 상위 부서 바로 아래로 끌어올린다', () => {
    // API가 주는 순서 — 개발 1팀이 id 순이라 맨 뒤에 있다
    const ordered = orderByHierarchy([개발팀, 디자인팀, 경영지원팀, 개발1팀]);

    expect(names(ordered)).toEqual(['개발팀', '개발 1팀', '디자인팀', '경영지원팀']);
  });

  it('하위 부서에만 depth 1을 붙인다', () => {
    const ordered = orderByHierarchy([개발팀, 개발1팀]);

    expect(ordered.map((d) => d.depth)).toEqual([0, 1]);
  });

  it('형제 하위 부서는 원래 순서를 유지한다', () => {
    const ordered = orderByHierarchy([개발팀, 디자인팀, 개발2팀, 개발1팀]);

    expect(names(ordered)).toEqual(['개발팀', '개발 2팀', '개발 1팀', '디자인팀']);
  });

  it('상위 부서가 목록에 없으면 그 부서를 제자리에서 최상위로 취급한다', () => {
    // 활성 부서만 걸러 온 목록에서 상위 부서가 비활성이면 실제로 이렇게 된다.
    //
    // 고아를 목록 <b>앞</b>에 두고 검사하는 것이 핵심이다. 뒤에 두면 맨 끝 안전망이 만드는
    // 결과와 순서가 같아져, 고아 처리를 통째로 지워도 테스트가 통과한다(뮤테이션으로 확인).
    const ordered = orderByHierarchy([개발1팀, 디자인팀]);

    expect(names(ordered)).toEqual(['개발 1팀', '디자인팀']);
    expect(ordered.every((d) => d.depth === 0)).toBe(true);
  });

  it('맨 끝 안전망은 계층에서 빠진 부서만 줍는다 — 정상 목록의 순서를 바꾸지 않는다', () => {
    const ordered = orderByHierarchy([개발1팀, 개발팀, 디자인팀]);

    // 개발 1팀은 부모가 목록에 있으므로 고아가 아니다. 부모 아래로 들어가야 한다
    expect(names(ordered)).toEqual(['개발팀', '개발 1팀', '디자인팀']);
    expect(ordered.map((d) => d.depth)).toEqual([0, 1, 0]);
  });

  it('원본 배열을 건드리지 않는다', () => {
    const input = [개발팀, 개발1팀];

    orderByHierarchy(input);

    expect(input).toEqual([개발팀, 개발1팀]);
    expect(개발1팀.depth).toBeUndefined();
  });

  it('빈 목록과 없는 값을 견딘다', () => {
    expect(orderByHierarchy([])).toEqual([]);
    expect(orderByHierarchy(undefined)).toEqual([]);
  });
});

describe('departmentOptionLabel', () => {
  it('하위 부서는 들여쓰고 ↳를 붙인다', () => {
    expect(departmentOptionLabel({ name: '개발 1팀', depth: 1 })).toBe('  ↳ 개발 1팀');
  });

  it('상위 부서는 이름 그대로 둔다', () => {
    expect(departmentOptionLabel({ name: '개발팀', depth: 0 })).toBe('개발팀');
  });

  it('들여쓰기에 일반 공백을 쓰지 않는다 — option 앞뒤 공백은 브라우저가 지운다', () => {
    const label = departmentOptionLabel({ name: '개발 1팀', depth: 1 });

    expect(label.startsWith(' ')).toBe(false);
    expect(label.trimStart()).toBe('↳ 개발 1팀');
  });
});
