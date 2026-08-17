import { useEffect } from 'react';

/**
 * 서버 페이지네이션의 안전망 — <b>총 페이지가 줄어들면 현재 페이지를 마지막으로 당긴다.</b>
 *
 * <p>목록이 줄어드는 일은 사용자가 만든다. 2페이지의 마지막 한 명을 퇴직 처리하면 총원이
 * 10명이 되어 페이지가 하나뿐인데, 화면은 여전히 2페이지를 요청한다. 서버는 <b>빈 목록</b>을
 * 정상 응답으로 돌려주고, 화면은 그걸 "조회된 구성원이 없습니다"로 보여준다 —
 * 실제로는 10명이 그대로 있는데도.
 *
 * <p>화면에서 끊는 표는 {@code utils/paginate.js}가 같은 일을 한다. 그쪽은 배열을 손에 들고
 * 있어 잘라내는 순간 바로잡을 수 있지만, 서버에서 끊는 표는 응답이 와야 총 페이지를 알 수 있어
 * 이렇게 뒤늦게 바로잡는다.
 *
 * <p>뮤테이션 쪽에서 미리 페이지를 내려 두는 화면이 있어도(개인 일정 표) 이 훅은 그대로 둔다 —
 * 그건 깜박임을 없애는 빠른 길이고, 이건 어느 경로로 줄어들든 걸리는 그물이다.
 *
 * @param {number} page 현재 페이지 (0-base)
 * @param {Function} setPage 페이지 상태 setter
 * @param {number|undefined} totalPages 서버가 준 총 페이지 수. 조회 전이면 undefined
 */
export function usePageClamp(page, setPage, totalPages) {
  useEffect(() => {
    // 조회 전(undefined)에는 손대지 않는다 — 0으로 되돌리면 첫 응답이 오기 전에 페이지가 튄다
    if (totalPages == null) return;
    if (page >= totalPages) {
      // 목록이 통째로 비면 totalPages가 0이다. 그때도 페이지는 0이어야 한다
      setPage(Math.max(0, totalPages - 1));
    }
  }, [page, setPage, totalPages]);
}
