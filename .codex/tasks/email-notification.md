# 이메일 자동 발송 1단계 — 건별 알림 인프라

`domain/email`은 **엔티티·리포지토리만 있고 참조하는 코드가 0줄**이다(확인함). 발송기·이벤트·비동기 설정을
새로 만들어 `TODO(email)` 7곳을 연결한다.

**이번 범위가 아닌 것** — docs/01 §2-8 관리자 일괄 발송 화면(대상 목록·양식 작성·선택 발송·자동 주기)은
2단계다. `PolicyConfigKey`의 `REMINDER_LIST_DAYS`·`REMINDER_AUTO_CYCLE`은 **`PENDING_FEATURE`로 그대로 둔다.**
프론트엔드도 이번엔 건드리지 않는다.

## 발송 계정 전제 — 개인 Gmail + 앱 비밀번호

회사 Workspace 계정을 못 받아서 **개인 Google 계정 1개를 발신 전용으로** 쓴다. 이게 설계에 주는 제약:

- **From 주소를 바꿀 수 없다.** 개인 Gmail SMTP는 발신자를 계정 주소로 강제한다.
  → 표시 이름만 지정한다: `MLsoft 연차관리 <계정주소>`. `MimeMessageHelper.setFrom(주소, 표시이름)`.
- **회신을 받을 주소는 Reply-To로 분리한다.** 설정값으로 뺄 것(`app.mail.reply-to`, 비면 헤더 생략).
- **일 500건 한도**다(Workspace 2,000건이 아니다). 건별 알림 규모에선 충분하지만,
  수신자 목록을 만들 때 중복을 제거해 불필요한 발송을 늘리지 말 것.

## 반드시 지킬 설계 제약

### 1. 트랜잭션 경계 — R-4의 핵심이다

- **메일 실패가 업무 처리를 롤백시키면 안 된다.** 메일 서버가 죽어도 연차 승인은 성공해야 한다.
- **업무 트랜잭션이 롤백되면 메일도 나가면 안 된다.** 그래서 `@TransactionalEventListener(AFTER_COMMIT)`다.
- 리스너는 **커밋 이후에 돈다 = 원래 트랜잭션이 이미 끝났다.** `email_history`를 저장하려면
  리스너 쪽에 `@Transactional(propagation = REQUIRES_NEW)`가 있어야 한다. 없으면 저장이 조용히 사라진다.
- 이벤트 페이로드에 **JPA 엔티티를 담지 마라.** 커밋 후엔 준영속이라 지연 로딩이 터진다.
  id와 원시값(이름·이메일·날짜·일수 문자열)만 담은 record로 만들 것.

### 2. 비동기 실행기를 명시할 것

`@EnableAsync`만 켜고 실행기를 안 주면 Spring 기본값이 요청마다 스레드를 새로 만든다.
**경계 있는 `ThreadPoolTaskExecutor` 빈을 만들고 `@Async("빈이름")`으로 지정**한다.
큐가 가득 찼을 때의 거부 정책도 정해라(발송은 버려도 업무는 살아야 한다).

### 3. 계정이 없어도 기동은 돼야 한다

지금 `application.yml:43`은 `username: ${MAIL_USERNAME}`로 **기본값이 없다.** 환경변수가 없으면
기동이 죽는다. 공휴일 API(`holiday.api-key`)와 같은 정책으로 맞춰라 —
**빈 값이면 기동은 되고, 발송 시점에 WARN 남기고 건너뛴다.** `email_history`에는 FAILED로 기록한다
(안 보낸 것이 아니라 못 보낸 것이므로 재시도 대상이다).

`COOKIE_SECURE`·`ALLOWED_DOMAIN` 같은 fail-fast 대상이 **아니다.** 메일은 부가 기능이다.

### 4. `retry_count` 추가 (리뷰 D-4)

"FAILED는 최대 3회 재시도"인데 횟수를 셀 컬럼이 없어 **영구 실패 건을 무한 재시도**하게 된다.

- `EmailHistory`에 `retryCount` 추가, `markFailed`가 증가시킨다
- 재시도 스케줄러: `status=FAILED AND retry_count < 3`만 집어 온다. 주기는 네가 정하고 근거를 써라
- **`db/schema.sql` 갱신 + `db/backfill-2026-08-12-email.sql` 신설 둘 다** 해야 한다.
  운영은 `ddl-auto: validate`라 하나만 하면 배포가 기동 단계에서 멈춘다.
  MySQL 8에는 `ADD COLUMN IF NOT EXISTS`가 없으니 `information_schema`로 건너뛰게 쓰고 **멱등**하게 유지할 것.
  기존 행의 `retry_count` 기본값 보정까지 같은 파일에 넣어라. 형식은 `db/backfill-2026-08-08-scheduler.sql` 참고.

### 5. 스케줄러를 추가한다면

`LeaveScheduler`와 같은 규칙을 따른다 — **`@Profile("!test")`로 크론을 막는다**
(`SchedulingConfig`를 막으면 `Clock` 빈이 사라져 테스트 컨텍스트가 깨진다).
`@EnableScheduling`은 이미 `SchedulingConfig`에 있으니 다시 선언하지 마라.

### 6. 수신자 규칙

docs/01 §2-3(45~46행)·§2-4(84행)를 읽고 그대로 따른다. 추가로:

- **퇴직자(`isActive == false`)에게 보내지 마라.**
- 이메일이 null/빈 문자열인 사원을 방어할 것
- **중복 제거** — 신청자가 곧 승인자이거나 primary==SYSTEM_ADMIN인 경우가 실제로 생긴다
- **사유(`requestReason`) 마스킹 (검증 Y-4)** — 본인·승인자·SYSTEM_ADMIN 외에는 사유가 보이면 안 된다.
  수신자별로 본문이 달라져야 한다면 그렇게 만들어라. 한 본문을 전원에게 뿌리지 마라.

### 7. 본문

**템플릿 엔진 의존성을 추가하지 마라**(Thymeleaf 없음). 문자열 조립으로 만들고, 제목·본문 생성은
한 곳에 모아라 — 7개 발송 지점이 각자 문자열을 만들면 문구 수정이 7곳 수정이 된다.
하드코딩 금지 규칙(`ResponseMessage`·`ErrorCode`)의 취지와 같다.

## 연결할 지점 7곳

- `LeaveService.java:124` 신청 · `:252` 승인/반려 · `:295` 취소 · `:325` 소급취소 처리
- `WelfareService.java:70` 신청 · `:128` 승인/반려
- `BirthdayLeaveGrantService.java:76` 생일 반차 지급 (당사자 + SYSTEM_ADMIN)

**해당 서비스의 기존 로직을 건드리지 마라.** 잔액 3필드(base/bonus/use)·`advance_days`에
손대는 코드가 이번 diff에 있으면 그건 틀린 것이다. 이벤트 발행 한 줄만 추가된다.

## 테스트

- **실제 SMTP를 때리는 테스트를 쓰지 마라.** `JavaMailSender`를 목으로.
- 반드시 덮을 것: ① 발송 실패해도 업무 트랜잭션이 커밋된다 ② 업무가 롤백되면 발송이 안 된다
  ③ 퇴직자가 수신자에서 빠진다 ④ 중복 수신자가 1건으로 접힌다
  ⑤ `retry_count >= 3`이면 재시도 대상에서 빠진다 ⑥ 계정 미설정이면 기동은 되고 FAILED로 기록된다
- ②는 `@Transactional` 테스트로는 못 잡는다(커밋이 안 일어난다). 어떻게 검증할지 근거를 써라.

## 코드 규칙 (CLAUDE.md·docs/04)

한국어 주석 · 일수는 `BigDecimal` · DTO는 record · 엔티티는 Setter 없이 정적 팩토리 ·
문자열 리터럴 하드코딩 금지 · 컨트롤러 try-catch 금지 · 본인 식별은 `@AuthenticationPrincipal AuthUser`.

## 산출물

샌드박스가 읽기 전용이다. **고치지 말고, 그대로 파일에 넣을 수 있는 전체 코드를 출력하라.**
파일마다 경로를 명시하고, 기존 파일 수정은 **바뀌는 부분의 앞뒤 맥락을 포함**해 어디에 넣는지 분명히 할 것.

마지막에 이 세 가지를 따로 써라:

1. **판단이 필요한 지점** — 네가 임의로 정한 것(재시도 주기, 스레드 풀 크기, 수신자 범위 해석 등)과 그 근거
2. **`.env.prod.example` / `application-example.yml`에 추가돼야 하는 항목**
3. **docs 정정이 필요한 곳** — 예: docs/01의 "Gmail SMTP 일 2,000건"은 Workspace 기준이라 개인 계정(500건)과 다르다

분량보다 정확성이다. 확인 못 한 것은 "확인 못 함"이라고 써라.
