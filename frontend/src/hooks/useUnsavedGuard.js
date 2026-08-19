import { useCallback, useEffect, useState } from 'react';
import { useNavigate } from 'react-router-dom';

/**
 * 저장하지 않은 변경이 있을 때 <b>페이지를 떠나려는 순간</b> 붙잡는다.
 *
 * <p>연차 정책 화면의 시스템 설정은 토글·입력을 바꾼 뒤 <b>따로 "설정 저장"을 눌러야</b> 반영된다.
 * 그런데 그 버튼은 카드 맨 아래 오른쪽에 있고, 토글은 눌리는 순간 켜진 것처럼 보인다 —
 * 만든 사람조차 저장 버튼이 있는 줄 몰랐다(2026-08-19 지적). 그대로 메뉴를 옮기면
 * 바꾼 값이 조용히 사라지고, 다음에 들어왔을 때 "껐는데 켜져 있다"로 보인다.
 *
 * <p>막는 곳은 두 군데다.
 * <ul>
 *   <li><b>앱 안 이동</b> — document의 <b>캡처 단계</b>에서 링크 클릭을 가로챈다. React는 리스너를
 *       루트 컨테이너(#root)에 붙이므로 document 캡처가 먼저 돌고, 거기서 전파를 끊으면
 *       react-router의 Link 핸들러가 아예 실행되지 않는다. 사이드바 메뉴가 전부 여기 걸린다</li>
 *   <li><b>새로고침·탭 닫기</b> — {@code beforeunload}. 브라우저 기본 경고창이라 문구는 못 바꾼다</li>
 * </ul>
 *
 * <p><b>브라우저 뒤로가기는 막지 않는다.</b> 이 앱은 {@code BrowserRouter}(선언형 라우터)라
 * {@code useBlocker}를 쓸 수 없고, 대신 흔히 쓰는 "더미 history 항목을 미리 넣어 popstate를
 * 한 번 삼키는" 우회는 <b>저장한 뒤에도 그 항목이 남아 뒤로가기가 한 번 먹통</b>이 된다.
 * 되돌리려면 {@code history.go(-1)}을 또 쏴야 하는데 그건 popstate를 다시 부르는 경합이다.
 * 조용히 고장 난 뒤로가기가 잃은 설정 한 건보다 나쁘다고 보고 덮지 않았다 —
 * 정 필요해지면 라우터를 {@code createBrowserRouter}로 바꾸는 게 정공법이다.
 *
 * @param {boolean} dirty 저장하지 않은 변경이 있는지
 * @returns {{blocked: boolean, leave: Function, stay: Function}}
 *          blocked면 확인 창을 띄우고, leave()는 원래 가려던 곳으로 보내고, stay()는 눌린 이동을 버린다
 */
export function useUnsavedGuard(dirty) {
  const navigate = useNavigate();
  // 눌렸지만 아직 보내지 않은 목적지. null이면 붙잡은 이동이 없다
  const [pendingTo, setPendingTo] = useState(null);

  useEffect(() => {
    if (!dirty) return undefined;

    function onClickCapture(e) {
      // 다른 핸들러가 이미 처리한 클릭은 건드리지 않는다
      if (e.defaultPrevented) return;
      // 새 탭·새 창으로 여는 클릭은 이 페이지를 떠나지 않는다.
      // 가운데 버튼은 여기서 볼 필요가 없다 — 주 버튼이 아니면 `click`이 아니라 `auxclick`이 뜬다
      if (e.metaKey || e.ctrlKey || e.shiftKey || e.altKey) return;

      const anchor = e.target?.closest?.('a[href]');
      if (!anchor) return;
      if (anchor.target && anchor.target !== '_self') return;
      if (anchor.hasAttribute('download')) return;

      const url = new URL(anchor.href, window.location.href);
      // 외부 링크는 여기서 막아도 소용없다 — 페이지 언로드라 beforeunload가 받는다
      if (url.origin !== window.location.origin) return;
      // 같은 화면 안의 앵커 이동(#섹션 등)은 떠나는 게 아니다
      if (url.pathname === window.location.pathname) return;

      e.preventDefault();
      e.stopPropagation();
      setPendingTo(url.pathname + url.search + url.hash);
    }

    document.addEventListener('click', onClickCapture, true);
    return () => document.removeEventListener('click', onClickCapture, true);
  }, [dirty]);

  useEffect(() => {
    if (!dirty) return undefined;

    function onBeforeUnload(e) {
      // preventDefault가 표준이고 returnValue는 옛 브라우저용 — 둘 다 둔다
      e.preventDefault();
      e.returnValue = '';
    }

    window.addEventListener('beforeunload', onBeforeUnload);
    return () => window.removeEventListener('beforeunload', onBeforeUnload);
  }, [dirty]);

  // 그냥 나가기 — 붙잡아 둔 목적지로 직접 보낸다. 클릭을 되살릴 방법이 없어 navigate로 대신한다
  const leave = useCallback(() => {
    if (pendingTo === null) return;
    const to = pendingTo;
    setPendingTo(null);
    navigate(to);
  }, [navigate, pendingTo]);

  // 취소 — 눌린 이동을 버리고 화면에 남는다. dirty는 그대로라 다음 이동도 다시 걸린다
  const stay = useCallback(() => setPendingTo(null), []);

  return { blocked: pendingTo !== null, leave, stay };
}
