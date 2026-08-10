# T-3 테스트 초안 자체 검수 — 붙이기 전에 틀린 곳을 찾아라

`docs/설계-초안/t3-프론트-테스트-초안-2026-08-11.md`에 프론트 테스트 초안이 있다
(네가 `test-author` 역할로 만든 것이다). **아직 적용하지 않았다.**

그대로 붙이면 실패하는 테스트가 나올 수 있고, 그때 "실제 결함인지 초안의 오류인지"
가리는 데 시간이 든다. 붙이기 전에 **초안을 실제 소스와 대조해 틀린 곳을 찾는 것**이 이번 과제다.

너는 파일을 고칠 수 없으니(read-only), **틀린 항목마다 고친 코드 조각을 출력**해라.

## 대조 대상

초안이 참조하는 모든 것을 실제 소스에서 확인해라:

1. **import 경로와 export 이름** — 초안이 부르는 함수·컴포넌트가 실제로 그 경로에 그 이름으로
   있는가. 특히 2026-08-10~11에 이름이 바뀐 것들:
   - `getMyTeamLeaveHistories` → **`getMyApprovalLeaveHistories`** (`my-team` → `my-approvals`)
   - `getMyTeamWelfareHistories` → **`getMyApprovalWelfareHistories`**
   - `useHistories`의 스코프 키: `'my-team'` → **`'my-approvals'`**
   - `useWelfare`에 신규: `useWelfarePolicies`, `useCreateWelfarePolicy`,
     `useUpdateWelfarePolicy`, `useDeactivateWelfarePolicy`
   - `useAuth`에 신규 export: `readStoredUserInfo`

2. **mock 대상이 맞는가** — 이 프로젝트는 **api 모듈**(`api/leaves.js` 등)을 mock한다.
   axios나 `api/index.js`를 mock하는 초안 코드가 있으면 잘못됐다.
   `vi.mock`에 나열한 함수가 그 모듈의 실제 export 전부를 덮는지도 봐라 —
   빠뜨리면 다른 함수가 `undefined`가 되어 엉뚱한 곳에서 터진다.

3. **응답 형태** — 이 프로젝트의 페이징 응답은 `{ content, page: { totalElements, totalPages } }`다
   (`page`가 중첩이다. Spring Boot 4의 `PagedModel` 형태). 초안이 `totalElements`를
   최상위에서 읽고 있으면 틀렸다.

4. **RequireAuth의 현재 동작** — 초안 작성 이후 바뀌었다:
   - `roles`가 있는 라우트는 **서버 확인 전에 아무것도 렌더하지 않는다**(`null`).
     즉 관리자 화면 테스트는 `me` mock이 resolve된 뒤에야 내용을 볼 수 있다 → `waitFor` 필수
   - `useCurrentUser`가 `initialDataUpdatedAt: 0`이라 **마운트 즉시 재검증이 돈다**

5. **AdminWelfarePoliciesPage의 부여일수 검증** — 초안 작성 이후 바뀌었다.
   `0.5일 단위 정규식`으로 거부하고 반올림하지 않는다. `0.25`를 넣으면
   `'부여 일수는 0.5일 단위로 입력해주세요.'`가 나온다. `toFixed(1)` 기준으로 쓴 단정이 있으면 틀렸다.

6. **컴포넌트가 실제로 렌더하는 텍스트** — 초안의 `getByText('...')`가 화면에 실제로 있는
   문자열인가. 라벨·버튼명·빈 상태 문구를 소스에서 확인해라.
   (예: 결재 화면 탭 이름, 정책 모달의 필드 라벨, `TableCard`의 `emptyLabel`)

7. **`vi.mock` 호이스팅** — `vi.mock`의 팩토리에서 바깥 변수를 참조하면 초기화 전 접근으로 터진다.
   초안에 그 패턴이 있으면 지적해라.

## 참고할 정답 예시 2개

이미 통과하고 있는 테스트다. 초안이 이 두 파일의 방식과 어긋나면 초안이 틀렸다고 보면 된다.

```
frontend/src/components/RequireAuth.test.jsx        (7건 통과)
frontend/src/components/layout/Sidebar.test.jsx     (5건 통과)
```

## 출력 형식

```
## 판정 요약
| 초안 파일 | 테스트 수 | 그대로 통과 예상 | 수정 필요 | 삭제 권장 |

## 수정 목록
### <초안 파일명> — <테스트 이름>
- 문제: <무엇이 실제 소스와 다른가. 파일:줄 근거>
- 고친 코드:
```<언어>
<해당 부분만. 파일 전체가 아니라 바꿔야 할 조각>
```

## 삭제 권장
<검증 가치가 없거나 구현 세부에 묶여 깨질 테스트. 이유와 함께>

## 그대로 적용해도 되는 파일
<목록만>
```

## 하지 말 것

- 테스트를 통과시키려고 **프로덕션 코드를 바꾸라고** 제안하지 말 것.
  초안이 틀렸으면 초안을 고쳐라. 프로덕션 코드가 진짜 틀렸다고 판단되면
  그건 `## 실제 결함 의심` 섹션으로 따로 빼서 근거를 대라 (재현 시나리오 포함)
- 새 테스트를 추가하지 말 것 — 이번 과제는 **검수**다
- `data-testid`를 프로덕션 코드에 뿌리라고 제안하지 말 것
