# 과제 (3/3-B): 승인 대기 중 입사일 1회 수정 — **프론트 테스트**

## 상황

`.codex/tasks/onboarding-revision.md`에 설계 전체가 있다. **D-10의 프론트 표가 이번 과제다.**

프로덕션 코드는 **이미 적용돼 있다.** 상상하지 말고 실제 코드를 읽어라:

- `frontend/src/pages/OnboardingPage.jsx` ← 이번 테스트 대상
- `frontend/src/api/auth.js` (`reviseOnboarding`)
- `frontend/src/hooks/useAuth.js` (`useCurrentUser` — 캐시 키 `['auth','me']`)

**기존 테스트의 구성을 본보기로 삼는다** (모킹 방식·QueryClient 래퍼·렌더 헬퍼):
- `frontend/src/pages/AdminDepartmentsPage.test.jsx`
- `frontend/src/pages/ApprovalsPage.test.jsx`

## 낼 파일

`frontend/src/pages/OnboardingPage.test.jsx` (**신규**) — 전문으로 낸다.

| # | 시나리오 | 기대 |
|---|---|---|
| 1 | 대기 상태 렌더 | **입력한 입사일이 화면에 보인다** (생년월일도) |
| 2 | `onboardingRevisable: true` | "입사일 수정" 버튼이 있다 |
| 3 | `onboardingRevisable: false, onboardingRevised: true` | 버튼 없음 + "이미 사용" 취지의 문구 |
| 4 | `onboardingRevisable: false, onboardingRevised: false` | 버튼 없음 + 관리자 반려 요청 안내 |
| 5 | 수정 버튼 → 폼이 뜨고 **기존 값이 채워져 있다** | `input[type=date]`의 value |
| 6 | 수정 제출 → `reviseOnboarding`이 호출되고, 응답이 `COMPLETED`면 `/dashboard`로 이동 | 라우팅 단정 |
| 7 | 수정 제출 후 응답이 `PENDING_APPROVAL`이면 대기 화면으로 돌아오고 **버튼이 사라진다** | 1회 제한이 화면에 반영됨 |

## 주의 — 이 프로젝트에서 실제로 걸렸던 것들

- **`getByText`로 문장 전체를 찾지 말 것.** Testing Library의 `getNodeText`는 중첩 요소(`<span>`,
  `<br/>`)의 텍스트를 건너뛰고 **직속 텍스트 노드만** 이어 붙인다. 이 화면의 안내문에는 `<br/>`가
  들어 있어 문장 전체 정규식이 매칭되지 않는다. **짧은 고유 조각**으로 찾아라
  (`AdminDepartmentsPage.test.jsx`에 같은 이유를 적은 주석이 있다).
- 컴포넌트가 `useCurrentUser()`를 쓴다. `api/auth.js`의 `me`를 모킹하거나 QueryClient에
  `setQueryData(['auth','me'], ...)`로 초기값을 심어라. **어느 쪽을 골랐는지 근거를 적을 것.**
- 성공/에러 toast는 `react-hot-toast`다. 모킹이 필요하면 기존 테스트 방식을 따를 것.
- `navigate` 단정은 기존 테스트가 쓰는 방식(MemoryRouter + 목적지 라우트 스텁 등)을 그대로 쓴다.
- **스모크 테스트 금지** — 렌더만 확인하고 끝나는 테스트는 실패한 작업이다.
- 시나리오 1개 = 테스트 1개.

## 산출물 형식

`## 결정` 표 → `## 파일`(전문) → `## 반영 순서` → `## 되돌릴 수 없는 것`.
