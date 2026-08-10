# 불변식 재감사 — 하루 정원 규칙과 감사 로그가 들어온 뒤

2026-08-10에 잔액 경로를 건드리는 변경이 들어왔다. 불변식 (B)가 여전히 모든 경로에서
유지되는지 **전수 재감사**해라. 역할 지시서의 산출물 형식 4단계를 그대로 따를 것.

## 이번에 바뀐 것 (감사 시 반드시 포함)

1. **`LeaveService.validateNoDateConflict` 신설** (I-7)
   - 하루 정원 1.0일 규칙. 같은 날 오전 반차 + 오후 반차를 허용하게 됐다.
   - **이것이 잔액에 주는 영향을 확인해라**: 같은 날짜에 신청 2건(각 0.5)이 존재할 수 있게
     됐으므로, 그 날짜가 걸린 복구·리셋 계산이 날짜를 **중복 계상**하지 않는지.
     특히 `LeaveRequest.daysOnOrAfter`와
     `LeaveRequestRepository.sumPreDeductedDaysOnOrAfter`가 `leave_dates`를 조인하는데,
     한 날짜에 두 신청이 걸리면 합계가 어떻게 되는가. 숫자로 검산할 것.
   - 기산일 리셋의 `carriedUse` 집계도 같은 관점으로 볼 것 (docs/09 §5).

2. **`ApproverResolver.resolveSub` 시그니처 변경** (I-7) — 잔액과 무관해 보이지만
   신청 흐름의 예외 발생 시점이 바뀌었다. **차감 전에 던지는지** 확인.

3. **`AdminAuditService` 신설** (S-3)
   - `UserService.updateBaseDays`가 `target.updateBaseDays(...)` **뒤에** 감사를 기록한다.
     `before`를 대입 전에 읽는지, 그리고 감사 기록이 `syncAdvanceDays` 결과를 흔들지 않는지.
   - `OnboardingApprovalService.approve`가 `grantInitialLeave` **뒤에** `user.getBaseDays()`를
     읽어 기록한다. 그 값이 부여 후 값인지 확인.

## 그 외 (기존 항목 재확인)

- `User`의 잔액 3필드를 바꾸는 도메인 메서드 6개가 마지막에 `syncAdvanceDays()`를 부르는지
  (`deductLeave`/`restoreLeave`/`addBonusDays`/`updateBaseDays`/`addMonthlyLeave`/`resetAnnualLeave`).
- `advance_days`에 **단독 대입**하는 코드가 새로 생기지 않았는지.
- 스케줄러 3잡(`AnnualLeaveResetService`/`MonthlyLeaveGrantService`/`BirthdayLeaveGrantService`)에서
  (B)가 유지되는지.
- `db/backfill-*.sql`의 값 보정 UPDATE가 (B)를 깨는 값을 넣지 않는지.

## 요청

§3(자동 재계산이 위험한 경로)에서 **반차 2건이 같은 날에 걸린 경우**를 새 시나리오로 추가해
숫자 검산을 붙여라. 이게 이번 변경의 핵심 위험이다.
