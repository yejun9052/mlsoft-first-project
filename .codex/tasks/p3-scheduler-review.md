# 작업: 스케줄러 3잡 구현 diff 리뷰

방금 docs/09 스케줄러 3잡을 구현했다. **아직 커밋하지 않은 변경 전체**(`git diff` + untracked)를 리뷰해라.

```
git status --short
git diff
```

untracked 신규 파일도 반드시 읽어라:
- `backend/src/main/java/com/mlsoft/backend/config/SchedulingConfig.java`
- `.../domain/leave/service/AnnualLeaveResetService.java`
- `.../domain/leave/service/MonthlyLeaveGrantService.java`
- `.../domain/leave/service/BirthdayLeaveGrantService.java`
- `.../domain/leave/scheduler/LeaveScheduler.java`
- `db/backfill-2026-08-08-scheduler.sql`

## 중점적으로 볼 것 (이 순서로)

1. **트랜잭션 경계가 실제로 걸리는가.** docs/09 §7은 "사원 1명당 1트랜잭션(`REQUIRES_NEW`),
   진입점에는 `@Transactional` 없음"을 요구한다.
   - `LeaveScheduler.runPerUser`가 서비스 빈을 프록시 경유로 부르는 게 맞는지
   - `findTargetIds`(readOnly)와 `reset`(REQUIRES_NEW)이 **다른 트랜잭션**인 사이에
     대상이 바뀌었을 때 어떻게 되는지 (가드가 트랜잭션 안에서 다시 검사되는가)
   - `AnnualLeaveResetService.reset`의 while 루프 전체가 한 트랜잭션인데, 그 안에서
     `leaveRequestRepository.sumPreDeductedDaysOnOrAfter`가 회차마다 불린다.
     **1회차의 `user.resetAnnualLeave()`가 flush되기 전에 2회차 쿼리가 나가면** 결과가 틀리는가?
     (JPA flush 모드·dirty checking 관점에서 판정해라. 이 항목이 가장 중요하다.)

2. **`LeaveRequestRepository.sumPreDeductedDaysOnOrAfter`의 JPQL.**
   `select lr.leaveType, count(d) from LeaveRequest lr join lr.dates d ... group by lr.leaveType`
   - `@ElementCollection` 조인에서 `count(d)`가 **날짜 개수**가 맞는지 (중복 계상 위험)
   - 같은 사원이 같은 날짜를 두 신청에 갖는 것이 가능한지 (`findOverlapping`이 막는지)
   - `Object[]` 캐스팅이 Hibernate 6/7에서 안전한지

3. **경계 조건.** 숫자로 반례를 만들어 봐라.
   - `MonthlyLeaveGrantService`: `hire_date = 2/29`(윤년)인 사원, 상한을 12로 올린 경우,
     `monthlyGrantedCount`가 상한보다 이미 큰 경우
   - `BirthdayLeaveGrantService`: 생일 == 오늘 == 입사일, 12/31 생일, `withYear` 2/29 보정
   - `AnnualLeaveResetService`: `lastResetDate > today`(미래)인 데이터, `hireDate > lastResetDate`인 데이터

4. **`LeaveService.restoreCurrentYearPortion` (I-10).**
   세 경로가 하나를 쓰는지, `daysOnOrAfter`가 `advanceUsedDays` 감사 기록과 충돌하지 않는지,
   `lastResetDate`가 null인 경로가 실제로 도달 가능한지.

5. **`db/backfill-2026-08-08-scheduler.sql`이 멱등한지**, MySQL 8 문법이 맞는지,
   `DATE_FORMAT(birth_day, '%m-%d') <= DATE_FORMAT(CURDATE(), '%m-%d')` 비교가 문자열 비교로
   의도대로 동작하는지.

6. **CLAUDE.md·docs/04 컨벤션 위반** — BigDecimal `==`, 문자열 리터럴 하드코딩, Setter,
   `ErrorCode` 미사용, 주석 한국어 여부.

## 산출물

```
## 🔴 반드시 고쳐야 할 것
## 🟡 고치는 편이 나은 것
## 🟢 확인했고 문제 없는 것 (한 줄씩)
```

각 항목은 **파일:줄 → 무엇이 틀렸는지 → 재현 시나리오(숫자) → 적용 가능한 수정 코드** 순으로.
"~할 수 있다" 같은 추측은 쓰지 말고, 확인 못 한 건 "미확인"이라고 명시해라.
문제가 없으면 없다고 하고 억지로 만들어내지 마라.
