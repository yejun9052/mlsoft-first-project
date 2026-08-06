# AGENTS.md — Codex CLI 작업 규칙

MLsoft 사내 연차·복리후생 관리 시스템. Spring Boot 4(Java 21) + React 19 모노레포.
Claude Code 쪽 규칙은 `CLAUDE.md`에 있고, 이 문서는 **Codex가 자동으로 읽는 같은 내용의 요약**이다.
둘이 어긋나면 `CLAUDE.md`와 `docs/`가 우선이다.

**출력·주석·커밋 메시지는 전부 한국어로 쓴다.**

## 절대 규칙

1. **`backend/src/main/resources/application-local.yml`은 시크릿 파일이다** — 열지 말고, 값을 출력하지도 말 것.
   `.env.prod`, `*.key`, `*.pem`도 같다.
2. **git 명령을 실행하지 말 것** (`git commit` / `push` / `checkout` / `reset` 전부).
   버전 관리는 Claude Code 쪽에서 한다. 읽기 전용 `git diff` / `git status`만 허용.
3. **서버를 띄우지 말 것** (`bootRun`, `npm run dev`). 이미 8080·5173에서 돌고 있다.
4. 파일을 고칠 권한이 없으면(샌드박스 read-only) **고치려 시도하지 말고, 적용할 수 있는 전체 코드 블록을 출력**한다.
   "여기를 이렇게 바꾸세요" 같은 서술이 아니라 파일 경로 + 완성된 코드여야 한다.

## 지켜야 하는 코드 컨벤션 (docs/04)

- **연차 일수는 `BigDecimal`만** — `double`/`Double` 금지, DB는 `DECIMAL(4,1)`.
  비교는 `compareTo`, `==`/`equals` 금지 (`0.0`과 `0`이 equals에서 다르다).
- **문자열 리터럴 하드코딩 금지** — 성공 메시지는 `ResponseMessage` 상수, 에러는 `ErrorCode` enum.
  실패는 `BusinessException(ErrorCode)`를 던지고 `GlobalExceptionHandler`가 변환한다. 컨트롤러 try-catch 금지.
- 모든 응답은 `ResponseEntity<CommonResponse<?>>` 래핑.
- **본인 식별은 `@AuthenticationPrincipal AuthUser`에서** — 요청 body의 userId 신뢰 금지.
- 엔티티: `@NoArgsConstructor(PROTECTED)` + `@Getter` + `@Builder`, **Setter 금지**, 정적 팩토리(`create`) 선호.
  상태 전이는 전부 도메인 메서드 안에 둔다.
- DTO는 Java record. URL은 kebab-case 리소스명만.
- React 컴포넌트는 `export default function Name({ props })` — **화살표 함수 컴포넌트 금지**.
- Tailwind v4 CSS-first. **`tailwind.config.js`는 없다** — 토큰은 `frontend/src/index.css`의 `@theme`.

## 도메인 핵심 — 연차 잔액

```
잔여 = base_days + bonus_days − use_days
advance_days = max(0, use_days − base_days − bonus_days)   ← 파생값. 단독 대입 금지
```

- **신청(PENDING) 시점에 `use_days` 선차감**, 반려·취소 시 복구 (승인 시점이 아니다)
- 잔여가 부족한데 당겨쓰기가 허용이면 부족분이 `advance_days`에 잡히고, 다음 기산일에 새 `base_days`에서 차감된다
- `advance_days`는 **파생값**이다. `User.syncAdvanceDays()` 하나만 이 필드를 쓴다.
  `base`/`bonus`/`use`를 바꾸는 도메인 메서드는 마지막에 반드시 이걸 호출한다. **예외 없다** —
  `resetAnnualLeave`도 호출한다(빚이 새 정책 연차보다 크면 음수 `base_days`가 남는데, 다음 리셋이
  base를 덮어쓰므로 advance로 이어받지 않으면 빚이 면제된다 — docs/09 §5 정정 참고).
- 이 필드에 **단독 대입하는 코드를 새로 만들지 말 것.** 그게 리뷰 I-1·I-2·I-8의 원인이었다.
- 당겨쓰기 누적 상한은 설정 `advance_max_days`(기본 5.0)다. `User.deductLeave`가 차감 **전에** 판정한다

## 시스템 설정 (관리자 조정 가능한 정책값)

`leave_policy_config` 테이블은 값만 들고 있다. 카탈로그는 `PolicyConfigKey` enum이 정의한다 —
키·타입·기본값·허용 범위·라벨·설명·동작여부. **설정 추가는 이 enum에 상수 한 줄**이면 되고
시딩·저장 검증·관리자 화면 렌더가 따라온다.

- 값은 문자열로 저장되지만 **아무 문자열이나 되는 게 아니다.** 검증은 `PolicyConfigKey.validate`
- 읽기는 `PolicyConfigReader`의 타입별 접근자로만. 키 문자열이나 `Boolean.parseBoolean`을 직접 쓰지 말 것
  (그 메서드는 `"ture"` 오타를 예외 없이 false로 만들어 설정을 조용히 뒤집는다)
- 값을 읽는 기능이 아직 없는 설정은 `PENDING_FEATURE`로 둔다 — 화면에 "미동작"으로 표시된다
- 프론트에 설정 목록·라벨·타입을 복제하지 말 것. `GET /api/admin/configs`가 메타데이터를 함께 준다
- `User`에 `@Version` 낙관적 락 — 동시 신청 초과 방지

## 테스트

```powershell
cd backend; .\gradlew.bat test --tests "*LeaveServiceTest"    # 절대경로 권장
cd frontend; npm test
```
Gradle이 캐싱하므로 코드 변경 없이 재실행하려면 `--rerun-tasks`. 테스트 DB는 H2 인메모리.

## 문서 지도 (작업 전 해당 문서를 먼저 읽는다)

| 문서 | 내용 |
|---|---|
| `docs/01-요구사항-기획.md` | 요구사항·권한 체계 |
| `docs/02-DB-설계.md` | 테이블 12개 + ENUM (`메모 N` 참조처) |
| `docs/03-API-설계.md` | 엔드포인트 + 공통 규칙 |
| `docs/04-코드-스타일-가이드.md` | 코드 컨벤션 원본 |
| `docs/05-디자인-가이드.md` | 디자인 토큰·화면 구조 |
| `docs/07-기능-갭분석.md` | `갭분석 A-1` 참조처 |
| `docs/08-운영-검증-리포트.md` | `검증 R-5`, `Y-2` 참조처 |
| `docs/09-스케줄러-설계.md` | 스케줄러 3개 잡 — 실행 순서·catch-up |
| `docs/10-코드리뷰-리포트.md` | `리뷰 I-1`, `F-3` 참조처 |
| `docs/11-프로젝트-흐름.md` | 전체 흐름 지도 — 처음 볼 문서 |

코드 주석의 `(검증 Y-2)`, `(갭분석 A-3)`, `(리뷰 I-1)` 표기는 각각 docs/08·07·10의 항목 번호다.

## 서브 에이전트

`.codex/agents/*.md`에 역할별 지시서가 있고 `.codex/run-agent.ps1`로 띄운다.
지시서를 받았다면 **그 역할의 산출물 형식을 반드시 그대로** 따를 것 — Claude Code가 기계적으로 읽어 반영한다.
