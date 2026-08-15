import { describe, expect, it } from 'vitest';
import {
  buildDepartmentMoveBody,
  canDropDepartment,
  resolveDropParentId,
} from './departmentDrop.js';

// 계층 예시 — 개발본부(루트) 아래 개발팀, 그리고 독립 루트인 미배정·디자인본부
const 개발본부 = { id: 1, name: '개발본부', description: '개발', leaderId: 10, parentId: null };
const 개발팀 = { id: 2, name: '개발팀', description: '개발 실무', leaderId: 20, parentId: 1 };
const 미배정 = { id: 3, name: '미배정', description: '소속 없음', leaderId: null, parentId: null };
const 디자인본부 = { id: 4, name: '디자인본부', description: '디자인', leaderId: 40, parentId: null };

const 부서들 = [개발본부, 개발팀, 미배정, 디자인본부];
const hasChildren = (id) => 부서들.some((d) => d.parentId === id);

describe('resolveDropParentId — 어디에 놓으면 누구의 하위가 되는가', () => {
  it('최상위 부서 위에 놓으면 그 부서의 하위가 된다', () => {
    expect(resolveDropParentId(미배정)).toBe(미배정.id);
  });

  it('하위 부서 위에 놓으면 형제가 된다 (그 부서의 상위 밑으로)', () => {
    expect(resolveDropParentId(개발팀)).toBe(개발본부.id);
  });

  it('빈 영역에 놓으면 최상위가 된다', () => {
    expect(resolveDropParentId(null)).toBeNull();
  });
});

describe('canDropDepartment — 2단계를 넘기거나 아무것도 안 바뀌는 이동을 막는다', () => {
  it('루트를 다른 루트 위로 옮길 수 있다 (사용자가 요청한 기본 동작)', () => {
    expect(canDropDepartment(디자인본부, 미배정, hasChildren)).toBe(true);
  });

  it('자기 자신 위에는 놓을 수 없다', () => {
    expect(canDropDepartment(미배정, 미배정, hasChildren)).toBe(false);
  });

  it('자기 자식 위에 놓을 수 없다 — 자기 밑으로 들어가는 순환이 된다', () => {
    // 개발팀의 상위는 개발본부이므로 resolveDropParentId는 개발본부 자신을 가리킨다
    expect(canDropDepartment(개발본부, 개발팀, hasChildren)).toBe(false);
  });

  it('이미 그 부서의 하위면 막는다 — 부를 API가 없다', () => {
    expect(canDropDepartment(개발팀, 개발본부, hasChildren)).toBe(false);
  });

  it('이미 최상위인 부서를 최상위 영역에 놓는 것도 막는다', () => {
    expect(canDropDepartment(미배정, null, hasChildren)).toBe(false);
  });

  /**
   * 이 프로젝트에서 실제로 뚫렸던 방향이다 (1차 테스트 F).
   * "상위가 루트인가"만 보면 이 이동이 통과해 3단계가 만들어진다.
   */
  it('하위 부서를 가진 부서는 다른 부서 아래로 못 간다 — 자식이 손자가 된다', () => {
    expect(canDropDepartment(개발본부, 미배정, hasChildren)).toBe(false);
  });

  it('그런 부서라도 최상위로 빼는 것은 언제나 가능하다', () => {
    const 자식있는하위 = { id: 5, name: '중간', parentId: 3 };
    const 계층 = (id) => id === 5;
    expect(canDropDepartment(자식있는하위, null, 계층)).toBe(true);
  });

  it('끌고 있는 것이 없으면 어디에도 놓을 수 없다', () => {
    expect(canDropDepartment(null, 미배정, hasChildren)).toBe(false);
  });
});

describe('buildDepartmentMoveBody — PUT이 전체 갱신이라 나머지 필드를 실어야 한다', () => {
  /**
   * parentId만 보내면 서버가 이름·설명을 null로 덮고 <b>팀장을 공석으로 만든다</b>.
   * 드래그 한 번에 그 부서의 결재선이 총관리자로 넘어간다는 뜻이라, 이 테스트가 그걸 고정한다.
   */
  it('이름·설명·팀장을 그대로 유지하고 상위만 바꾼다', () => {
    expect(buildDepartmentMoveBody(개발팀, 3)).toEqual({
      id: 2,
      name: '개발팀',
      description: '개발 실무',
      leaderId: 20,
      parentId: 3,
    });
  });

  it('팀장이 공석인 부서는 null을 유지한다 (undefined를 보내면 필드가 빠진다)', () => {
    expect(buildDepartmentMoveBody(미배정, null)).toEqual({
      id: 3,
      name: '미배정',
      description: '소속 없음',
      leaderId: null,
      parentId: null,
    });
  });
});
