/**
 * 부서 목록을 <b>상위 부서 바로 아래에 그 하위 부서가 오도록</b> 한 줄로 편다.
 *
 * <p>API는 id 순 플랫 목록만 준다. 그대로 깔면 `개발팀 · 디자인팀 · 경영지원팀 · 개발 1팀`처럼
 * 나중에 만든 하위 부서가 부모와 떨어져 맨 뒤에 붙어, 화면만 봐서는 계층을 알 수 없다.
 *
 * <p>부서 관리 목록과 구성원 관리의 부서 선택이 <b>같은 순서</b>를 써야 하므로 여기 한 곳에 둔다.
 * 각자 구현하면 한쪽에서만 순서가 틀어지고, 그 종류의 산재가 리뷰 I-5의 원인이었다.
 *
 * @param {Array<{id:number, parentId:?number}>} departments 부서 목록 (플랫)
 * @returns {Array<object>} 계층 순으로 재배열된 목록. 하위 부서에는 `depth: 1`이 붙는다
 */
export function orderByHierarchy(departments) {
  const list = departments ?? [];
  const ids = new Set(list.map((d) => d.id));

  // 부모가 이 목록에 없는 부서는 고아가 아니라 "여기서는 최상위"로 본다.
  // 활성 부서만 걸러 온 목록에서 상위 부서가 비활성이면 실제로 이렇게 되는데,
  // 루트(parentId == null)만 훑으면 그 부서가 통째로 사라져 선택지에서 없어진다.
  const isRoot = (d) => d.parentId == null || d.parentId === d.id || !ids.has(d.parentId);

  // 계층은 2단계까지다 (서버 DepartmentService.validateParent가 보장). 손자를 따로 훑지 않는다.
  const ordered = list.filter(isRoot).flatMap((root) => [
    { ...root, depth: 0 },
    ...list
      .filter((d) => !isRoot(d) && d.parentId === root.id)
      .map((child) => ({ ...child, depth: 1 })),
  ]);

  // 어떤 이유로든 빠진 부서는 뒤에 붙인다 — 순서가 어색한 편이 선택지에서 조용히 사라지는 것보다 낫다.
  const placed = new Set(ordered.map((d) => d.id));
  return [...ordered, ...list.filter((d) => !placed.has(d.id)).map((d) => ({ ...d, depth: 0 }))];
}

/**
 * 선택지 한 줄의 표시 이름 — 하위 부서는 들여쓰고 `↳`를 붙인다 (부서 관리 목록과 같은 기호).
 *
 * <p>들여쓰기를 일반 공백이 아니라 ` `로 하는 이유: `<option>`의 앞뒤 공백은 브라우저가
 * 지워 버려 일반 공백으로는 들여쓰기가 보이지 않는다. `<option>`은 CSS로 여백을 줄 수도 없다.
 */
export function departmentOptionLabel(department) {
  return department.depth ? `  ↳ ${department.name}` : department.name;
}
