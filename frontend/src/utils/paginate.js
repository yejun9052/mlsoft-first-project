/**
 * 이미 손에 들고 있는 배열을 화면에서 끊어 보여 준다 — <b>클라이언트 사이드</b> 페이지네이션.
 *
 * <p>서버 페이지네이션(`Pagination` + 훅의 page·size)을 쓸 수 없는 표에만 쓴다. 두 종류가 있다:
 * <ul>
 *   <li><b>화면에서 거르는 표</b>(내 연차 내역의 연도·종류·상태 칩) — 서버가 10건씩 끊어 주면
 *       필터가 그 10건 안에서만 걸려, 조건에 맞는 건이 뒤 페이지에 있어도 "없음"이 뜬다</li>
 *   <li><b>두 목록을 합쳐 정렬하는 표</b>(결재 관리의 연차+복리후생) — 각각 페이지로 받아
 *       합치면 페이지 경계가 서로 어긋나 정렬이 깨진다</li>
 * </ul>
 *
 * <p>둘 다 <b>서버에서 받는 총량 자체는 그대로다</b>(상한 100·50건). 이 함수는 화면에 한 번에
 * 그리는 양만 줄인다 — 그 상한을 늘리거나 없애려면 서버 쪽 병합·필터가 필요하다.
 *
 * @param {Array} items 이미 거르고 정렬까지 끝난 목록
 * @param {number} page  0-base 페이지 번호 (Spring Page와 같은 기준)
 * @param {number} size  한 페이지 행 수
 */
export function paginate(items, page, size) {
  const list = items ?? [];
  const totalElements = list.length;
  const totalPages = Math.max(1, Math.ceil(totalElements / size));

  // 필터를 좁히면 총 페이지가 줄어드는데 page는 그대로다. 클램프하지 않으면 있지도 않은
  // 5페이지를 잘라 **빈 표**가 뜨고, 사용자는 조건에 맞는 게 없다고 읽는다.
  // (호출부에서 필터가 바뀔 때 page를 0으로 되돌리기도 하지만, 이건 그 그물이다.)
  const safePage = Math.min(Math.max(page, 0), totalPages - 1);

  return {
    rows: list.slice(safePage * size, safePage * size + size),
    page: safePage,
    totalPages,
    totalElements,
  };
}
