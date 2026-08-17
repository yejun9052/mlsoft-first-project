# 2026-08-17 전체 감사 (1/4) — 보안·인가·마스킹

## 지금 상태

- 자동 테스트 **백엔드 333 · 프론트 151** 전부 통과. `main`이 아니라 `design/cobalt-glass-redesign` 브랜치다.
- **2026-08-12에 이 저장소를 한 번 보안 감사했다.** 그때 본 것을 다시 훑지 마라.
  그 이후 들어온 것만 새 눈으로 보고, 기존 코드는 **아래 변경 때문에 깨진 것이 있는지**만 본다.

### 08-12 이후 들어온 것 (아무도 감사한 적 없다 — 여기를 우선)

| 영역 | 무엇 |
|---|---|
| 온보딩 1회 수정 | `PATCH /api/auth/onboarding` · `AuthService.reviseOnboarding` · `User.markOnboardingRevised` · 설정 키 `onboarding_revision_enabled` |
| 역할↔부서 팀장 동기화 | `UserService.assignDepartmentLeader` · `changeRole` · `DepartmentService` |
| 승인자 상위 부서 승격 | `ApproverResolver.resolvePrimary` / `findLeaderUpwards` |
| 퇴직 복구 | `UserService.restore` · `User.restore` · `AdminAction.USER_RESTORED` |
| 이메일 본문·수신자 | `EmailTemplateFactory`(HTML 표·바로가기 버튼·`escape`) · `EmailNotificationPublisher`(신청자 구분) |
| 부서 드래그 이동 | `utils/departmentDrop.js` · `AdminDepartmentsPage` |
| 페이지 응답 직렬화 | `WebConfig`의 `@EnableSpringDataWebSupport(VIA_DTO)` — **모든 페이징 응답의 JSON 모양이 바뀌었다** |

## 특히 볼 것

### 1. 온보딩 대기 상태의 권한 구멍

`OnboardingCheckInterceptor`가 온보딩 미완료 계정의 `/api/**`를 `/api/auth/*`만 남기고 막는다.
그 **열려 있는 `/api/auth/*` 안에서** 대기 계정이 할 수 있는 일을 전부 세어라.

- `PATCH /api/auth/onboarding`(수정)이 **대기 상태가 아닌 계정**에도 열려 있는가.
  이미 확정된 사원이 입사일을 바꿔 연차를 다시 받을 수 있는가
- 수정 횟수 제한(`onboardingRevised`)이 **서버에서** 강제되는가, 화면 버튼 숨김에만 의존하는가
- 관리자가 `onboarding_revision_enabled`를 껐을 때 API도 함께 막히는가
- 입사일 검증(미래 날짜·자동 승인 기간)이 최초 제출과 수정에서 **같은 경로**를 타는가

### 2. 역할·팀장·승인자가 한꺼번에 움직인다

`changeRole`이 역할 변경과 `department.leader_id` 갱신, 이전 팀장 강등, 대기 결재 이관을 함께 한다.

- 이 경로로 **권한이 의도보다 넓어지는** 입력이 있는가 (자기 자신을 상위 부서 팀장으로 등)
- `ApproverResolver`가 상위 부서로 올라갈 때 **결재 자격이 없는 사람**을 primary로 앉히는 경로가 있는가.
  `isEligibleFor`가 실제로 모든 갈래에서 불리는가
- 승인 API(`validateApprover`)가 **저장된 primary/sub만** 허용하는 규칙이 이 변경 뒤에도 유지되는가.
  상위 부서 팀장이 "결재 가능한 사람"으로 슬쩍 늘어나 있지 않은가
- 퇴직 복구(`restore`)로 **권한이 되살아나면 안 되는 것**이 되살아나는가

### 3. 이메일로 새는 것

- `EmailTemplateFactory.escape`가 **본문에 들어가는 모든 사용자 입력**을 통과하는가.
  표 라벨·값·요약 문장·제목까지 전부 세어라. 한 곳이라도 빠지면 메일 본문에 링크를 심을 수 있다
- 바로가기 버튼 URL이 `app.frontend-url` 설정값이다. 그 값에 이상한 것이 들어오면 어떻게 되는가
- 신청자/결재자 분기가 생겼다. **사유 마스킹(`reasonVisible`)과 이 분기가 서로 어긋나는 조합**이 있는가
- 퇴직자·비활성 계정이 수신자에 남는 경로

### 4. 응답 모양이 바뀐 것의 보안 영향

`VIA_DTO`로 페이징 응답이 `{content, page:{...}}`가 됐다.
**전에 최상위에 나가던 필드 중 지금 노출이 늘어난 것이 있는가** (`pageable`, `sort` 등).
반대로 프론트가 못 읽어 **권한 판단이 헐거워진** 곳이 있는가.

### 5. 시크릿

`.gitignore`와 `.dockerignore`의 **차집합**만 확인하라 (리뷰 O-4).
**파일 내용을 열지 마라.** 파일명과 추적 여부만 본다.

## 읽어야 할 것

`backend/src/main/java/com/mlsoft/backend/` 중 `security/`, `config/`, `domain/auth/`,
`domain/user/service/`, `domain/email/`, 각 `controller/`.
기준 문서: `docs/01`(권한 체계 §2-3(a)·§2-9(b)), `docs/03`, `docs/08 R-2·Y-4`.

## 하지 말 것

- 08-12에 이미 본 정상 경로를 다시 설명하기
- 재현 조건 없는 "위험해 보인다"
- 이미 `docs/12`에 있는 알려진 항목 재보고 (**S-7 자기 자신 퇴사는 이미 안다** — 다시 쓰지 마라)
