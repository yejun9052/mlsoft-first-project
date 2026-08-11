# T-3 프론트 테스트 감사 — "통과하는 테스트"가 아니라 "의존하는 테스트"인지

미커밋 변경분은 테스트 파일 2개다.

- `frontend/src/components/RequireAuth.test.jsx` (7건)
- `frontend/src/components/layout/Sidebar.test.jsx` (5건)

둘 다 현재 **통과한다**. 통과 여부는 이미 확인했으니 보지 말고, 아래 세 가지만 답하라.
대응 구현은 `frontend/src/components/RequireAuth.jsx`, `frontend/src/components/layout/Sidebar.jsx`,
`frontend/src/hooks/useCurrentUser.js`(권한 단일 출처)다.

## 1. 위양성 — 이름과 다른 이유로 통과하는 테스트

각 `it(...)` 제목이 주장하는 조건을 그 테스트가 **실제로** 검증하는지 하나씩 대조하라.
이 프로젝트에는 실제 전례가 있다: `renderGuard({ roles = ADMIN_ROLES })`의 기본 인수 때문에
`undefined`를 넘긴 "온보딩 미완료" 테스트가 관리자 역할 제한이 걸린 채 돌고 있었고,
그런데도 통과했다 — 온보딩 리다이렉트가 역할 검사보다 **먼저** 일어나기 때문이다.
같은 종류(단언에 도달하기 전에 다른 분기가 결과를 만들어 내는 것)를 전부 찾아라.

특히 확인할 것:
- `roles={null}`을 넘긴 테스트가 정말 "역할 제한 없음" 경로를 타는가
- 리다이렉트를 기대하는 테스트가, 원인이 되는 조건을 **바꿨을 때만** 그 결과가 나오는가
  (예: 강등 테스트가 사실은 "온보딩" 또는 "미인증" 경로로 대시보드에 도달하는 것은 아닌가)
- `waitFor`의 단언이 실제 상태 전이를 기다리는가, 아니면 초기 렌더에서 이미 참인가
- Sidebar의 `screen.getByText('5')` 같은 단언이 배지가 아닌 다른 DOM 텍스트에 걸릴 여지가 있는가

## 2. 가드를 떼면 깨지는가 (핵심)

아래 각 가드를 **주석 처리했다고 가정**하고, 어느 테스트가 실패하는지 지목하라.
어떤 가드에 대해 "실패하는 테스트가 없다"면 그 가드는 지금 검증되지 않고 있는 것이다 — 명시하라.

- `RequireAuth.jsx`의 `if (roles && !serverConfirmed)` 대기 블록
  (서버 확인 전 관리자 화면 렌더 차단 — Codex가 2026-08-10에 지적한 항목)
- `RequireAuth.jsx`의 `if (roles && !roles.includes(userInfo.role))` 역할 검사
- `RequireAuth.jsx`의 온보딩 리다이렉트
- `useCurrentUser`의 `initialDataUpdatedAt: 0`
  (이게 없으면 `initialData` + `staleTime` 조합으로 **재검증이 아예 안 돈다**)
- `useCurrentUser`가 서버 응답을 localStorage에 반영하는 부분
- Sidebar 결재 쿼리의 `enabled`(사원에게 결재 API를 호출하지 않게 하는 것)

## 3. 빠진 것

리뷰 F-7·F-8이 고친 동작 중 **두 파일 어디에서도 검증되지 않는 것**이 있으면 지목하라.
없으면 "없음"이라고 답하라. 커버리지를 늘리려고 테스트를 발명하지는 마라.

## 산출물

- 수정이 필요하면 **해당 테스트 블록의 교체 코드 전문**을 내라 (파일 전체 말고 `it(...)` 단위).
- 파일을 직접 고치지 마라. 반영은 Claude Code가 한다.
- 문제가 없으면 "수정 없음"으로 명확히 답하라. 억지로 지적을 만들지 마라.
