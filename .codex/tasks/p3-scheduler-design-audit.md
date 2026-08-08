# 작업: 스케줄러 3잡 착수 전 설계 감사 (docs/09)

지금부터 `docs/09-스케줄러-설계.md`대로 스케줄러 3잡(기산일 리셋 / 월차 적립 / 생일 반차)을
구현하려 한다. **코드를 쓰기 전에** 설계가 잔액 불변식을 깨는 지점이 있는지 감사해라.

읽어야 할 것:
- `docs/09-스케줄러-설계.md` 전체 (특히 §4·§5·§6, §5의 I-11 정정 블록)
- `backend/src/main/java/com/mlsoft/backend/domain/user/entity/User.java`
  (`resetAnnualLeave` 4인자 오버로드가 I-11 결정을 이미 구현하고 있다)
- `domain/leave/entity/LeaveResetHistory.java`
- `domain/leave/entity/LeaveRequest.java`, `domain/leave/service/LeaveService.java`

## 반드시 숫자로 판정할 것 (추측 금지)

1. **catch-up 다회 리셋과 `carriedUse`의 상호작용.**
   §6은 `last_reset_date + 1년 <= 오늘`인 동안 while 루프로 리셋을 반복한다.
   2년 밀린 사원에게 리셋이 2회 도는데, **각 회차의 `carriedUse`를 무엇으로 잡아야 하나?**
   - 1회차는 "1차 기산일 이후 날짜", 2회차는 "2차 기산일 이후 날짜"인가?
   - 아니면 두 회차 모두 "최종 기산일 이후"인가?
   두 해석의 결과를 `base/use/bonus/advance` 4필드로 각각 계산해서 비교하고, 어느 쪽이
   `Σ부여 − Σ사용` 항등식을 만족하는지 판정해라. **이게 가장 중요한 항목이다.**

2. **`LeaveResetHistory.create()`가 I-11 결정과 어긋나지 않는지.**
   현재 `newBaseDays(newBaseDays.subtract(user.getAdvanceDays()))`로 기록하는데,
   I-11 결정은 `newBase = 정책연차 − oldYearDebt`다. `carriedUse > 0`이면 이 둘이 갈린다.
   갈리는 구체 수치를 제시하고, 이력 기록이 실제 전이와 달라지는지 판정해라.

3. **①리셋 → ②월차 순서에서 월차가 리셋된 base에 더해질 때.**
   `addMonthlyLeave()`는 `base += 1`이다. 리셋으로 `base`가 음수(=빚)인 사원에게
   월차가 적립되면 어떻게 되나? 1년 미만인데 리셋이 도는 경우가 존재하는가?
   (`last_reset_date`는 온보딩 시 `hire_date`로 세팅된다 — 조건이 겹치는지 확인)

4. **I-10 (기산일 횡단 신청의 복구 과다).**
   `2/28~3/2` 3일 신청이 리셋(3/1) 후 취소될 때 `LeaveService`의 복구 경로가
   `request.getDays()` 전체를 되돌리는 지점을 **파일:줄로** 특정해라.
   복구량을 "기산일 이후 날짜 수 × 단가"로 좁힐 때, `user.getLastResetDate()`를 기준으로 하면
   틀리는 경우가 있는지 확인해라 (예: 리셋이 2회 돌아 lastResetDate가 더 미래로 간 경우,
   또는 신청 전체가 기산일 이전인 경우).

5. **월차 catch-up의 상한 판정.**
   §2는 "누적 적립 < 11"인데 누적 적립 횟수를 어디서 읽나? `last_monthly_grant_date`만으로는
   횟수를 알 수 없다. §3은 base 역산을 금지했다. 이 모순을 해소할 방법을 제시해라
   (컬럼 추가가 필요하면 그렇게 말하고 이유를 대라).

## 산출물

역할 지시서의 형식 대신 이번엔 아래 형식으로 답해라.

```
## 1. catch-up × carriedUse  — 판정과 수치 검산
## 2. LeaveResetHistory 불일치 — 있음/없음 + 수치
## 3. 리셋 × 월차 간섭 — 있음/없음 + 조건
## 4. I-10 복구 경로 — 파일:줄 + 좁히는 기준의 함정
## 5. 월차 누적 횟수 — 해소 방안
## 6. 설계에서 발견한 그 밖의 결함 (없으면 "없음")
```

각 항목은 **판정 한 줄 → 근거(숫자·파일:줄)** 순으로. 확인 못 한 건 "미확인"이라고 명시해라.
