import { useEffect, useState } from 'react';

// JS가 실제 렌더 트리를 나눠야 할 때만 쓴다.
// CSS로 숨기기만 하면 표와 모바일 카드가 접근성 트리에 함께 남고, 같은 행을 두 번 읽게 된다.
export default function useMediaQuery(query) {
  function getMatches() {
    if (typeof window === 'undefined' || typeof window.matchMedia !== 'function') {
      // jsdom과 SSR은 기존 데스크톱 렌더를 기준으로 삼아 기존 테스트와 첫 렌더를 보존한다.
      return false;
    }
    return window.matchMedia(query).matches;
  }

  // **초기값을 여기서 미리 재는 이유** — 아래 effect가 어차피 다시 재지만, effect는 첫 페인트
  // *뒤에* 돈다. 초기값을 그냥 false로 두면 휴대폰에서 데스크톱 표가 한 프레임 그려졌다가
  // 카드로 바뀌며 화면이 튄다. jsdom에는 그 한 프레임을 관측할 방법이 없어 테스트로 못 막는다 —
  // 그래서 이 줄을 "어차피 effect가 하니까"라며 지우지 말 것 (2026-08-20 뮤테이션에서 확인).
  const [matches, setMatches] = useState(getMatches);

  useEffect(() => {
    if (typeof window.matchMedia !== 'function') return undefined;

    const media = window.matchMedia(query);
    function handleChange(event) {
      setMatches(event.matches);
    }

    setMatches(media.matches);
    media.addEventListener('change', handleChange);
    return () => media.removeEventListener('change', handleChange);
  }, [query]);

  return matches;
}
