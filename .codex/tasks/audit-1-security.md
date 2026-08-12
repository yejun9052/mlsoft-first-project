# 1차 테스트 — 보안 감사

배포 전 마지막 점검이다. 사용자가 직접 브라우저로 확인하기 전에 **코드에서 뚫리는 곳**을 먼저 찾는다.

## 지금 상태

- 백엔드 엔드포인트 64개 / 컨트롤러 15개, 엔티티 14개, 자동 테스트 278건 전부 통과
- **오늘(2026-08-12) 이메일 건별 알림이 들어갔다** — `domain/email/{event,service,scheduler}`,
  `EmailNotificationPublisher`가 7개 지점에서 이벤트를 발행한다. **이 신규 코드를 우선 봐라.**
- 아직 미구현: 관리자 일괄 발송(docs/01 §2-8), Google Workspace `hd` claim 검증(docs/08 R-2)

## 특히 볼 것

### 이메일 신규 코드 (오늘 들어감 — 아무도 감사한 적 없다)
- `EmailNotificationPublisher`가 수신자를 고르는 규칙. **연차 사유(`requestReason`)·취소 사유가
  볼 자격 없는 사람의 메일함에 들어가는 경로가 있는가.** `reasonVisible` 플래그가 실제로
  분기를 만드는가, 아니면 항상 true라 무의미한가.
- 승인 알림이 **재직 중 SYSTEM_ADMIN 전원**에게 간다. 이 목록에 들어가면 안 되는 사람이 섞이는가.
- `EmailDeliveryService`의 실패 메시지가 `email_history.error_message`에 저장된다.
  **여기에 시크릿·계정 정보·개인정보가 들어갈 수 있는가** (SMTP 예외 메시지에 계정이 실릴 수 있다).

### 인가 경계
- 컨트롤러 15개를 전부 훑어 **소유권·승인자 검증이 서비스에 실제로 있는지** 확인하라.
  `@PreAuthorize`만 있고 서비스가 안 보는 엔드포인트가 하나라도 있으면 🔴이다.
- `OnboardingCheckInterceptor`의 제외 경로와 `SecurityConfig`의 `permitAll` 목록을 대조해
  **가드 밖에 있는 `/api/**` 경로**를 찾아라.
- 퇴직자·강등자가 이미 발급받은 쿠키로 할 수 있는 일이 남아 있는가.

### 마스킹
- 연차 사유·`ScheduleEntry` 메모의 마스킹 기준이 **모든 응답 경로에서 같은가** —
  목록·상세·캘린더·이력·통계·엑셀/CSV(있다면)·이메일 본문.
  한 곳이라도 기준이 다르면 그게 구멍이다.

### 시크릿
- `git ls-files`로 추적 중인 파일 중 시크릿이 들어갈 수 있는 것. `.gitignore`와 `.dockerignore`의
  **차집합**을 확인하라 — 한쪽에만 있으면 운영 이미지에 박힌다 (리뷰 O-4).
- **파일 내용을 열지 말고** 파일명·추적 여부만 본다.

## 읽어야 할 것

`backend/src/main/java/com/mlsoft/backend/` 전체, 특히
`security/`, `config/SecurityConfig.java`, `config/WebConfig.java`,
`domain/*/controller/`, `domain/email/`, `global/`.
기준 문서는 `docs/01`(권한 체계), `docs/03`(API 규칙), `docs/08 R-2·Y-4`.

## 산출물

역할 지시서의 형식을 그대로 따른다. **1차 테스트 보고서에 그대로 붙일 것이므로**
제목·심각도 표기를 일관되게 써라.
