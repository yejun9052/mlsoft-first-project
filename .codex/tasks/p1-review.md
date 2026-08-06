# 작업: 1순위 수정(advance_days 파생값화) 변경분 리뷰

## 리뷰 범위 — 아래 파일만 본다

프로덕션:
- `backend/src/main/java/com/mlsoft/backend/domain/user/entity/User.java`
- `backend/src/main/java/com/mlsoft/backend/domain/leave/service/LeaveService.java`
- `backend/src/main/java/com/mlsoft/backend/domain/leave/entity/LeaveRequest.java`

테스트:
- `backend/src/test/java/com/mlsoft/backend/domain/user/entity/UserTest.java` (신규)
- `backend/src/test/java/com/mlsoft/backend/domain/leave/service/LeaveServiceTest.java`
- `backend/src/test/java/com/mlsoft/backend/domain/user/service/UserServiceTest.java`
- `backend/src/test/java/com/mlsoft/backend/domain/welfare/service/WelfareServiceTest.java`

**`frontend/`와 `docs/` 변경분은 이 리뷰 범위가 아니다** — 같은 작업 트리에 다른 작업(디자인 개편)이
섞여 있으니 무시할 것.

## 수정 내용 요약

`advance_days`를 파생값으로 바꿨다. `User.syncAdvanceDays()` 하나만 이 필드에 쓰고,
`deductLeave` / `restoreLeave` / `addBonusDays` / `updateBaseDays` / `addMonthlyLeave`가 마지막에 호출한다.
`resetAnnualLeave`는 의도적 예외다 (docs/09 §5).

`restoreLeave(days, advanceUsedDays)` → `restoreLeave(days)`로 줄였고, `LeaveRequest.advanceUsedDays`는
감사 기록 전용으로 격하했다.

## 이미 확인된 것 (다시 보고하지 말 것)

- 전체 테스트 100개 통과 (기존 84 + 신규 16)
- `syncAdvanceDays` 본문을 주석 처리하면 13개가 실패한다 — 테스트가 이 로직에 실제로 의존함을 확인
- `restoreLeave` 호출부 3곳(LeaveService 219·292·314) 전부 1인자로 수정 완료

## 특별히 봐야 할 것

1. `deductLeave`의 반환값이 **모든 입력에서** 기존 구현과 같은가.
   특히 `days`가 0인 경우, `remaining`이 정확히 `days`와 같은 경계, `bonusDays`가 null인 경우
2. `bonusDays`가 **null**인 사원(`@Column` nullable)에서 `syncAdvanceDays`가 NPE 없이 도는가.
   `getRemainingDays()`의 null 방어를 타는지 확인
3. `BigDecimal` scale — `max(BigDecimal.ZERO)`가 scale 0인 `ZERO`를 반환할 수 있다.
   `DECIMAL(4,1)` 컬럼 저장, JSON 응답(`LeaveSummaryResponse`·`UserResponse`), 프론트 표시에
   `0` vs `0.0` 차이가 문제를 일으키는가
4. `deductLeave`에서 예외를 던지기 전에 상태가 변경되는 부분이 남아 있는가 (부분 변경 후 롤백 의존)
5. `advanceUsedDays`를 읽는 다른 코드가 남아 있는가 — 복구 계산에 아직 의존하는 곳이 있으면 치명
6. 테스트의 기대값이 불변식과 일치하는가. 잘못된 기대값을 통과시키고 있지 않은가
