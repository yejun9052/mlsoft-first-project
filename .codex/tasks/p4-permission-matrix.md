# 작업: 권한 게이트 실측 인벤토리 (T-1 선행)

컨트롤러·security 테스트가 **0개**다. `JwtFilter`·`OnboardingCheckInterceptor`·`@PreAuthorize`가
한 번도 검증된 적이 없다 — 권한 게이트가 실제로 막는지 확인된 바 없다는 뜻이다.

테스트를 쓰기 전에 **무엇을 고정해야 하는지부터 실측**해라. 코드를 고치지 말고 표로 답해라.

## 읽을 것

- `backend/src/main/java/com/mlsoft/backend/security/` 전체
  (`JwtFilter`, `OnboardingCheckInterceptor`, `SecurityConfig`, `AuthUser`,
   `RestAuthenticationEntryPoint`, `RestAccessDeniedHandler`)
- `config/SecurityConfig.java`, `config/WebConfig.java` (인터셉터 등록 경로 패턴)
- `domain/**/controller/*.java` 전부 (엔드포인트 + `@PreAuthorize`)

## 산출물

### 1. 엔드포인트 × 역할 접근 매트릭스

모든 엔드포인트를 한 줄씩. 열은 이렇게:

| 메서드 + 경로 | 컨트롤러:줄 | `@PreAuthorize` | 미인증 | EMPLOYEE | TEAM_LEADER | SYSTEM_ADMIN | 온보딩 미완료 | 퇴직자 |

각 칸은 `허용` / `401` / `403` 중 하나로. **코드에서 읽은 사실만** 쓰고, 애매하면 "미확인".

### 2. 게이트가 3겹인 구간과 그 순서

요청 하나가 `SecurityConfig` 경로 규칙 → `JwtFilter` → `@PreAuthorize` →
`OnboardingCheckInterceptor`를 어떤 순서로 지나는지 확정해라.
**인터셉터가 `@PreAuthorize`보다 먼저인지 나중인지**가 중요하다 —
403의 원인이 역할인지 온보딩인지가 그 순서로 갈린다. Spring MVC의 실제 실행 순서를 근거로 답해라.

### 3. 실측된 구멍 (가장 중요)

아래를 **각각 판정**해라. 있으면 재현 경로를, 없으면 왜 막히는지를 파일:줄로.

- `@PreAuthorize`가 빠진 관리자 전용 엔드포인트가 있는가
- 소유권 검증(본인 것만)이 서비스 계층에 없는 경로가 있는가 —
  CLAUDE.md는 "`@PreAuthorize`는 역할 게이트 전용, 소유권은 서비스 책임"이라고 못박고 있다.
  그 약속이 실제로 지켜지는지 엔드포인트별로 확인해라
- 퇴직자(`is_active=false`)가 통과할 수 있는 경로가 있는가 (인터셉터 제외 패턴 포함)
- 온보딩 미완료자가 `/api/auth/*` 외에 도달할 수 있는 경로가 있는가
- JWT의 role과 DB role이 다를 때 재구성이 **모든 경로에서** 일어나는가
- 인터셉터가 등록되지 않은 `/api/**` 하위 경로가 있는가 (`WebConfig`의 패턴과 실제 컨트롤러 경로 대조)

### 4. 테스트로 고정할 우선순위 10개

위에서 찾은 것 중 **회귀하면 가장 위험한 순서**로 10개. 각 항목에
`@WebMvcTest`와 `@SpringBootTest + MockMvc` 중 어느 쪽이 맞는지와 그 이유를 한 줄로.
(`@WebMvcTest`는 인터셉터가 DB를 보므로 리포지토리 목킹이 필요하다 — 그게 맞는 선택인지 판단해라.)

추측은 쓰지 마라. 확인한 파일·줄만 근거로 쓰고, 확인 못 한 건 "미확인"이라고 명시해라.
