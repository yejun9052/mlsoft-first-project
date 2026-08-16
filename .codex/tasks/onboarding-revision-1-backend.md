# 과제 (1/3): 승인 대기 중 입사일 1회 수정 — **백엔드 + DB만**

## 먼저 할 일

**`.codex/tasks/onboarding-revision.md`를 읽어라.** 설계 전체(배경·확정 결정 D-1~D-10·금지사항)가
거기 있다. 이 파일은 그 설계의 **출력 범위를 자르는 지시서**일 뿐이다.

지난 실행에서 파일 14개 전문이 한 응답 한도를 넘어 산출물이 나오지 않았다. 그래서 3분할한다.
이번은 1/3이다.

## 이번 응답에서 낼 것 — 백엔드 프로덕션 코드 + DB만

| # | 파일 | 신규/수정 |
|---|---|---|
| 1 | `backend/.../domain/user/entity/User.java` | 수정 (D-2: `onboardingRevised` 필드 + `markOnboardingRevised()` + `rejectOnboarding` 초기화) |
| 2 | `backend/.../domain/policy/entity/PolicyConfigKey.java` | 수정 (D-6: `ONBOARDING_REVISION_ENABLED`) |
| 3 | `backend/.../global/exception/ErrorCode.java` | 수정 (D-4: `ONBOARDING_REVISION_DISABLED` 403, `ONBOARDING_REVISION_EXHAUSTED` 400) |
| 4 | `backend/.../global/response/ResponseMessage.java` | 수정 (`ONBOARDING_REVISED`) |
| 5 | `backend/.../domain/auth/dto/UserMeResponse.java` | 수정 (D-1: 필드 2개 + `from(User, boolean)`) |
| 6 | `backend/.../domain/auth/service/AuthService.java` | 수정 (D-4·D-5: `reviseOnboarding` + 공유 판정 메서드 추출) |
| 7 | `backend/.../domain/auth/controller/AuthController.java` | 수정 (D-3: `PATCH /api/auth/onboarding`) |
| 8 | `db/schema.sql` | 수정 (D-9: users에 `onboarding_revised`) |
| 9 | `db/backfill-2026-08-16-onboarding-revision.sql` | **신규** (D-9: 멱등 backfill) |

`UserMeResponse.from(User)`의 **다른 호출부가 남아 있는지 반드시 grep으로 확인**하고,
남아 있으면 그 파일도 목록에 추가해 전문을 내라 (D-1이 요구하는 것이다).
`db/schema.sql`은 크므로 **`users` 테이블의 `CREATE TABLE` 블록만** 잘라 내되,
"이 블록만 교체하면 된다"고 명시하라 — 이 파일 하나는 전문 출력 규칙의 예외로 허용한다.

## 이번에 내지 말 것

- 프론트엔드 (2/3에서 낸다)
- 테스트 (3/3에서 낸다 — 위 코드가 실제로 적용된 뒤에 작성해야 한다)

## 이름은 이미 고정돼 있다 — 바꾸지 말 것

2/3·3/3이 같은 이름을 전제로 작성되므로 하나라도 다르면 어긋난다.

```
필드      User.onboardingRevised (boolean)
도메인    User.markOnboardingRevised()
응답      UserMeResponse.onboardingRevisable, UserMeResponse.onboardingRevised
서비스    AuthService.reviseOnboarding(Long userId, OnboardingRequest request)
엔드포인트 PATCH /api/auth/onboarding
에러      ErrorCode.ONBOARDING_REVISION_DISABLED (403)
          ErrorCode.ONBOARDING_REVISION_EXHAUSTED (400)
메시지    ResponseMessage.ONBOARDING_REVISED
설정 키   onboarding_revision_enabled → PolicyConfigKey.ONBOARDING_REVISION_ENABLED
DB 컬럼   users.onboarding_revised
```

## 산출물 형식

역할 지시서 그대로 — `## 결정` 표 → `## 파일`(전문) → `## 반영 순서` → `## 되돌릴 수 없는 것`.
마지막에 `## 다음 분할에 넘길 전제` 절을 추가해, 2/3·3/3이 알아야 할 시그니처를 표로 정리하라.
