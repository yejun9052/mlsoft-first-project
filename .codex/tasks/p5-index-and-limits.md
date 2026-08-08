# 작업: 조회 상한(S-4)과 인덱스(D-1) 실측 설계

두 항목 모두 "코드 0건"이라 실측부터 필요하다. 코드를 고치지 말고 **적용 가능한 코드/DDL을 출력**해라.

## 1. S-4 — 조회 상한 부재

`size=100000` 요청이 그대로 통과한다(실측). 페이징 파라미터를 받는 엔드포인트 전부를 찾아라.

- `@PageableDefault`가 붙은 곳 / 안 붙은 곳을 구분해 표로
- Spring Boot 4에서 전역 상한을 거는 정확한 방법을 코드로 제시해라
  (`spring.data.web.pageable.max-page-size` 프로퍼티가 맞는지, 아니면 `PageableHandlerMethodArgumentResolver`
   커스터마이징인지 — **추측하지 말고 이 프로젝트의 의존성 버전 기준으로** 확인해라)
- **상한을 넘겼을 때 400을 던질지 조용히 잘라낼지** 판단하고 근거를 대라.
  이 프로젝트는 "값 검증은 저장 시점에, 읽기는 실패시키지 않는다"는 원칙이 있다(PolicyConfigReader).
  조회 파라미터에는 어느 쪽이 맞나?
- 페이징을 쓰지 않고 `List`를 통째로 돌려주는 엔드포인트도 찾아라 —
  거기가 진짜 구멍이다(상한을 걸 자리조차 없다). 예: 캘린더·팀 현황·부서 트리.
  각각 실제로 몇 행까지 커질 수 있는지 근거와 함께 판정해라.

## 2. D-1 — 인덱스 선언 0건

`@Table(indexes = ...)`가 하나도 없다. **실제 쿼리를 근거로** 필요한 인덱스를 정하라.

- `backend/src/main` 전체의 `@Query`·derived query·`@ElementCollection` 조인을 훑어
  **WHERE/ORDER BY에 실제로 쓰이는 컬럼 조합**을 뽑아라
- 그중 데이터가 쌓였을 때 느려질 것만 고른다. 행이 수십 개로 끝나는 테이블
  (`department`, `leave_policy`, `leave_policy_config`)은 제외하고 이유를 적어라
- **스케줄러가 매일 도는 3개 쿼리**(`findIdsDueForAnnualReset`, `findIdsUnderOneYear`,
  `findIdsWithoutBirthdayLeave`)와 리셋의 `countDatesOnOrAfterByType`도 반드시 포함해 판정해라
- 각 인덱스에 대해: 대상 쿼리(파일:줄), 컬럼 순서와 **그 순서인 이유**(선택도·범위 조건 위치),
  이미 있는 인덱스(FK 자동 생성분·UNIQUE)와 중복되지 않는지
- 산출물은 두 벌: (a) 엔티티 `@Table(indexes=...)` 코드, (b) `db/` 마이그레이션 SQL
  (`information_schema` 가드로 멱등하게 — 기존 backfill 파일들의 패턴을 따라라)

## 주의

- `users` 테이블은 방금 컬럼 3개가 늘었다(`monthly_granted_count`, `last_birthday_grant_year`,
  `onboarding_status`). 스케줄러·온보딩 승인 쿼리가 이들을 조건으로 쓴다
- MySQL 8 기준. `db/schema.sql`에 현재 스키마가 있으니 기존 키와 대조해라

## 산출물 형식

```
## 1. S-4 — 페이징 엔드포인트 인벤토리
## 2. S-4 — 상한 없는 List 반환 엔드포인트 (위험도 순)
## 3. S-4 — 적용 코드
## 4. D-1 — 필요한 인덱스 (쿼리 근거 포함)
## 5. D-1 — 적용 코드 + 마이그레이션 SQL
## 6. 넣지 않기로 한 인덱스와 그 이유
```

확인 못 한 건 "미확인"이라고 명시해라. 추측으로 인덱스를 늘리지 마라 —
쓰이지 않는 인덱스는 쓰기 비용만 늘린다.
