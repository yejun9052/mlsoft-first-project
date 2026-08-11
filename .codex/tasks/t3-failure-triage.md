# T-3 초안 테스트 5건 실패 — 실제 결함인가, 초안의 오류인가

`docs/설계-초안/t3-프론트-테스트-초안-2026-08-11.md`의 신규 테스트 5파일을 방금 적용했다.
그 초안은 **한 번도 실행된 적이 없다**(작성 당시 읽기 전용 샌드박스라 테스트를 못 돌렸다).
지금 `npx vitest run` 결과는 **80건 중 5건 실패**다.

판별해야 할 것은 하나다 — 각 실패가

- **(A) 실제 앱 결함**인가 (테스트가 옳고 구현이 틀렸다) → 구현을 고쳐야 한다
- **(B) 초안 테스트의 오류**인가 (구현이 옳고 테스트가 틀렸다) → 테스트를 고쳐야 한다

**둘을 섞지 마라.** (A)를 (B)로 처리하면 결함을 테스트로 덮는 것이 된다.
이 프로젝트는 "통과하는 테스트가 아니라 의존하는 테스트"를 기준으로 삼는다 —
**통과시키려고 단언을 약화시키지 마라.** 특히 `getAllByRole(...)[0]`으로 바꿔
"아무 버튼이나 누르는" 테스트로 만들면 원래 검증 의도가 사라진다.

## 실패 5건

### 1. `src/hooks/useWelfare.test.jsx:134` — 정책 생성 뮤테이션 인자
```
expected "vi.fn()" to be called with arguments: [ { category: '출산', …(4) } ]
Received 1st call: [ {…body…}, { client: QueryClient {}, meta: undefined, mutationKey: undefined } ]
```

### 2. `src/pages/AdminWelfarePoliciesPage.test.jsx` — "부여 일수가 0보다 작으면 저장하지 않는다"
```
TestingLibraryElementError: Unable to find an element with the text:
부여 일수는 0~365일 사이여야 합니다.
```
실제 화면이 어떤 문구로, 어느 시점에 검증하는지 확인하라.
**입력값 검증 자체가 없다면 그건 (A)다** — 화면에 안내 없이 저장이 나가는지 확인할 것.

### 3~5. `src/pages/ApprovalsPage.test.jsx` 3건
```
Found multiple elements with the role "button" and name "승인"   (2건)
Found multiple elements with the role "button" and name "반려"   (1건)
```
테스트 의도는 **결재 종류별로 다른 뮤테이션이 불리는지**다 —
일반 연차 승인 → 연차 approval, `CANCEL_PENDING` 반려 → cancel-approval,
복리후생 승인 → welfare approval. 목록에 여러 건이 렌더되어 버튼이 여러 개인 것이라면,
**의도한 그 행의 버튼을 특정**하는 방식으로 고쳐야 한다(행 컨테이너 스코프 등).
아무 버튼이나 집는 방식은 안 된다 — 종류별로 갈리는지가 이 테스트의 전부다.

## 참고

- 대응 구현: `frontend/src/pages/ApprovalsPage.jsx`, `frontend/src/pages/AdminWelfarePoliciesPage.jsx`,
  `frontend/src/hooks/useWelfare.js`, `frontend/src/hooks/useLeaves.js`
- React Query v5를 쓴다. `mutationFn`의 시그니처를 확인하라.
- 테스트 파일 전문은 이미 위 경로에 적용돼 있다. 초안 문서가 아니라 **적용된 파일**을 읽어라.

## 산출물

실패 5건 각각에 대해:

```
### N. <테스트 이름>
- 판정: (A) 실제 결함 / (B) 초안 오류
- 근거: <코드 어디가 그렇게 만드는지. 파일:줄>
- 조치: (B)면 교체할 `it(...)` 블록 전문. (A)면 구현의 어느 줄을 어떻게 고쳐야 하는지
        + 그 테스트는 그대로 두어야 하는 이유
```

파일을 직접 고치지 마라. 반영은 Claude Code가 한다.
