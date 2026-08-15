// 부서 드래그 이동 규칙 — 계층은 2단계까지 (docs/02 부서, 1차 테스트 F).
//
// 화면(드래그)과 서버(DepartmentService.validateParent)가 같은 규칙을 지켜야 하는데,
// 규칙을 컴포넌트 안에 두면 드래그 이벤트를 흉내 내지 않고는 검증할 수 없다.
// 그래서 판정만 순수 함수로 떼어 냈다 — 여기가 테스트 대상이고, 컴포넌트는 결과를 그리기만 한다.

/**
 * 끌어온 부서를 대상 위에 놓았을 때 **어느 부서의 하위가 되는가**.
 *
 * - 대상이 최상위 부서면 → 그 부서의 하위로 들어간다
 * - 대상이 이미 하위 부서면 → 그 부서와 **형제**가 된다 (같은 상위 부서 밑)
 *   떨어뜨린 자리에 들어가는 것이 눈에 보이는 대로라, 아무 일도 안 일어나는 것보다 낫다
 * - 대상이 없으면(최상위 영역에 놓음) → 최상위 부서가 된다
 *
 * @returns {number|null} 새 상위 부서 id. null이면 최상위
 */
export function resolveDropParentId(target) {
  if (!target) return null;
  return target.parentId ?? target.id;
}

/**
 * 이 이동이 가능한가.
 *
 * 막는 경우는 셋뿐이고, 전부 "2단계를 넘긴다"거나 "아무것도 안 바뀐다"로 환원된다.
 * 서버도 같은 것을 막지만(400), 끌고 가는 동안 놓을 수 있는 자리인지 보여 주려면 화면에도 필요하다.
 *
 * @param dragged   끌고 있는 부서 { id, parentId }
 * @param target    놓으려는 부서 { id, parentId } — null이면 최상위 영역
 * @param hasChildren (id) => boolean — 그 부서가 하위 부서를 갖고 있는가
 */
export function canDropDepartment(dragged, target, hasChildren) {
  if (!dragged) return false;

  const nextParentId = resolveDropParentId(target);

  // 자기 자신 아래로는 못 들어간다 (자기 자식 위에 놓는 경우도 여기서 걸린다)
  if (nextParentId === dragged.id) return false;

  // 제자리 — API를 부를 이유가 없다
  if (nextParentId === (dragged.parentId ?? null)) return false;

  // 자식을 가진 부서가 내려가면 그 자식이 손자가 된다 (3단계). 최상위로 빼는 것은 언제나 가능하다
  if (nextParentId !== null && hasChildren(dragged.id)) return false;

  return true;
}

/**
 * 서버에 보낼 수정 본문.
 *
 * `PUT /api/departments/{id}`는 **전체 갱신**이라 이름·설명·팀장을 함께 실어야 한다.
 * parentId만 보내면 나머지가 null로 덮여 **팀장이 공석이 된다** — 드래그 한 번에 그 부서의
 * 결재선이 총관리자로 넘어간다는 뜻이라, 이 함수를 거치지 않고 본문을 직접 만들지 말 것.
 */
export function buildDepartmentMoveBody(department, nextParentId) {
  return {
    id: department.id,
    name: department.name,
    description: department.description,
    leaderId: department.leaderId ?? null,
    parentId: nextParentId,
  };
}
