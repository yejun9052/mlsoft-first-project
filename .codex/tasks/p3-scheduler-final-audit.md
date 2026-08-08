# 작업: 스케줄러 구현 후 잔액 불변식 재감사

스케줄러 3잡 구현 + 테스트가 끝났다(백엔드 200 그린). 커밋 전 마지막으로
**잔액 불변식 (B)가 새 경로 전부에서 유지되는지** 다시 감사해라.

`git status --short` / `git diff` 로 변경 전체를 보고, 신규 파일도 읽어라.

## 이번에 새로 생긴 경로 (전부 검사할 것)

- `User.carryOverDebt(carriedUse)` — 새 public 메서드. `resetAnnualLeave`와
  `LeaveResetHistory.create`가 공유한다. **호출 시점이 리셋 직전이 아니면 값이 틀리는데,
  그걸 강제하는 장치가 있는가?** 없다면 오용 가능한 시나리오를 숫자로 제시해라
- `User.addMonthlyLeave()` — `monthlyGrantedCount += 1`이 추가됐다. base와 카운터가
  어긋날 수 있는 경로가 있는가 (예외·롤백·부분 실패)
- `User.markMonthlyGranted(int)` — 카운터만 바꾸고 `syncAdvanceDays()`를 부르지 않는다.
  **이게 (B)를 깨는가?** 판정하고 근거를 대라
- `User.grantBirthdayLeave(days, year)` — `addBonusDays` 위임
- `LeaveRequest.daysOnOrAfter(boundary)` — 복구량 계산
- `AnnualLeaveResetService.applyOneRound` — 이력 저장 → 상태 전이 순서
- `LeaveService.restoreCurrentYearPortion`

## 반드시 숫자로 답할 것

1. **`markMonthlyGranted`가 (B)를 깨는가** — 깨면 시나리오, 안 깨면 왜 안 깨는지
2. **catch-up 2회차에서 `carryOverDebt`가 1회차 결과 위에서 정확한가** —
   `base=15, use=5, bonus=0`인 사원이 2회 리셋될 때 4필드를 회차별로 계산해 제시
3. **`daysOnOrAfter`와 리셋의 `carriedUse` 집계 기준이 정확히 같은가** —
   한쪽은 `!date.isBefore(boundary)`(자바), 다른 쪽은 `d >= :from`(JPQL)이다.
   경계 날짜가 양쪽에서 같게 취급되는가? 다르면 use_days가 어긋나는 수치를 제시해라
4. **월차 상한을 낮춘 뒤(11 → 5) 이미 8회 받은 사원**에게 어떤 일이 일어나는가.
   `while (count < max)` 조건이므로 회수는 안 되는데, 그 상태가 (B)를 깨는가

## 산출물

역할 지시서의 4개 섹션 형식 그대로. 단 §1 인벤토리는 **이번에 새로 생기거나 바뀐 지점만**
표에 넣어라(기존 지점은 직전 감사에서 이미 확인했다).

문제가 없으면 없다고 하고 억지로 만들어내지 마라. 확인 못 한 건 "미확인"이라고 명시해라.
