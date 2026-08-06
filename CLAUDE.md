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
3. 온보딩 미완료(`hire_date == null`) → `/api/auth/*` 외 403

따라서 컨트롤러의 `@PreAuthorize`는 **역할 게이트 전용**이고, 소유권·승인자 식별 검증은 서비스 계층 책임이다.

### 연차 잔액 모델
`잔여 = base_days + bonus_days - use_days`. 핵심 규칙:
- **신청(PENDING) 시점에 `use_days` 선차감**, 반려·취소 시 복구
- 잔여 부족 + 당겨쓰기 허용이면 부족분이 `advance_days`에 잡힘 → 다음 기산일에 새 `base_days`에서 차감
- **`advance_days`는 파생값이다** — `advance_days = max(0, use_days − base_days − bonus_days)`.
  `User.syncAdvanceDays()` 하나만 이 필드에 쓰고, 잔액 3필드(base/bonus/use)를 바꾸는 도메인 메서드 6개가
  마지막에 이걸 호출한다 — **`resetAnnualLeave`까지 예외 없이**. 이 필드에 단독 대입하는 코드를 새로 만들지 말 것
  (그게 리뷰 I-1·I-2·I-8의 원인이었다). `LeaveRequest.advanceUsedDays`는 감사 기록 전용이고 복구 계산에 쓰지 않는다.
  리셋도 재계산해야 하는 이유(빼면 빚이 면제된다)는 docs/09 §5 정정 블록에 검산이 있다.
- `User`에 `@Version` 낙관적 락 — 동시 신청 초과 방지
- 상태 전이는 전부 `User`·`LeaveRequest`의 도메인 메서드 안에 있다 (`deductLeave`, `restoreLeave`, `resetAnnualLeave`). Setter 없음.

`RequestStatus`(PENDING/APPROVED/REJECTED/CANCELLED/CANCEL_PENDING)는 연차·복리후생이 공유하며, 취소 승인/거부의 세부 결과는 status가 아니라 `action_history`의 `RequestAction`으로 기록한다.

### 시스템 설정 (관리자 조정 가능한 정책값)

`leave_policy_config` 테이블은 **값만** 들고 있고, 카탈로그는 `PolicyConfigKey` enum(`domain/policy/entity/`)이 정의한다 — 키·타입·기본값·허용 범위·라벨·설명·동작여부. **설정을 추가할 땐 이 enum에 상수 한 줄만 넣으면** 시딩(`DataInitializer`)·저장 검증·관리자 화면 렌더가 모두 따라온다. 프론트에 키 이름을 하드코딩하지 말 것 (`GET /api/admin/configs`가 메타데이터를 함께 내려준다).

- 값 검증은 **저장 시점**에 한다 (`PolicyConfigKey.validate`). 읽는 시점에 터지면 잘못 넣은 관리자가 아니라 **사원의 연차 신청이 실패**한다
- 읽기는 `PolicyConfigReader`의 타입별 접근자로만. **읽을 때도 같은 검증을 다시** 통과시키고 어긋나면 기본값 + WARN — 옛 값이 상한을 무력화하는 것을 막는다
- 그 값을 읽는 기능이 아직 없으면 반드시 `PENDING_FEATURE`로 둔다 (관리자 화면에 "미동작" 배지)
- 현재 ACTIVE: `advance_leave_enabled`, `advance_max_days`(당겨쓰기 누적 상한), `leave_max_dates_per_request`

### 프론트엔드 데이터 흐름
`api/*.js`(엔드포인트 1:1 함수, `res.data.data` 언랩) → `hooks/use*.js`(React Query 래퍼) → 페이지. 쿼리 키는 `['leaves', 서브리소스, ...파라미터]` 규칙이라 접두사로 일괄 invalidate 할 수 있다.

`api/index.js`의 axios 인스턴스가 `withCredentials: true` + 응답 인터셉터를 갖는다 — 401은 localStorage 정리 후 로그인 리다이렉트, 그 외 에러는 서버 메시지를 toast로 일괄 표출. **개별 호출부에서 에러 toast를 중복으로 띄우지 말 것.**

권한 가드는 `RequireAuth`(라우트 레벨)로 선차단한다 — useEffect 내 리다이렉트 방식 금지(깜박임·우회 여지).

### 스타일
Tailwind v4 CSS-first 설정. **`tailwind.config.js`는 없고** 디자인 토큰은 `frontend/src/index.css`의 `@theme` 블록에 있다 (`navy-*`, `accent-*`, `ink-*`, `radius-*`). docs/04의 "tailwind.config.js에 등록" 서술은 v3 기준이라 현재 코드와 다르다.

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

| 파일 | 커밋 | 내용 |
|---|---|---|
| `backend/src/main/resources/application.yml` | ✅ | 공통 설정, 시크릿은 `${ENV}` 참조만 |
| `application-local.yml` | ❌ gitignored | 로컬 개발 실제 시크릿 (DB·OAuth·JWT·공휴일 API) |
| `application-example.yml` | ✅ | 로컬 설정 템플릿 |

로컬은 MySQL 서비스 `MySQL96`(3306)의 `mlsoft_leave` DB를 쓴다. 테스트는 H2 인메모리(MySQL 호환 모드, `year/day/value`를 `NON_KEYWORDS`로 허용).

`ADMIN_EMAILS`(`app.admin-emails`)는 **최초 자동 가입 시에만** SYSTEM_ADMIN을 부여한다. 이미 가입된 계정을 승격하려면 DB `UPDATE users SET role='SYSTEM_ADMIN'` + 재로그인이 함께 필요하다.

`jpa.hibernate.ddl-auto: update` — 아직 마이그레이션 도구 없음. 운영 전환 시 validate + Flyway로 바꿔야 한다 (docs/08 Y-6).

## 배포

루트 `Dockerfile`이 3단계 멀티스테이지로 **프론트 빌드 산출물을 백엔드 static 리소스에 넣어 단일 컨테이너·단일 포트(8080)**로 합친다 (같은 오리진이라 CORS/쿠키 문제 없음). SPA 딥링크는 `WebConfig`의 리소스 리졸버가 index.html로 폴백시킨다.

```
docker compose -f docker-compose.prod.yml --env-file .env.prod up -d --build
```
`COOKIE_SECURE` 미설정 시 `CookieSecurityCheck`가 배포 단계에서 fail-fast 한다.

## 문서

작업 전 `docs/` 해당 문서를 먼저 확인한다. 설계 문서가 곧 서브 에이전트의 규칙이기도 하다.

| 문서 | 내용 |
|---|---|
| `docs/01-요구사항-기획.md` | 요구사항·기능 명세·권한 체계 |
| `docs/02-DB-설계.md` | 테이블 12개 + ENUM |
| `docs/03-API-설계.md` | 엔드포인트 + 공통 규칙(응답 포맷·페이징·에러 코드) |
| `docs/04-코드-스타일-가이드.md` | 코드 컨벤션 (위 요약의 원본) |
| `docs/05-디자인-가이드.md` | 디자인 토큰·화면 구조 |
| `docs/07-기능-갭분석.md` | 이전 버전 대비 누락·모순 (코드 주석의 `갭분석 A-1` 등 참조처) |
| `docs/08-운영-검증-리포트.md` | 실사용 검증 이슈 (코드 주석의 `검증 R-5`, `Y-2` 등 참조처) |
| `docs/09-스케줄러-설계.md` | 스케줄러 3개 잡 설계 — 실행 순서·catch-up·미래 승인분 재차감 |
| `docs/10-코드리뷰-리포트.md` | 코드 리뷰 결과 (코드 주석의 `리뷰 I-1`, `F-3` 등 참조처) |
| `docs/11-프로젝트-흐름.md` | **전체 흐름 지도** — 요청이 흐르는 길·연차 잔액 상태 전이·기능별 구현 상태. 처음 볼 문서 |
| `docs/구현-현황/` | 백엔드·프론트엔드·실행환경별 진행 상황 |

코드 주석의 `(검증 Y-2)`, `(갭분석 A-3)` 같은 표기는 각각 docs/08, docs/07의 항목 번호를 가리킨다 — 해당 로직을 수정할 땐 그 항목을 먼저 읽을 것.

## 세션 관리

`SESSION.md`가 직전 세션 요약과 이월 작업 목록을 담고 있다. `/load-session`으로 복구하고 `/save-session`으로 기록한다(옵시디언 볼트 연동).

`.claude/agents/`에 프로젝트 전용 서브 에이전트 4개가 있다 — `spring-backend`, `react-frontend`, `db-designer`, `style-reviewer`.

**git push는 사용자가 명시적으로 지시할 때만 한다.** 로컬 커밋까지가 기본이다.
