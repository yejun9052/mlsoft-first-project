# D-2 N+1 잔여 전수 조사

docs/12 ⑥의 **D-2**가 "부분 해결"로 남아 있다. `@EntityGraph` 15건 / `@BatchSize` 2건이
붙어 있는데, 어디가 아직 열려 있는지 목록이 없어서 착수할 수 없는 상태다.
그 목록을 만들어라 — 역할 지시서의 산출물 형식 4단계를 그대로 따를 것.

## 대상 저장소 (전부)

```
backend/src/main/java/com/mlsoft/backend/domain/*/repository/*.java
```

빠짐없이 읽어라. 2026-08-10에 두 개가 늘었다:
- `AdminAuditLogRepository.search` — 신규 (감사 로그)
- `LeaveActionHistoryRepository.findByApprover` / `WelfareActionHistoryRepository.findByApprover` —
  `findByUserDepartmentId*`를 대체한 신규 쿼리

## 특히 확인해 줄 것

1. **`leave_dates` / `schedule_dates` 컬렉션**
   `LeaveRequest.dates`는 `@ElementCollection` + `@BatchSize(50)`다.
   페이징 목록(`findByUser`, `findForAdmin`, `findPendingForApprover`)에서 응답이 `dates`를
   읽는데, `@BatchSize`가 실제로 몇 쿼리로 줄이는지 숫자로 판정해라.
   `@EntityGraph`에 넣지 않은 이유는 페이징이 메모리로 가기 때문이다(§2 항목).

2. **`WelfareRequest`의 승인자가 FK 없는 raw `Long`이다** (D-5, 지금 수정 중).
   그래서 `WelfareResponse`가 승인자 **이름**을 어떻게 채우는지 확인해라.
   서비스가 별도 조회를 돌린다면 그게 목록 크기만큼 반복되는지 — FK로 바꾸면
   `@EntityGraph`로 합칠 수 있는 자리인지 판정해 달라.

3. **캘린더·팀 현황** (`findInDateRange`, `findByDepartmentInDateRange`)
   페이징이 없는 목록이다. 응답이 신청자·부서·날짜를 모두 읽는데 현재 대책이 무엇인지.

4. **처리 이력 3종**의 `@EntityGraph`가 `user.department`까지 넣고 있다.
   그 depth가 실제로 필요한지(응답이 부서명을 읽는지) 확인해라 — 불필요하면 조인 비용만 든다.

## 참고

- 인덱스 선언 위치: 엔티티 `@Table(indexes=)` + `db/backfill-2026-08-08-query-indexes.sql`
  (상단에 **넣지 않은 것과 이유**가 적혀 있다. 그 목록을 다시 제안하지 말 것)
- `application.yml`에 SQL 로깅 설정이 있으면 알려 달라 — 실측하려면 그걸 켜야 한다.
  단, **서버를 띄우지 말고** 설정 존재 여부만 확인할 것 (AGENTS.md 절대 규칙 3).
- 이 프로젝트 규모는 사원 수십~수백 명이다. "이론상 N+1"과 "실제로 아픈 N+1"을 §4에서 구분해라.
