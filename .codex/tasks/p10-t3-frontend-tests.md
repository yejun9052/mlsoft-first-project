# T-3 프론트 테스트 보강

프론트 테스트가 **54개인데 대부분 api 레이어**다. 2026-08-10에 컴포넌트 테스트 11건
(`RequireAuth.test.jsx` 6 · `Sidebar.test.jsx` 5)이 처음 들어갔다. 그 패턴을 이어
**페이지 렌더·권한 분기·상태 전이** 테스트를 만들어라.

## 인프라 (이미 갖춰져 있다 — 새로 깔지 말 것)

- `vitest` + `jsdom` + `@testing-library/react` + `@testing-library/jest-dom`
- 설정: `frontend/vite.config.js`의 `test` 블록, `src/setupTests.js`
- **참고할 기존 테스트 2개를 반드시 먼저 읽어라** — 렌더 헬퍼·mock 방식·한국어 DisplayName 문체를
  그대로 따라야 한다:
  - `frontend/src/components/RequireAuth.test.jsx`
  - `frontend/src/components/layout/Sidebar.test.jsx`

핵심 패턴:
- `vi.mock('../../api/xxx.js', () => ({ ... }))` 로 **api 모듈**을 막는다 (axios가 아니라 api 함수)
- `QueryClient`는 테스트마다 새로 만들고 `defaultOptions: { queries: { retry: false } }`
- `MemoryRouter`로 감싼다
- 단정은 `waitFor`로 — 이 앱은 `useCurrentUser`가 initialData 후 재검증하므로 첫 렌더 뒤 상태가 바뀐다

## 만들 것 (우선순위 순)

### 1. `ApprovalsPage` — 결재 화면 (가장 값이 크다)
- 대기 탭이 **연차 대기 + 복리후생 대기를 병합**해 보여준다 (`pendingList`)
- 건 종류별로 처리 엔드포인트가 갈린다: `CANCEL` → cancel-approval, `WELFARE` → welfare 승인,
  그 외 → approval. **이 분기가 틀리면 잘못된 API가 호출된다** — 종류별로 올바른 뮤테이션이
  불리는지 검증해라
- 승인/반려 확인 다이얼로그가 뜨고, 확인 전에는 API가 불리지 않는다
- 처리 완료 탭은 `my-actions` 스코프를 쓴다 (`my-approvals`가 아니다 — 리뷰 S-6)

### 2. `AdminHistoryPage` — 처리 이력
- 탭 3종(연차·복리후생·관리자 조작). **"관리자 조작" 탭은 SYSTEM_ADMIN에게만 보인다**
- 탭을 바꾸면 액션 필터가 초기화된다 (탭마다 액션 종류가 달라 그대로 들고 가면 400)
- 감사 로그 탭의 필터 칩은 **서버가 준 목록**(`/admin/audit-logs/actions`)에서 온다
- 팀장 부제가 "내가 결재자로 지정된 …"이다 (부서 기준이 아니다)

### 3. `AdminWelfarePoliciesPage` — 2026-08-10 신설, 테스트 0개
- 정책 추가·수정 모달이 생성/수정을 공유한다 (editing == null이면 생성)
- 필수 필드 검증: 구분·부여일수·제출자료·설명. 부여일수 범위 0~365
- `defaultDays`를 `toFixed(1)`로 보낸다 (서버가 DECIMAL(4,1))
- 비활성화는 확인 다이얼로그를 거친다

### 4. `useLeaves` / `useWelfare` 훅의 invalidate 규칙
- 복리후생 승인은 **연차 잔액을 바꾼다**(bonus_days 가산) → `['leaves']`까지 무효화해야 한다 (리뷰 F-5).
  이게 빠지면 대시보드 잔여 연차가 낡는다. 뮤테이션별로 어떤 키가 무효화되는지 검증해라
- 쿼리 키에 `size`가 들어간다 (리뷰 F-1) — 크기가 다른 호출이 캐시를 공유하지 않는지

## 출력 형식

`AGENTS.md` 절대 규칙 4에 따라 **파일 경로 + 완성된 전체 코드**를 내라.
"여기를 이렇게 바꾸세요" 서술은 안 된다. 파일마다:

```
### frontend/src/pages/ApprovalsPage.test.jsx
<완성된 전체 파일 내용>
```

## 지키지 말아야 할 것

- 스냅샷 테스트 금지 — 이 프로젝트에 없는 방식이고, 깨졌을 때 무엇이 왜 틀렸는지 알 수 없다
- 구현 세부(클래스명·DOM 구조)에 묶인 단정 금지. `getByText`/`getByRole` 같은
  **사용자가 보는 것** 기준으로 찾아라
- 테스트를 위해 프로덕션 코드에 `data-testid`를 새로 뿌리지 말 것.
  꼭 필요하면 그 이유를 따로 적어 제안만 하고, 테스트는 다른 방법으로 써라
- 한 파일에 20개씩 몰아넣지 말 것. **각 테스트가 깨졌을 때 무엇이 고장났는지 이름만 보고
  알 수 있어야** 한다 (기존 2개 파일의 DisplayName 문체 참고)
