# 작업: 보안 통합 테스트 작성 (T-1)

직전 감사(`.codex/out/diff-reviewer-0808-135417.md`)에서 권한 게이트는 전부 정상으로 확인됐다.
문제는 **테스트가 0개**라 회귀를 못 잡는다는 것이다. 그 감사 §4가 뽑은 10개를 실제 코드로 써라.

파일을 고치지 말고 **적용 가능한 전체 테스트 코드를 파일 경로와 함께 출력**해라.

## 방식 — `@SpringBootTest` + `MockMvc` (감사 §4 판단 그대로)

`@WebMvcTest`는 인터셉터가 의존하는 `UserRepository`를 목킹해야 하고, 실제 필터 체인·인터셉터·
메서드 보안의 **실행 순서**를 고정하기 어렵다. 이 테스트의 요지가 바로 그 순서이므로 통합으로 간다.

```java
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class SecurityAccessMatrixTest { ... }
```

## 인증을 어떻게 넣을지 먼저 정해라

JWT는 HttpOnly 쿠키로 들어온다(`TokenCookieFactory`·`JwtFilter`·`JwtProvider` 확인).
**실제 쿠키를 만들어 요청에 실어라** — `@WithMockUser`를 쓰면 `JwtFilter`를 건너뛰어
정작 검증하려는 것(토큰 파싱·DB 상태 재조회)이 빠진다.

`JwtProvider`를 주입받아 실제 토큰을 발급하고 `.cookie(new Cookie(...))`로 싣는 방식을 권한다.
쿠키 이름은 코드에서 확인해라(하드코딩하지 말고 상수를 참조).

기존 통합 테스트의 사용자 저장 패턴은
`LeaveServiceIntegrationTest.saveUser`를 참고해라 — 이메일 도메인을 `@integration.test`로
분리하는 이유가 주석에 있다(같은 H2를 공유해 시더 테스트의 개수 단언이 흔들렸다).

## 고정할 10개 (감사 §4)

1. 미인증 사용자가 일반 `/api/**` → `401` + `CommonResponse` 실패 형식
2. 만료·위조 JWT → `401`
3. EMPLOYEE가 SA 전용 엔드포인트 → `403`
4. **토큰 SA · DB EMPLOYEE(강등) → 같은 요청에서 즉시 `403`**
5. **토큰 EMPLOYEE · DB SA(승격) → 같은 요청에서 허용**
6. 퇴직자가 `/api/auth/logout` → 허용 + 만료 쿠키 반환
7. 퇴직자가 `/api/auth/me`·일반 API → `403`
8. 온보딩 미완료자가 `/api/auth/me`·`/onboarding` → 허용, `/api/leaves` → `403`
9. TL이 **자신이 승인자가 아닌** 연차·복리후생 승인 호출 → `403`
10. 타인의 연차 취소·복리후생 취소·일정 수정/삭제·연차 이력 조회 → 각각 `403`

4·5번이 이 시스템의 특징적인 동작이라 가장 중요하다 —
`OnboardingCheckInterceptor`가 매 요청 DB role로 SecurityContext를 재구성하는 것이 실제로 되는지.

## 규칙

- 한국어 `@DisplayName`. **각 테스트에 "이게 깨지면 무엇이 뚫리는가"를 한 줄 주석으로**
- 응답 본문은 `{ success, message, data }` 래핑이다 — status만 보지 말고 형식도 확인해라(1번)
- 파일이 커지면 나눠도 된다. 나눌 때 기준을 밝혀라
- import 포함 완결된 파일로 출력. 여러 파일이면 파일마다 경로를 명시
- **실행 안 해도 되지만, 컴파일이 안 될 만한 추측은 하지 마라.** 시그니처는 반드시 코드에서 확인하고,
  확인 못 한 건 그 자리에 "미확인: <무엇>"이라고 적어라
