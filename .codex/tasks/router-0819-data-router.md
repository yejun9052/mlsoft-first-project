# 과제: 라우터를 데이터 라우터로 바꾸고 이탈 가드를 `useBlocker`로 교체

프론트엔드만 건드린다. 백엔드는 전혀 관련 없다.

## 왜 하는가

`AdminPolicyPage`(연차 정책)와 `MyInfoPage`(내 정보)에는 **저장 버튼을 따로 눌러야 반영되는 폼**이
있고, 저장하지 않고 나가면 값이 조용히 사라진다. 2026-08-19에 그걸 붙잡는 훅
`src/hooks/useUnsavedGuard.js`를 만들어 두 화면에 붙였다.

그 훅은 지금 **document의 캡처 단계에서 링크 클릭을 가로채는** 방식이다. 앱이
`BrowserRouter`(선언형 라우터)라 react-router의 정식 수단인 `useBlocker`를 쓸 수 없었기 때문이다.
그래서 **브라우저 뒤로가기를 못 막는다.**

이 과제는 라우터를 `createBrowserRouter`(데이터 라우터)로 바꿔 `useBlocker`를 쓰게 하는 것이다.
그러면 뒤로가기가 덮이고, **동시에 링크 클릭 판별 코드가 전부 사라진다** —
새 탭 여부·수정키·외부 링크·같은 화면 여부를 직접 가려내던 로직은 라우터가 대신 판단한다.
이 과제의 성패는 "뒤로가기가 막히는가"와 **"코드가 줄었는가"** 둘 다로 본다.

## 먼저 읽을 것

- `src/main.jsx` — 라우터 마운트 지점. `Toaster`가 `BrowserRouter` 안에 있다
- `src/App.jsx` — 라우트 트리 전체
- `src/components/RequireAuth.jsx` — **주석까지 반드시 읽을 것.** 리뷰 이력이 세 건 붙어 있고
  그 조건들이 이 과제의 최대 위험 지점이다
- `src/hooks/useUnsavedGuard.js` — 교체 대상. **JSDoc에 왜 지금 방식인지가 적혀 있다**
- `src/hooks/useUnsavedGuard.test.jsx` — 현재 명세 12건
- `src/pages/MyInfoPage.jsx` / `src/pages/MyInfoPage.test.jsx` — 훅 사용처 + 테스트 7건
- `src/pages/AdminPolicyPage.jsx` — 훅 사용처 (테스트 없음)
- `src/components/layout/Sidebar.test.jsx`, `src/components/RequireAuth.test.jsx`,
  `src/pages/ApprovalsPage.test.jsx`, `src/pages/OnboardingPage.test.jsx`
  — 라우터를 마운트하는 나머지 테스트. **깨지는지 판단해서 알려라**
- `CLAUDE.md`의 "프론트엔드 데이터 흐름"·"코드 컨벤션" 절
- `package.json` — react-router-dom 7.18.1, react 19.2.7, vitest 4.1.10

## 해야 할 일

### 1. 라우터 교체

`App.jsx`의 라우트 트리를 `createRoutesFromElements`로 그대로 재사용한다.
**라우트 트리를 다시 쓰지 말 것** — 지금 JSX가 그대로 들어간다.

- `main.jsx`가 `createBrowserRouter` + `RouterProvider`를 쓰게 한다
- `RouterProvider`는 children을 받지 않는다. `Toaster`를 어디에 둘지 **정하고 근거를 써라**
- `QueryClientProvider`와의 중첩 순서를 유지할 것

### 2. `useUnsavedGuard`를 `useBlocker`로 교체

- 링크 클릭 가로채기(document 캡처, 수정키·`target`·origin·pathname 판별)를 **전부 제거**한다
- `beforeunload`는 **그대로 둔다** — 새로고침·탭 닫기는 라우터 밖의 일이라 `useBlocker`가 못 본다
- 훅의 외부 계약 `{ blocked, leave, stay }`를 **유지**한다.
  두 페이지가 이미 이 이름으로 쓰고 있고, 이 과제에서 화면 코드를 바꾸지 않는 것이 목표다.
  유지가 불가능하다면 왜인지 쓰고 바뀐 계약과 두 페이지의 수정본을 함께 내라
- JSDoc을 다시 쓴다. **"뒤로가기를 막지 않는다"와 그 이유를 적어 둔 블록은 이제 거짓이다** —
  지우고, 대신 왜 데이터 라우터로 바꿨는지를 남겨라

### 3. 테스트

- `useUnsavedGuard.test.jsx`의 12건 중 **의미가 사라지는 것**(외부 링크·새 탭·수정키·같은 화면)은
  지운다. 그건 이제 라우터의 책임이지 이 훅의 책임이 아니다. **다만 지운 이유를 결정 표에 쓸 것**
- **뒤로가기를 막는다는 검증을 새로 넣는다.** 이게 이 과제의 핵심 산출물이다
- `MyInfoPage.test.jsx` 7건은 **전부 살아남아야 한다.** 하네스만 데이터 라우터로 바꾼다.
  특히 `renderBeforeLoad`(서버 응답 전)와 "저장값이 낡았으면" 두 건은 훅과 무관한
  잠재 결함을 잡는 것이라 내용을 바꾸지 말 것
- 나머지 4개 테스트 파일이 깨지는지 판단하고, 깨진다면 수정본을 내라

## 지켜야 할 규칙

- **화살표 함수 컴포넌트 금지** — `export default function Name({ props })`
- 주석·문서는 **한국어**. 무엇을 하는지가 아니라 **왜 그렇게 했는지**를 쓴다
- 기존 주석 문체를 따를 것 (이 프로젝트는 결정의 근거를 주석에 남긴다)
- `RequireAuth`의 동작을 **바꾸지 말 것.** 라우터만 갈아 끼운다

## 반드시 답해야 할 것 (결정 표에 포함)

1. `RequireAuth`가 렌더 중 `<Navigate>`로 리다이렉트한다. 데이터 라우터에서 **동작이 같은가?**
   다르다면 무엇이 다르고 어떻게 맞췄는가
2. `roles`가 걸린 라우트에서 서버 확인 전 `return null`로 공백을 그리는 부분
   (`RequireAuth.jsx` 주석 참고). 데이터 라우터에서도 같은가?
3. `Toaster`를 어디에 뒀고 왜인가
4. `useBlocker`가 **`replace: true` 이동과 리다이렉트도 막는가?**
   막는다면 `RequireAuth`의 `<Navigate replace>`가 미저장 상태에서 걸려
   **로그인 만료·권한 강등 리다이렉트가 확인 창에 갇히는 사고**가 나지 않는가.
   난다면 어떻게 막았는가 — **이게 이 과제에서 가장 위험한 지점이다**
5. `createRoutesFromElements`가 JSX 주석(`{/* ... */}`)이 섞인 트리를 그대로 받는가

## 산출물

역할 지시서의 형식을 따른다. 파일은 **변경 후 전문**으로.

`## 반영 순서`에 다음 확인 명령을 포함할 것:

```
npm test            # 전부 통과해야 한다 (현재 176건)
npm run lint        # StatusBadge 경고 1건 외에 새 경고가 없어야 한다
npm run build       # 빌드가 되어야 한다
```

`## 되돌릴 수 없는 것`에는 **수동으로 확인해야 하는 회귀 목록**을 적어라 —
자동 테스트가 못 보는 것 중 라우터 교체로 깨질 수 있는 것 (로그인 리다이렉트, 온보딩 강제 이동,
역할 게이트, SPA 딥링크, 새로고침 후 경로 유지 등).
