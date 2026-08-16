# 과제: 승인 대기 중 입사일 확인 + 1회 수정 (관리자 on/off)

## 배경

온보딩에서 입사일을 자가 신고하면, 자동 승인 기간(`onboarding_auto_approve_days`, 기본 90일)
**밖**의 날짜는 연차 0으로 `PENDING_APPROVAL`에 들어간다 (리뷰 S-1).

지금 그 사원이 보는 화면(`OnboardingPage`의 `pendingApproval` 분기)에는
**자기가 무엇을 입력했는지가 안 나온다.** 오타를 냈어도 확인할 방법이 없고,
고치려면 관리자에게 반려를 요청해 `NOT_STARTED`로 되돌린 뒤 처음부터 다시 내야 한다.

세 가지를 만든다.

1. 대기 화면에 **입력한 입사일·생일을 표시**
2. 사원이 **1회에 한해 스스로 수정**
3. 관리자가 이 수정 기능을 **켜고 끌 수 있게**

---

## 반드시 먼저 읽을 파일

읽지 않고 쓰면 이 프로젝트의 규칙을 다시 만들게 된다.

**백엔드**
- `backend/src/main/java/com/mlsoft/backend/domain/auth/service/AuthService.java` — 온보딩 판정의 현재 전부
- `backend/src/main/java/com/mlsoft/backend/domain/auth/service/OnboardingApprovalService.java` — 승인·반려
- `backend/src/main/java/com/mlsoft/backend/domain/auth/controller/AuthController.java`
- `backend/src/main/java/com/mlsoft/backend/domain/auth/dto/UserMeResponse.java`
- `backend/src/main/java/com/mlsoft/backend/domain/auth/dto/OnboardingRequest.java`
- `backend/src/main/java/com/mlsoft/backend/domain/user/entity/User.java` — 도메인 메서드만, Setter 없음
- `backend/src/main/java/com/mlsoft/backend/domain/user/entity/OnboardingStatus.java`
- `backend/src/main/java/com/mlsoft/backend/domain/policy/entity/PolicyConfigKey.java` — 설정 카탈로그
- `backend/src/main/java/com/mlsoft/backend/domain/policy/service/PolicyConfigReader.java`
- `backend/src/main/java/com/mlsoft/backend/security/OnboardingCheckInterceptor.java` — **경로 제약의 근거**
- `backend/src/main/java/com/mlsoft/backend/global/exception/ErrorCode.java`
- `backend/src/main/java/com/mlsoft/backend/global/response/ResponseMessage.java`
- `backend/src/main/java/com/mlsoft/backend/domain/audit/entity/AdminAction.java` — **여기에 추가하지 않는 이유를 확인할 것**

**프론트엔드**
- `frontend/src/pages/OnboardingPage.jsx`
- `frontend/src/hooks/useAuth.js` — `useCurrentUser`가 권한·상태의 단일 출처다
- `frontend/src/api/auth.js`
- `frontend/src/pages/AdminPolicyPage.jsx` — 설정 화면은 **서버 메타데이터로 자동 렌더**된다

**스키마**
- `db/schema.sql` (users 테이블)
- `db/backfill-2026-08-08-scheduler.sql` — 멱등 backfill 작성 패턴의 본보기

**규칙**
- `CLAUDE.md`, `docs/04-코드-스타일-가이드.md`

---

## 확정된 설계 (고민하지 말고 이대로 구현한다)

아래는 이미 판단이 끝난 결정이다. **다르게 하고 싶으면 구현하지 말고 근거를 적어 반문할 것.**

### D-1. 수정 가능 여부는 `GET /api/auth/me`가 내려준다

`PENDING_APPROVAL` 사원은 `OnboardingCheckInterceptor` ③에 의해 **`/api/auth/*` 밖의 모든 경로가 403**이다.
그래서 프론트가 `GET /api/admin/configs`로 설정을 읽어 판단할 수 없다.

`UserMeResponse`에 필드 **2개**를 추가한다.

| 필드 | 타입 | 의미 |
|---|---|---|
| `onboardingRevisable` | `boolean` | **지금 수정 버튼을 눌러도 되는가** — 서버가 최종 판정한 값 |
| `onboardingRevised` | `boolean` | 수정권을 이미 썼는가 (화면 문구 구분용) |

`onboardingRevisable` = `상태 == PENDING_APPROVAL` **&&** `설정 ON` **&&** `!onboardingRevised`

**세 조건을 프론트에서 조합하지 말 것.** 규칙이 두 곳에 생기면 한쪽만 바뀐다.
프론트는 `onboardingRevisable` 하나만 보고 버튼을 렌더한다.
`onboardingRevised`는 **문구를 가르는 데만** 쓴다 — "이미 한 번 수정하셨습니다"와
"관리자가 이 기능을 꺼 두었습니다"는 사원이 취할 행동이 다르다.

`UserMeResponse.from(User)`는 설정을 모르므로, **설정값을 인자로 받는 오버로드**를 만든다.
기존 `from(User)`를 유지하고 새 `from(User, boolean revisionEnabled)`를 두되,
**`getMe`·`completeOnboarding`·`reviseOnboarding` 세 곳 모두 새 쪽을 쓴다.**
기존 `from(User)`가 남아 있으면 `revisable=false`가 조용히 내려가 버튼이 사라지는 사고가 난다 —
**호출부가 하나도 남지 않으면 기존 `from(User)`는 지운다.**

### D-2. 엔티티 필드는 `boolean onboardingRevised` 하나

```java
/** 승인 대기 중 입사일을 스스로 고쳤는가 — 수정은 1회뿐이다 */
@Column(nullable = false)
@Builder.Default
private boolean onboardingRevised = false;
```

- **횟수(int)로 두지 말 것.** 요구는 1회로 못박혀 있고, 카운트를 두면 "상한은 어디서 오나"는
  설정이 또 필요해진다. 그 설정은 읽는 코드가 없으므로 `PENDING_FEATURE`로 둬야 하고,
  결국 동작하지 않는 설정이 하나 늘 뿐이다 (`PolicyConfigKey` 클래스 주석 참조).
- `create()` 정적 팩토리는 손대지 않는다 — `@Builder.Default`가 false를 준다.

**반려 시 수정권을 되돌린다.** `User.rejectOnboarding()`에서 `this.onboardingRevised = false`.
반려는 `NOT_STARTED`로 되돌려 새 사이클을 시작하는 동작이다. 되돌리지 않으면
"반려 → 다시 제출 → 오타 발견 → 수정 불가"가 되어 이 기능이 있으나 마나가 된다.
**이 한 줄을 지우면 깨지는 테스트를 반드시 만들 것.**

승인(`completeOnboarding`) 쪽은 건드리지 않는다 — `COMPLETED`가 되면 이 경로를 못 쓴다.

### D-3. 엔드포인트는 `PATCH /api/auth/onboarding`

- **`/api/auth/` 접두사여야 한다.** 인터셉터가 그 밖을 막는다.
- POST(최초 제출)와 PATCH(수정)를 메서드로 가른다. `/api/auth/onboarding/revise` 같은
  동사 경로는 docs/04의 "URL은 kebab-case 리소스명만, 메서드명 노출 금지"에 걸린다.
- 요청 body는 기존 `OnboardingRequest` 그대로 재사용 (`birthDay`, `hireDate`).
- 응답은 `UserMeResponse`. 새 `ResponseMessage.ONBOARDING_REVISED = "입사일을 수정했습니다."`

### D-4. 검증 순서 — 이 순서를 지킨다

`AuthService.reviseOnboarding(Long userId, OnboardingRequest request)`:

1. 상태가 `PENDING_APPROVAL`이 아니면 → `ONBOARDING_NOT_PENDING` (기존 코드, 400)
2. 설정 `onboarding_revision_enabled`가 false면 → **새** `ONBOARDING_REVISION_DISABLED` (403)
3. `onboardingRevised == true`면 → **새** `ONBOARDING_REVISION_EXHAUSTED` (400)
4. 미래 입사일이면 → `FUTURE_HIRE_DATE` (기존 코드)
5. 통과 → 수정권 차감 후 **최초 제출과 같은 판정**을 태운다 (D-5)

**상태를 먼저 본다.** 설정이 꺼져 있을 때 `NOT_STARTED` 사원이 PATCH를 부르면
"기능이 꺼졌다"가 아니라 "대기 중이 아니다"가 맞는 답이다.

### D-5. 수정 후에도 최초 제출과 똑같은 자동 승인 판정을 태운다

`completeOnboarding`의 본문(미래일 차단 → 자동 승인 범위 비교 → 대기 or 즉시 확정)을
**private 메서드 하나로 뽑아** POST·PATCH가 공유한다.

```
1990-01-01 신고 → 대기
  → 수정으로 2026-08-01 입력 → 자동 승인 범위 안 → 즉시 COMPLETED + 연차 부여
  → 수정으로 2000-01-01 입력 → 여전히 범위 밖 → PENDING_APPROVAL 유지 (수정권만 소진)
```

범위 안으로 고치면 확정하는 것이 정책과 일치한다 — 그 범위의 날짜는 애초에 관리자 확인이
필요 없다는 것이 `onboarding_auto_approve_days`의 정의다. 악용 경로도 아니다:
최근 날짜는 연차가 적게 나오므로 최초 제출로 직접 넣는 것보다 유리할 게 없다.

이 공유가 리뷰 S-1이 "자동 승인과 관리자 승인이 **같은 경로**를 쓴다"고 못박은 것과 같은 계열이다.
판정을 복사하면 나중에 정책이 바뀔 때 수정 경로만 어긋난다.

**수정권 차감은 판정 결과와 무관하게 한다** (확정되든 대기가 유지되든 `onboardingRevised = true`).
`User`에 도메인 메서드를 둔다 — `markOnboardingRevised()`. **Setter 금지.**

### D-6. 관리자 설정 키

`PolicyConfigKey`의 `// ── 온보딩 (리뷰 S-1) ──` 블록, `ONBOARDING_AUTO_APPROVE_DAYS` **바로 뒤**에:

```java
ONBOARDING_REVISION_ENABLED(
        "onboarding_revision_enabled", "true",
        "승인 대기 중 입사일 수정 허용",
        "관리자 승인을 기다리는 사원이 신고한 입사일·생일을 1회에 한해 스스로 고칠 수 있게 한다. "
                + "끄면 잘못 입력한 사원은 관리자 반려를 기다려야 한다.",
        PolicyConfigStatus.ACTIVE),
```

- BOOLEAN 생성자 `(key, defaultValue, label, description, status)`를 쓴다.
- **기본값 `"true"`** — 기능을 켜 두는 것이 이 작업의 목적이고, 끄면 기존 동작으로 돌아간다.
- **`ACTIVE`다.** 이 값을 읽는 코드(D-4의 2번)를 같은 커밋에서 만들기 때문이다.
- 읽기는 `PolicyConfigReader.getBoolean(...)`. 키 문자열을 서비스에 하드코딩하지 말 것.
- 프론트 `AdminPolicyPage`는 **손댈 필요가 없다** — 서버 메타데이터로 자동 렌더된다.
  그 파일을 고치려 든다면 설계를 잘못 읽은 것이다.

### D-7. 감사 로그(`admin_audit_log`)에는 남기지 않는다

`AdminAction` enum 주석: *"기준은 사원의 권한이나 연차 잔액을 **관리자가 직접 바꾸는 조작**"*.
입사일 수정은 **사원 본인의 행위**다. 여기에 넣으면 관리자 조작 이력이 오염된다.

`log.info("[온보딩 수정] userId={}, {} → {}, 결과={}", ...)`로 애플리케이션 로그에만 남긴다.
**수정 전 입사일을 로그에 반드시 포함할 것** — 덮어쓰면 그 값이 어디에도 남지 않는다.

### D-8. 프론트 — `OnboardingPage`

**현재 이 페이지는 `localStorage`를 직접 읽는다.** `useAuth.js`가 *"컴포넌트는 localStorage를
직접 읽지 말고 이 훅을 쓴다"*고 명시한 것과 어긋난다. 이번에 `useCurrentUser()`로 바꾼다.
이유는 스타일이 아니다 — 관리자가 그사이 반려하면 상태·수정권이 서버에서 바뀌는데,
저장값만 보면 사원 화면이 낡은 채로 남아 없는 수정권을 제안하게 된다.

대기 화면(`pendingApproval` 분기)에 넣을 것:

1. **입력한 값 표시** — 입사일과 생일. 라벨 + 값 형태. `YYYY-MM-DD` 그대로면 충분하다.
2. **`onboardingRevisable === true`일 때만** "입사일 수정" 버튼.
3. 버튼을 누르면 같은 폼(생일·입사일 date input)이 뜬다. **폼 컴포넌트를 새로 만들지 말고
   기존 폼을 재사용**한다 — `max={TODAY}`·`DATE_INPUT_CLASS`가 이미 붙어 있다.
   초기값은 현재 저장된 값으로 채운다.
4. 수정 폼에는 **"수정은 1회만 가능합니다"**를 눈에 띄게 적는다. 되돌릴 수 없는 조작이다.
5. 제출 → `PATCH`. 응답이
   - `COMPLETED` → 성공 toast + `/dashboard`로 이동 (최초 제출 성공 경로와 동일)
   - `PENDING_APPROVAL` → 대기 화면으로 복귀. 이제 `onboardingRevisable=false`라 버튼이 사라진다.
6. `onboardingRevised === true`인데 `revisable=false`면 → **"수정은 1회만 가능하며 이미 사용하셨습니다.
   더 고치려면 관리자에게 반려를 요청해 주세요."**
   `onboardingRevised === false`인데 `revisable=false`면(설정 OFF) → 기존 안내문 그대로.
7. 응답을 `localStorage`에 저장하고 **react-query 캐시(`['auth','me']`)도 갱신**한다.
   저장만 하고 캐시를 두면 `useCurrentUser`의 다음 렌더가 옛 값을 쓴다.

**로그아웃 버튼은 그대로 남긴다.**

`frontend/src/api/auth.js`에 함수 추가:

```js
// 승인 대기 중 입사일·생일 수정 — 1회만 허용 (PATCH /api/auth/onboarding).
export async function reviseOnboarding({ birthDay, hireDate }) { ... }
```

### D-9. 스키마

**둘 다 해야 한다.** 하나만 하면 운영 배포가 기동 단계에서 멈춘다 (`ddl-auto: validate`).

1. `db/schema.sql`의 `users`에 컬럼 추가:
   `` `onboarding_revised` bit(1) NOT NULL DEFAULT b'0', ``
   (기존 컬럼들의 알파벳 정렬 위치를 따른다 — `onboarding_status` 바로 앞)
2. **새 파일** `db/backfill-2026-08-16-onboarding-revision.sql`
   - `db/backfill-2026-08-08-scheduler.sql`의 패턴을 그대로 따른다
   - MySQL 8에는 `ADD COLUMN IF NOT EXISTS`가 없다 → `information_schema.COLUMNS`로 존재 여부를
     보고 건너뛰는 prepared statement 방식
   - 전체를 **멱등**하게. 두 번 돌려도 안전해야 한다
   - `leave_policy_config`에 새 키를 넣는 INSERT는 **불필요하다** — `DataInitializer`가 시딩한다.
     넣는다면 `INSERT IGNORE` 또는 `ON DUPLICATE KEY UPDATE`로 멱등하게 하고,
     **관리자가 이미 바꾼 값을 덮어쓰지 않도록** 주의할 것. 판단해서 결정하고 근거를 적어라.

### D-10. 테스트

**"통과하는 테스트가 아니라 의존하는 테스트"** — 프로덕션 코드 한 줄을 지웠을 때
깨지는 테스트여야 한다.

`backend/src/test/java/com/mlsoft/backend/domain/auth/service/AuthServiceTest.java`
(기존 파일 — 스타일을 그대로 따를 것)에 추가:

| # | 검증 | 지우면 깨져야 하는 코드 |
|---|---|---|
| 1 | `NOT_STARTED` 사원의 수정 → `ONBOARDING_NOT_PENDING` | D-4 1번 |
| 2 | 설정 OFF → `ONBOARDING_REVISION_DISABLED` | D-4 2번 |
| 3 | 이미 수정함 → `ONBOARDING_REVISION_EXHAUSTED` | D-4 3번 |
| 4 | 미래 입사일 → `FUTURE_HIRE_DATE` | D-4 4번 |
| 5 | 범위 밖으로 수정 → `PENDING_APPROVAL` 유지, **연차 0 그대로**, `onboardingRevised=true` | D-5 |
| 6 | 범위 안으로 수정 → `COMPLETED` + 연차 부여 + 기산일 설정 | D-5 공유 판정 |
| 7 | 수정 2회 시도 → 두 번째가 거부된다 (1·2회를 이어서 호출) | D-2 플래그 |
| 8 | **반려 후 다시 제출하면 수정권이 돌아온다** | D-2 `rejectOnboarding` 초기화 |
| 9 | `getMe`의 `onboardingRevisable`이 세 조건 조합대로 나온다 (최소 3케이스) | D-1 |

8번은 `OnboardingApprovalServiceTest`에 두는 편이 자연스러울 수 있다. 판단해서 배치하고 근거를 적어라.

**프론트** — `frontend/src/pages/OnboardingPage.test.jsx` (새 파일).
`frontend/src/pages/AdminDepartmentsPage.test.jsx`의 구성(모킹 방식·렌더 헬퍼)을 본보기로 삼는다.

| # | 검증 |
|---|---|
| 1 | 대기 상태에서 **입력한 입사일이 화면에 보인다** |
| 2 | `onboardingRevisable=true` → 수정 버튼이 있다 |
| 3 | `onboardingRevisable=false, onboardingRevised=true` → 버튼 없음 + "이미 사용" 문구 |
| 4 | `onboardingRevisable=false, onboardingRevised=false` → 버튼 없음 + 기존 안내 |
| 5 | 수정 제출이 `PATCH`를 부르고, 응답이 `COMPLETED`면 대시보드로 이동한다 |

---

## 하지 말 것

- `AdminPolicyPage.jsx`에 키 이름을 하드코딩 (서버 메타데이터로 자동 렌더된다)
- `AdminAction`에 상수 추가 (D-7)
- `User`에 Setter 추가 — 상태 전이는 도메인 메서드로만
- `LocalDate` 비교를 서버 기본 TZ로 하기 — `AuthService`의 `KST` 상수를 쓴다
- `getHireDate() != null`로 온보딩 완료 판별 — `isOnboardingCompleted()`를 쓴다
- 컨트롤러 try-catch, 문자열 리터럴 하드코딩 (`ErrorCode`·`ResponseMessage`)
- `COMPLETED` 사원의 입사일 수정 경로를 만들기 — **이번 범위 밖이다**
- 리팩터링 끼워 넣기. 공유 판정 메서드 추출(D-5)까지가 허용 범위다

## 산출물

역할 지시서(`.codex/agents/code-author.md`)의 형식을 그대로 따른다 —
`## 결정` 표 → `## 파일`(전문, 새 파일/수정 파일 구분) → `## 반영 순서` → `## 되돌릴 수 없는 것`.

주석은 한국어로, **왜**를 쓴다. 이 프로젝트의 기존 주석 문체를 따를 것.
