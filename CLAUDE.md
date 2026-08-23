# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

MLsoft 사내 연차·복리후생 관리 시스템. Spring Boot 4(Java 21) + React 19 모노레포이며, 이전 버전(`참고자료/MLsoft`)을 분석해 재설계한 V2다. **코드 주석·문서·커밋 메시지는 전부 한국어로 작성한다.**

## 명령어

### 백엔드 (`backend/`)
```powershell
$env:SPRING_PROFILES_ACTIVE = 'local'; .\gradlew.bat bootRun   # 실행 (8080)
.\gradlew.bat test                                             # 전체 테스트 (H2 인메모리)
.\gradlew.bat test --tests "*LeaveServiceTest"                 # 단일 테스트 클래스
.\gradlew.bat test --tests "*LeaveServiceTest.apply_잔여부족시_예외"  # 단일 메서드
.\gradlew.bat build                                            # 빌드 (테스트 포함)
```
Gradle이 결과를 캐싱하므로 코드 변경 없이 재실행하려면 `--rerun-tasks`를 붙인다.

### 프론트엔드 (`frontend/`)
```powershell
npm run dev          # 개발 서버 (5173, /api·/oauth2를 8080으로 프록시)
npm test             # vitest 1회 실행
npm run test:watch   # watch 모드
npm run lint         # oxlint
npm run build        # 프로덕션 빌드
```
단일 테스트: `npx vitest run src/api/leaves.test.js -t "테스트명"`

### 전체 개발 서버 기동
`/dev` 슬래시 커맨드(`.claude/commands/dev.md`)를 쓴다. **서버를 Bash/PowerShell 도구의 `run_in_background`로 띄우면 안 된다** — 세션 하네스가 백그라운드 작업을 정리할 때 서버까지 죽는 문제가 재현됐다. `Start-Process`로 세션과 분리된 프로세스로 띄우고 로그는 파일로 리다이렉트할 것.

## 아키텍처

### 계층 구조 (백엔드)
`com.mlsoft.backend` 아래 **도메인별 패키지**(`domain/{auth,user,department,leave,policy,welfare,holiday,email}`)로 나뉘고, 각 도메인은 `controller / dto / entity / repository / service`를 갖는다. 공통 요소는 `global/`(응답·예외·BaseTimeEntity), `security/`(JWT·OAuth2), `config/`에 있다.

### 인증 흐름
Google OAuth2 → `CustomOAuth2UserService`(도메인 검증 + 자동 가입) → `OAuth2SuccessHandler`가 **HttpOnly 쿠키로 JWT 발급** → 이후 요청은 `JwtFilter`가 쿠키를 읽어 `AuthUser` principal을 SecurityContext에 세팅. 세션 없음(STATELESS), CSRF 비활성.

**`OnboardingCheckInterceptor`가 `/api/**` 전역 가드**로 매 요청 DB를 조회해 세 가지를 검사한다 — JWT는 발급 후 상태 변화를 못 담기 때문:
1. 퇴직자(`is_active=false`) → 로그아웃 외 전 경로 차단
2. DB role ≠ 토큰 role → SecurityContext 권한을 DB 기준으로 재구성 (승격·강등 즉시 반영)
3. 온보딩 미확정 → `/api/auth/*` 외 403

**온보딩 완료 판별은 `hire_date != null`이 아니라 `onboarding_status == COMPLETED`다** (리뷰 S-1). 입사일이 자가 신고라 자동 승인 기간(`onboarding_auto_approve_days`, 기본 90일) 밖의 값은 **연차 0으로 승인 대기**에 들어가고, 그 상태에서도 `hire_date`는 채워져 있다. 그래서 옛 기준을 쓰면 미확정 입사일이 스케줄러 3잡·승인자 후보의 입력이 된다. 새 코드에서 `getHireDate() != null`로 온보딩을 판별하지 말 것 — `isOnboardingCompleted()`를 쓴다.

따라서 컨트롤러의 `@PreAuthorize`는 **역할 게이트 전용**이고, 소유권·승인자 식별 검증은 서비스 계층 책임이다.

### 연차 잔액 모델
`잔여 = base_days + bonus_days - use_days`. 핵심 규칙:
- **신청(PENDING) 시점에 `use_days` 선차감**, 반려·취소 시 복구
- 잔여 부족 + 당겨쓰기 허용이면 부족분이 `advance_days`에 잡힘 → 다음 기산일에 새 `base_days`에서 차감
- **`advance_days`는 파생값이다** — `advance_days = max(0, use_days − base_days − bonus_days)`.
  `User.syncAdvanceDays()` 하나만 이 필드에 쓰고, 잔액 3필드(base/bonus/use)를 바꾸는 도메인 메서드 6개가
  마지막에 이걸 호출한다 — **`resetAnnualLeave`까지 예외 없이**. 이 필드에 단독 대입하는 코드를 새로 만들지 말 것
  (`@PrePersist`·`@PreUpdate`가 저장 직전에 한 번 더 재계산하지만 **그물이지 대체재가 아니다** —
  도메인 메서드는 flush 전 메모리 상태도 맞아야 하므로 계속 직접 호출한다)
  (그게 리뷰 I-1·I-2·I-8의 원인이었다). `LeaveRequest.advanceUsedDays`는 감사 기록 전용이고 복구 계산에 쓰지 않는다.
  리셋도 재계산해야 하는 이유(빼면 빚이 면제된다)는 docs/09 §5 정정 블록에 검산이 있다.
- `User`에 `@Version` 낙관적 락 — 동시 신청 초과 방지
- 상태 전이는 전부 `User`·`LeaveRequest`의 도메인 메서드 안에 있다 (`deductLeave`, `restoreLeave`, `resetAnnualLeave`). Setter 없음.

`RequestStatus`(PENDING/APPROVED/REJECTED/CANCELLED/CANCEL_PENDING)는 연차·복리후생이 공유하며, 취소 승인/거부의 세부 결과는 status가 아니라 `action_history`의 `RequestAction`으로 기록한다.

### 스케줄러 (`domain/leave/scheduler`, `domain/leave/service/*GrantService`·`*ResetService`)

`LeaveScheduler`가 매일 00:10 KST에 **① 기산일 리셋 → ② 월차 적립 → ③ 생일 반차** 순으로 돈다. 설계·검산은 docs/09.

- **순서를 바꾸지 말 것** — 취향이 아니라 데이터 의존성이다. 리셋이 `bonus_days`를 갈아 끼우므로 생일 반차가 먼저면 그날 증발하고(생일==기산일인 사원), 월차가 먼저면 1주년에 하루짜리 유령 적립이 남는다
- **사원 1명 = 1트랜잭션**(`REQUIRES_NEW`). `User`에 낙관적 락이 있어 전체를 한 트랜잭션으로 묶으면 1명의 충돌로 전원이 롤백된다. **그래서 사원별 루프가 진입점에 있다** — 서비스가 자기 메서드를 루프로 부르면 Spring 프록시를 안 타 이 경계가 생기지 않는다
- **리셋의 `carriedUse`는 회차마다 그 회차 기산일로 다시 집계**한다. 최종 기산일 기준으로 한 번에 계산하면 1차 연도 귀속분이 한 회차 일찍 빠져 그 해 이력이 틀린다
- **채무 계산은 `User.carryOverDebt` 하나뿐이다** — `resetAnnualLeave`와 `LeaveResetHistory.create`가 공유한다. 이력이 자기 식(`advance_days` 직접 차감)을 갖고 있던 것이 실제 결함이었다(기록이 5일 어긋남)
- 월차 지급일은 **`hire_date + N개월`**로 계산하고 횟수는 `monthly_granted_count`가 센다. 직전 지급일에서 한 달씩 더하면 말일 클램프가 누적돼 지급일이 앞당겨지고(1/31→2/28→3/28), 날짜에서 횟수를 역산하면 `MONTHS.between(1/31, 2/28)=0`이라 매달 재지급된다
- 잡 서비스는 `Clock`이 아니라 계산된 `today`를 인자로 받는다. `Clock` 빈은 진입점에만 주입한다
- **테스트에서 크론이 돌면 안 된다** — 가드는 `LeaveScheduler`의 `@Profile("!test")`다. `SchedulingConfig`를 막으면 `Clock` 빈이 사라져 테스트 컨텍스트가 깨진다

### 개인 일정 (`domain/schedule`)

외근·출장·재택근무·교육을 캘린더에 기록한다. **연차 잔액을 차감하지 않고, 결재를 거치지 않고, 상태 전이가 없다** — 그래서 `LeaveRequest`가 아니라 별도 엔티티(`ScheduleEntry`)다. 같은 엔티티에 얹으면 신청·결재·취소 흐름마다 "차감 안 하면 건너뛰기" 분기가 생기는데, 그 조건 분기 산재가 리뷰 I-1·I-5의 원인이었다. **연차 로직을 `ScheduleService`에 복사해 오지 말 것** (그걸 막는 테스트가 있다).

- 종류 추가는 `ScheduleType` enum에 상수 한 줄 — `GET /api/schedules/types`가 라벨까지 내려주므로 프론트가 따라온다
- 주말·과거 날짜를 막지 않는다 (주말 출장·지난주 외근 기록이 정상 사용). 같은 날짜+같은 종류 중복만 막는다
- 메모는 본인·SYSTEM_ADMIN에게만 응답에 채워진다 (연차 사유 마스킹과 같은 기준)

### 시스템 설정 (관리자 조정 가능한 정책값)

`leave_policy_config` 테이블은 **값만** 들고 있고, 카탈로그는 `PolicyConfigKey` enum(`domain/policy/entity/`)이 정의한다 — 키·타입·기본값·허용 범위·라벨·설명·동작여부. **설정을 추가할 땐 이 enum에 상수 한 줄만 넣으면** 시딩(`DataInitializer`)·저장 검증·관리자 화면 렌더가 모두 따라온다. 프론트에 키 이름을 하드코딩하지 말 것 (`GET /api/admin/configs`가 메타데이터를 함께 내려준다).

- 값 검증은 **저장 시점**에 한다 (`PolicyConfigKey.validate`). 읽는 시점에 터지면 잘못 넣은 관리자가 아니라 **사원의 연차 신청이 실패**한다
- 읽기는 `PolicyConfigReader`의 타입별 접근자로만. **읽을 때도 같은 검증을 다시** 통과시키고 어긋나면 기본값 + WARN — 옛 값이 상한을 무력화하는 것을 막는다
- 그 값을 읽는 기능이 아직 없으면 반드시 `PENDING_FEATURE`로 둔다 (관리자 화면에 "미동작" 배지)
- 현재 카탈로그는 `PolicyConfigKey`를 기준으로 ACTIVE 7개와 PENDING_FEATURE 2개다(2026-08-23).
  실제 키·동작 여부는 enum과 `GET /api/admin/configs`에서 확인하고 이 문서에 키 목록을 복제하지 않는다.

### 프론트엔드 데이터 흐름
`api/*.js`(엔드포인트 1:1 함수, `res.data.data` 언랩) → `hooks/use*.js`(React Query 래퍼) → 페이지. 쿼리 키는 `['leaves', 서브리소스, ...파라미터]` 규칙이라 접두사로 일괄 invalidate 할 수 있다.

`api/index.js`의 axios 인스턴스가 `withCredentials: true` + 응답 인터셉터를 갖는다 — 401은 localStorage 정리 후 로그인 리다이렉트, 그 외 에러는 서버 메시지를 toast로 일괄 표출. **개별 호출부에서 에러 toast를 중복으로 띄우지 말 것.**

권한 가드는 `RequireAuth`(라우트 레벨)로 선차단한다 — useEffect 내 리다이렉트 방식 금지(깜박임·우회 여지).

### 스타일
Tailwind v4 CSS-first 설정. **`tailwind.config.js`는 없고** 디자인 토큰은 `frontend/src/index.css`의 `@theme` 블록에 있다 (`navy-*`, `accent-*`, `ink-*`, `radius-*`), 커스텀 유틸리티는 `@utility`. 값의 단일 출처는 `index.css`이고 docs/05가 그걸 문서화한다.

## 코드 컨벤션

전체 규칙은 `docs/04-코드-스타일-가이드.md`에 있다. 특히 지킬 것:

- **연차 일수는 `BigDecimal`만 사용** (double/Double 금지, DB는 `DECIMAL(4,1)`). 비교는 `compareTo`, `==` 금지
- **문자열 리터럴 하드코딩 금지** — 성공 메시지는 `ResponseMessage` 상수, 에러는 `ErrorCode` enum(HTTP status + 메시지). 실패는 `BusinessException(ErrorCode)`를 던지고 `GlobalExceptionHandler`가 일괄 변환. 컨트롤러 try-catch 금지
- 모든 응답은 `ResponseEntity<CommonResponse<?>>` 래핑 (`{ success, message, data }`)
- **본인 식별은 `@AuthenticationPrincipal AuthUser`에서** — 요청 body의 userId/employeeId 신뢰 금지
- 엔티티: `@NoArgsConstructor(PROTECTED)` + `@Getter` + `@Builder`, Setter 금지, 정적 팩토리(`create`) 선호, `@Enumerated(EnumType.STRING)`
- DTO는 Java record
- 자바 필드는 camelCase, DB 매핑은 `@Column(name=...)`
- URL은 kebab-case 리소스명만 (`DELETE /api/leaves/{id}`, 메서드명 노출 금지)
- React 컴포넌트는 `export default function Name({ props })` — **화살표 함수 컴포넌트 금지**

## 설정과 시크릿

| 파일 | 커밋 | 도커 컨텍스트 | 내용 |
|---|---|---|---|
| `backend/src/main/resources/application.yml` | ✅ | 포함 | 공통 설정, 시크릿은 `${ENV}` 참조만 |
| `application-local.yml` | ❌ gitignored | **❌ dockerignored** | 로컬 개발 실제 시크릿 (DB·OAuth·JWT·공휴일 API) |
| `application-example.yml` | ✅ | 포함 | 로컬 설정 템플릿 |

**시크릿 파일을 새로 만들면 `.gitignore`와 `.dockerignore` 둘 다에 넣는다.** git과 도커 빌드 컨텍스트는 별개다 — `Dockerfile`의 `COPY backend/ ./`는 파일시스템을 그대로 복사하므로 gitignore가 막아 주지 않고, `bootJar`가 jar 리소스로 패키징해 **운영 이미지에 시크릿이 박힌다** (리뷰 O-4, 2026-08-07 차단).

로컬은 MySQL 서비스 `MySQL96`(3306)의 `mlsoft_leave` DB를 쓴다. 테스트는 H2 인메모리(MySQL 호환 모드, `year/day/value`를 `NON_KEYWORDS`로 허용).

`DemoDataInitializer`가 **`local` 프로필에서만** 시연 데이터를 만든다 — 사원 8·부서 3·연차 신청 8(모든 상태)·복리후생 2·처리 이력. 화면을 mock 없이 실 API로 채우기 위한 것이다(리뷰 F-2). 잔액은 반드시 도메인 메서드(`deductLeave`/`restoreLeave`)로 만들어 불변식을 지킨다 — 행을 직접 짜 넣지 말 것. **`@Profile("local")`을 지우거나 넓히지 말 것**: 시연 계정이 운영 DB에 들어가면 실제 사원과 구분할 방법이 없다. 재기동은 안전하다(멱등).

`ADMIN_EMAILS`(`app.admin-emails`)는 **최초 자동 가입 시에만** SYSTEM_ADMIN을 부여한다. 이미 가입된 계정을 승격하려면 DB `UPDATE users SET role='SYSTEM_ADMIN'` + 재로그인이 함께 필요하다.

`jpa.hibernate.ddl-auto: update` — 아직 마이그레이션 도구 없음. 운영 전환 시 validate + Flyway로 바꿔야 한다 (docs/08 Y-6).

## 배포

루트 `Dockerfile`이 3단계 멀티스테이지로 **프론트 빌드 산출물을 백엔드 static 리소스에 넣어 단일 컨테이너·단일 포트(8080)**로 합친다 (같은 오리진이라 CORS/쿠키 문제 없음). SPA 딥링크는 `WebConfig`의 리소스 리졸버가 index.html로 폴백시킨다.

```
docker compose -f docker-compose.prod.yml --env-file .env.prod up -d --build
```

**운영 프로필은 `ddl-auto: validate`다** (`application-prod.yml`). Hibernate가 테이블을 만들지 않으므로 최초 스키마는 `db/schema.sql`이 만든다 — compose가 mysql 컨테이너의 `/docker-entrypoint-initdb.d/`에 마운트하고, MySQL은 데이터 볼륨이 비어 있을 때만 실행한다.

**엔티티를 바꾸면 두 가지를 함께 해야 한다** (하나만 하면 배포가 기동 단계에서 멈춘다 — 의도된 동작이다):
1. `db/schema.sql` 갱신 — **빈 DB를 처음 만들 때만** 쓰인다
2. `db/backfill-<날짜>-<주제>.sql`에 `ALTER TABLE` 추가 — **이미 데이터가 있는 DB는 이쪽으로만 컬럼을 받는다.** MySQL 8에는 `ADD COLUMN IF NOT EXISTS`가 없으므로 `information_schema`로 존재 여부를 보고 건너뛰게 쓴다(예: `db/backfill-2026-08-08-scheduler.sql`). 값 보정 UPDATE도 같은 파일에 이어 붙이고 전체를 멱등하게 유지한다

**`@Enumerated(STRING)` enum에 상수를 추가하는 것도 스키마 변경이다** (2026-08-16에 겪음). DB 컬럼이 MySQL `ENUM(...)` 타입이라 값 목록을 넓히지 않으면 저장 시점에 `Data truncated for column '...'`로 터진다 — **`ddl-auto: update`는 컬럼을 추가만 하고 기존 컬럼 정의는 바꾸지 않는다.** 그래서 `MODIFY COLUMN`을 backfill에 함께 써야 한다(예: `db/backfill-2026-08-16-user-restore.sql`).

두 가지를 특히 조심할 것:
- **테스트가 절대 못 잡는다.** H2는 매번 엔티티에서 스키마를 새로 만들어 새 상수가 항상 포함된다
- **운영에서 fail-fast도 안 걸린다.** `ddl-auto: validate`는 ENUM 값 목록까지 검사하지 않아 기동은 정상이고, 그 값을 처음 저장하는 요청에서 500이 난다 — 컬럼 누락보다 더 조용히 터진다

현재 ENUM 컬럼을 갖는 enum 10개(`AdminAction`·`RequestStatus`·`RequestAction`·`LeaveType`·`ScheduleType`·`Role`·`OnboardingStatus`·`WelfareTarget`·`EmailType`·`EmailStatus`)는 2026-08-16에 `db/schema.sql`과 전수 대조해 일치를 확인했다.

기동 fail-fast 2개 — `COOKIE_SECURE` 미설정 시 `CookieSecurityCheck`, `ALLOWED_DOMAIN`이 비면 `AllowedDomainCheck`(빈 값은 "제한 없음"이라 아무 Google 계정이나 자동 가입된다).

## 문서

작업 전 `docs/` 해당 문서를 먼저 확인한다. 설계 문서가 곧 서브 에이전트의 규칙이기도 하다.

| 문서 | 내용 |
|---|---|
| `docs/01-요구사항-기획.md` | 요구사항·기능 명세·권한 체계 |
| `docs/02-DB-설계.md` | 초기 DB 설계 + 현재 schema.sql 16개 테이블·ENUM 대조 |
| `docs/03-API-설계.md` | 엔드포인트 + 공통 규칙(응답 포맷·페이징·에러 코드) |
| `docs/04-코드-스타일-가이드.md` | 코드 컨벤션 (위 요약의 원본) |
| `docs/05-디자인-가이드.md` | 디자인 토큰·화면 구조 |
| `docs/07-기능-갭분석.md` | 이전 버전 대비 누락·모순 (코드 주석의 `갭분석 A-1` 등 참조처) |
| `docs/08-운영-검증-리포트.md` | 실사용 검증 이슈 (코드 주석의 `검증 R-5`, `Y-2` 등 참조처) |
| `docs/09-스케줄러-설계.md` | 스케줄러 3개 잡 설계 — 실행 순서·catch-up·미래 승인분 재차감 |
| `docs/10-코드리뷰-리포트.md` | 코드 리뷰 결과 (코드 주석의 `리뷰 I-1`, `F-3` 등 참조처) |
| `docs/11-프로젝트-흐름.md` | **전체 흐름 지도** — 요청 경로·연차 잔액 상태 전이·구조. 처음 볼 문서 |
| `docs/12-남은-작업.md` | **현재 상태와 열려 있는 작업의 단일 원본** — 완료·부분·미구현·우선순위 |
| `docs/13-QA-체크리스트.md` | 손으로 돌려볼 항목 — 자동 테스트가 못 잡는 렌더·권한·배포 설정. 브라우저로 확인할 때 |
| `PROJECT_PROGRESS.md` | **세션 전환용 진행도 메모리** — 완료·미구현·정책 결정·다음 순서 |
| `CHANGELOG.md` (루트) | **버전별 변경 이력** — 무엇이 언제 왜 바뀌었는지. 배포 전이라 `0.x` |
| ~~`docs/구현-현황/`~~ | **폐기(2026-08-07)** — docs/11 §4와 docs/12가 대체. 상태 문서를 3개 병렬로 두니 아무도 안 고쳤다 |

**리포트 문서(07·08·10)는 본문을 고쳐 쓰지 말 것** — 코드 주석이 항목 번호를 참조하므로 번호가 바뀌면 링크가 끊긴다. 07·08은 상단 "구현 상태" 표만 갱신한다.

코드 주석의 `(검증 Y-2)`, `(갭분석 A-3)` 같은 표기는 각각 docs/08, docs/07의 항목 번호를 가리킨다 — 해당 로직을 수정할 땐 그 항목을 먼저 읽을 것.

## 세션 관리

`SESSION.md`가 직전 세션 요약과 이월 작업 목록을 담고 있다. `/load-session`으로 복구하고 `/save-session`으로 기록한다(옵시디언 볼트 연동).

`.claude/agents/`에 프로젝트 전용 서브 에이전트 4개가 있고, `.codex/agents/`에 Codex 감사·테스트·리뷰 역할 7개가 있다.

**git push는 사용자가 명시적으로 지시할 때만 한다.** 로컬 커밋까지가 기본이다.
