# 리뷰 대상: 2026-08-10 커밋 4건 (S-2 · S-3 · I-7 · S-6)

이번엔 워킹 트리가 깨끗하다. 아래 **커밋 범위**를 변경분으로 보고 리뷰해라.

```
git diff 14bb7a6..HEAD           # 전체 변경분
git diff --stat 14bb7a6..HEAD    # 범위 확인
```

커밋 4건:

| 커밋 | 항목 | 요지 |
|---|---|---|
| `6475aaf` | S-2 | 마지막 SYSTEM_ADMIN 강등·퇴직 차단 |
| `aaf7da7` | S-3 | 관리자 조작 감사 로그 신설 (`domain/audit`) |
| `fbb9819` | I-7 | 경계값 5건 — 하루 정원 규칙·승인자 중복·@Size·미래 입사일 |
| `ae2be47` | S-6 | 처리 이력 스코프를 결재자 기준으로 (`my-team` → `my-approvals`) |

## 특히 확인해 줄 것

우선순위 순이다. 위쪽에 시간을 더 써라.

1. **하루 정원 1.0일 규칙** (`LeaveService.validateNoDateConflict`)
   - 반차 0.5 + 반차 0.5 = 1.0은 통과, 같은 종류 중복은 거부가 의도다.
   - `BigDecimal` 비교에 scale 문제가 없는지 (`0.5+0.5`와 `1.0`의 `compareTo`).
   - `findOverlapping`이 distinct **신청** 단위로 오는데 날짜 교집합을 자바에서 다시 좁힌다.
     이 좁히기가 빠지거나 잘못된 경로가 있는가.
   - **취소·반려로 복구된 뒤 같은 날 재신청**이 가능한가 (`ACTIVE_STATUSES`에서 빠지는지).
   - 같은 요청 안에 **같은 날짜가 두 번** 들어오면? (`dates`에 중복이 있는 경우)

2. **`validateNoDateConflict` 호출 위치**
   - 예전에는 `findOverlapping` 검사가 승인자 결정 **뒤**였다. 지금도 그 순서인지,
     그리고 **차감 전**인지 확인해라. 예외 전에 상태를 바꾸면 롤백에 의존하게 된다.

3. **감사 기록의 트랜잭션 경계** (`AdminAuditService`)
   - 조작과 같은 트랜잭션에 참여하는 것이 의도다(롤백 시 기록도 사라짐).
   - `@Transactional`이 붙은 `record*`를 같은 클래스가 아닌 **다른 서비스**가 부르므로
     프록시를 타는 것이 맞는데, 전파가 실제로 REQUIRED로 합쳐지는지.
   - `userRepository.getReferenceById(actorId)`로 프록시만 잡는다. 이 프록시가
     `AdminAuditLog.create`의 `actor`로 들어가 insert될 때 문제가 없는지.
   - `UserService.retire`는 `target.retire()` **뒤에** 감사를 기록한다.
     `target.getRetiredAt()`이 그 시점에 채워져 있는가.

4. **S-6 쿼리** (`findByApprover` 2개)
   - `h.leaveRequest.primaryApprover.id`가 만드는 암묵 조인이 `INNER`인지,
     그래서 빠지는 행이 있는지. `subApprover`는 nullable이다 — `or` 조건에서 null 처리.
   - `@EntityGraph` + `@Query` 병용이 페이징 count 쿼리를 깨지 않는지.
   - welfare 쪽은 승인자가 FK 없는 raw `Long`이다(D-5, 아직 미수정). 그 차이가 문제가 되는가.

5. **S-2 가드**
   - `changeRole`에서 `role != SYSTEM_ADMIN`일 때만 검사한다.
     `TEAM_LEADER`인 대상을 `EMPLOYEE`로 바꾸는 경우처럼 **관리자가 아닌 대상**에서
     불필요한 카운트 쿼리가 돌지 않는지, 반대로 막아야 할 경로가 새지 않는지.
   - 카운트에 `onboardingStatus = COMPLETED`를 넣었다. 이게 **너무 엄격**해서
     정상적으로 강등 가능한 상황을 막는 경우가 있는가.

6. **미래 입사일** (`AuthService.completeOnboarding`)
   - `@PastOrPresent`를 DTO에서 **제거**하고 서비스에서 KST로 판정하게 바꿨다.
   - 이 제거로 **다른 경로**(온보딩 승인 `OnboardingApprovalService.approve`)에서
     미래 입사일이 들어올 구멍이 생기는가. 승인은 이미 저장된 `hire_date`를 쓴다.

7. **누락된 호출부** — 시그니처가 바뀐 것들:
   `UserService.changeRole/changeDepartment/updateBaseDays/retire`(actorId 추가),
   `LeavePolicyConfigService.update`(actorId 추가), `ApproverResolver.resolveSub`(primary 추가).
   프로덕션·테스트·`DemoDataInitializer`·`DataInitializer` 전부 따라왔는가.

## 참고

- `db/schema.sql`은 신규 테이블 블록만 삽입했다(전체 재덤프 안 함). 근거는 파일 헤더에 있다.
- `db/backfill-2026-08-10-audit-log.sql`이 배포 전 실행 대상이다.
- 테스트는 백엔드 264 · 프론트 39 전부 그린인 상태다. **테스트가 통과한다고 옳다는 뜻은 아니다** —
  테스트 자체가 틀린 것을 고정하고 있으면 그걸 지적해라.
