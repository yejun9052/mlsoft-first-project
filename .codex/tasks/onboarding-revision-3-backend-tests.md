# 과제 (3/3-A): 승인 대기 중 입사일 1회 수정 — **백엔드 테스트**

## 상황

`.codex/tasks/onboarding-revision.md`에 설계 전체가 있다. **D-10의 표가 이번 과제다.**

1/3(백엔드)·2/3(프론트) 프로덕션 코드는 **이미 적용돼 있다.** 상상하지 말고 실제 코드를 읽어라:

- `backend/src/main/java/com/mlsoft/backend/domain/auth/service/AuthService.java`
  (`reviseOnboarding`, `processOnboarding`, `getMe`)
- `backend/src/main/java/com/mlsoft/backend/domain/user/entity/User.java`
  (`onboardingRevised`, `markOnboardingRevised()`, `rejectOnboarding()`)
- `backend/src/main/java/com/mlsoft/backend/domain/auth/dto/UserMeResponse.java` (`from(User, boolean)`)
- `backend/src/main/java/com/mlsoft/backend/domain/policy/entity/PolicyConfigKey.java`
  (`ONBOARDING_REVISION_ENABLED`)
- `backend/src/main/java/com/mlsoft/backend/global/exception/ErrorCode.java`

그리고 **기존 테스트를 끝까지 읽고 그 스타일을 그대로 따른다**:
- `backend/src/test/java/com/mlsoft/backend/domain/auth/service/AuthServiceTest.java`
- `backend/src/test/java/com/mlsoft/backend/domain/auth/service/OnboardingApprovalServiceTest.java`

## 써야 할 테스트

**기준: 프로덕션 코드 한 줄을 지웠을 때 깨지는 테스트.** 통과만 하는 테스트는 실패한 작업이다.
각 테스트에 "이 줄을 지우면 이 테스트가 깨진다"를 **주석 한 줄로** 남겨라.

| # | 시나리오 | 무너뜨려야 하는 코드 |
|---|---|---|
| 1 | `NOT_STARTED` 사원이 수정 요청 → `ONBOARDING_NOT_PENDING` | 상태 검사 |
| 2 | 설정 OFF → `ONBOARDING_REVISION_DISABLED` | 설정 검사 |
| 3 | 이미 수정한 사원 → `ONBOARDING_REVISION_EXHAUSTED` | 수정권 검사 |
| 4 | 미래 입사일로 수정 → `FUTURE_HIRE_DATE`, **그리고 `onboardingRevised`가 여전히 false** | 차감이 미래일 검증 뒤에 있다는 것 |
| 5 | 자동 승인 범위 **밖**으로 수정 → `PENDING_APPROVAL` 유지 · `baseDays` 0 유지 · `onboardingRevised=true` · `hireDate`가 새 값 | 공유 판정의 대기 분기 |
| 6 | 자동 승인 범위 **안**으로 수정 → `COMPLETED` · 연차 부여 · `lastResetDate` 설정 | 공유 판정의 확정 분기 |
| 7 | 같은 사원이 연속 2회 수정 → 두 번째가 `ONBOARDING_REVISION_EXHAUSTED` | 1회 제한 전체 |
| 8 | `getMe`의 `onboardingRevisable` — 최소 3케이스 (대기+설정ON+미사용→true / 설정OFF→false / 이미수정→false). `COMPLETED` 사원도 false인지 한 건 더 두면 좋다 | `UserMeResponse.from`의 3조건 AND |

**4번이 특히 중요하다.** 차감을 미래일 검증 앞으로 옮기면 날짜를 잘못 찍은 사원이
수정권만 잃는다. 그 회귀를 잡는 테스트가 4번이다.

### 반려 시 수정권 복구 (별도 배치)

`OnboardingApprovalServiceTest`에 둔다 — 반려는 그쪽 서비스의 동작이다.

| # | 시나리오 |
|---|---|
| 9 | `onboardingRevised=true`인 대기 사원을 반려하면 `onboardingRevised`가 false로 돌아간다 |

`User.rejectOnboarding()`에서 `this.onboardingRevised = false;` 한 줄을 지웠을 때
**반드시 깨져야 한다.**

## 주의

- `AuthServiceTest`는 순수 Mockito다. `policyConfigReader.getBoolean(...)`·`getInt(...)` 스텁이
  필요하다. **Mockito strict stub이라 쓰이지 않는 스텁을 남기면 테스트가 실패한다** —
  예: 상태 검사에서 즉시 예외가 나는 1번 테스트에 `getBoolean` 스텁을 걸면 안 된다.
- `BigDecimal` 단정은 `assertEquals(0, expected.compareTo(actual))`. `assertEquals(expected, actual)` 금지.
- `LocalDate.now(KST)`를 서비스가 직접 부른다 — 고정 시각 주입 지점이 없다.
  기존 테스트가 이걸 어떻게 다루는지 **먼저 확인하고 같은 방식**을 쓸 것.
  오늘 기준 상대 날짜(`LocalDate.now().minusDays(...)`)로 쓰면 경계에서 깨지지 않게 여유를 둘 것.
- 시나리오 1개 = 테스트 1개. 한 테스트에 여러 개를 묶지 말 것.

## 산출물 형식

역할 지시서 그대로 — `### 추가: <파일 경로>` + 삽입 위치 + 새 import + 코드 블록.
파일 전문이 아니라 **추가할 테스트 메서드**만 내라 (기존 파일이 크다).
