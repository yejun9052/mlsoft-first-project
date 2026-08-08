# 작업: 스케줄러 3잡 테스트 작성 (docs/09 §8 필수 시나리오 7종)

방금 스케줄러 3잡을 구현했다. **docs/09 §8의 필수 테스트 7종**을 JUnit5 + Mockito로 작성해라.
파일을 고치지 말고 **적용 가능한 전체 테스트 코드를 파일 경로와 함께 출력**해라.

## 먼저 읽을 것 (구현이 이미 끝나 있다)

- `backend/src/main/java/com/mlsoft/backend/domain/leave/service/AnnualLeaveResetService.java`
- `.../domain/leave/service/MonthlyLeaveGrantService.java`
- `.../domain/leave/service/BirthdayLeaveGrantService.java`
- `.../domain/leave/scheduler/LeaveScheduler.java`
- `.../domain/user/entity/User.java` (`resetAnnualLeave` 4인자, `carryOverDebt`, `addMonthlyLeave`,
  `markMonthlyGranted`, `grantBirthdayLeave`, `monthlyGrantedCount`, `lastBirthdayGrantYear`)
- `.../domain/leave/entity/LeaveResetHistory.java` (`create` 5인자)
- `.../domain/leave/entity/LeaveRequest.java` (`daysOnOrAfter`)
- 기존 테스트 스타일 참고: `backend/src/test/java/.../domain/user/entity/UserTest.java`,
  `.../domain/leave/service/LeaveServiceTest.java`

## 시그니처 (이대로 호출해라 — 추측 금지)

```java
// AnnualLeaveResetService
List<Long> findTargetIds(LocalDate today)
int reset(Long userId, LocalDate today)            // 반환 = 처리한 회차 수
// 협력자: UserRepository, LeaveRequestRepository, LeaveResetHistoryRepository,
//         LeavePolicyService, PolicyConfigReader

// LeaveRequestRepository (default 메서드 — Mockito로 stub할 때 주의)
BigDecimal sumPreDeductedDaysOnOrAfter(User user, LocalDate from, Collection<RequestStatus> statuses)
List<Object[]> countDatesOnOrAfterByType(User user, LocalDate from, Collection<RequestStatus> statuses)

// MonthlyLeaveGrantService
List<Long> findTargetIds(LocalDate today)
int grant(Long userId, LocalDate today)            // 반환 = 적립 횟수

// BirthdayLeaveGrantService
List<Long> findTargetIds(LocalDate today)
boolean grant(Long userId, LocalDate today)
```

**중요**: `sumPreDeductedDaysOnOrAfter`는 인터페이스의 `default` 메서드다.
Mockito `@Mock`은 default 메서드도 stub할 수 있으니 그냥 `given(...)`으로 stub해라
(`countDatesOnOrAfterByType`를 stub하는 방식은 쓰지 마라 — 어느 쪽을 택했는지 명시할 것).

## 작성할 시나리오 (docs/09 §8 표 그대로)

| # | 시나리오 | 검증 대상 |
|---|---|---|
| 1 | 2년 밀린 리셋 | catch-up 반복 + 이력 2건, **회차마다 그 회차 기산일로 carriedUse 재집계** |
| 2 | 기산일을 걸친 신청(2/28~3/2) | 날짜 단위 재차감 — 새 use = 2.0 |
| 3 | 같은 날 두 번 실행 | 생일 반차 멱등성 |
| 4 | 생일 == 기산일 | 잡 실행 순서 (반차가 살아남는지) |
| 5 | 3개월 밀린 월차 | 반복 적립 + 상한 |
| 6 | 입사 전 생일 | 소급 지급 차단 |
| 7 | `bonus_carry_over_enabled` on/off | 이월 공식 (이미 쓴 보너스가 부활하지 않는지) |

여기에 **네가 직전 감사에서 지적한 것들**을 회귀 테스트로 추가해라:

- 8. `LeaveResetHistory` 이력 = 실제 전이 일치 — `base=15/use=20/bonus=0/carriedUse=20/policyBase=15`에서
  이력의 `newBaseDays=15`, `advanceSettled=0`, `expiredDays=15`인지 (예전 식이면 10/5/0이 나왔다)
- 9. I-10 복구 — `2/28·3/1·3/2` 신청이 3/1 리셋 후 **반려·즉시취소·소급취소승인 세 경로 각각**에서
  `use_days`가 음수가 되지 않는지 (`LeaveServiceTest`에 추가)
- 10. 월차 말일 클램프 — `hire_date = 1/31`인 사원의 2회차 적립일이 **3/31**인지 (2/28에서 한 달 더한 3/28이 아니라).
  `monthlyGrantedCount` 기반 계산이 실제로 드리프트를 막는지 고정하는 테스트다
- 11. 온보딩 소급분 이중 적립 차단 — `AuthService.completeOnboarding`이 `markMonthlyGranted`를 호출하므로
  같은 개월분이 스케줄러에서 다시 적립되지 않는지

## 규칙

- 한국어 `@DisplayName`. 테스트 메서드명도 기존 스타일(`apply_잔여부족시_예외`)을 따른다
- `BigDecimal` 비교는 `compareTo` (`assertEquals(0, expected.compareTo(actual))`)
- **각 테스트에 "왜 이걸 검증하는가"를 한 줄 주석으로** — 숫자가 왜 그 값인지 검산을 남겨라
- 파일 경로를 명시하고, 파일 단위로 완결된 코드를 출력해라 (import 포함)
- 기존 테스트를 깨뜨리는 변경이 필요하면 그 사실과 이유를 먼저 밝혀라
