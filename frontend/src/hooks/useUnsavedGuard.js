import { useCallback, useEffect } from 'react';
import { useBlocker } from 'react-router-dom';

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
 *   <li><b>앱 안 이동</b> — 데이터 라우터의 {@code useBlocker}. 링크 이동뿐 아니라
 *       브라우저 뒤로가기 같은 history 이동도 같은 경로로 붙잡는다</li>
 *   <li><b>새로고침·탭 닫기</b> — {@code beforeunload}. 라우터 밖의 문서 이동이라
 *       브라우저 기본 경고창을 사용하고 문구는 바꿀 수 없다</li>
 * </ul>
 *
 * <p>이 훅 때문에 앱 라우터를 {@code BrowserRouter}에서 {@code createBrowserRouter}로 바꿨다.
 * 선언형 라우터에서는 {@code useBlocker}를 쓸 수 없어 링크의 수정키·target·origin·현재 경로를
 * document 캡처 단계에서 직접 판별해야 했고, 그래도 뒤로가기는 막지 못했다. 데이터 라우터가
 * 이동 여부를 판단하게 해 그 판별 코드를 없애고 링크와 history 이동을 같은 계약으로 처리한다.
 *
 * @param {boolean} dirty 저장하지 않은 변경이 있는지
 * @returns {{blocked: boolean, leave: Function, stay: Function}}
 *          blocked면 확인 창을 띄우고, leave()는 붙잡은 이동을 계속하며, stay()는 그 이동을 버린다
 */
export function useUnsavedGuard(dirty) {
  const blocker = useBlocker(dirty);

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

  // proceed는 링크 클릭과 POP 이동에 공통으로 원래 이동을 이어 간다.
  const leave = useCallback(() => {
    if (blocker.state === 'blocked') {
      blocker.proceed();
    }
  }, [blocker]);

  // reset 뒤에도 dirty는 그대로라 다음 이동 역시 다시 붙잡힌다.
  const stay = useCallback(() => {
    if (blocker.state === 'blocked') {
      blocker.reset();
    }
  }, [blocker]);

  return {
    blocked: blocker.state === 'blocked',
    leave,
    stay,
  };
}
