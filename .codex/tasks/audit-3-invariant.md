# 1차 테스트 — 연차 잔액 불변식 재감사

**이전 감사 이후 코드가 바뀌었다.** 그때 통과했다고 지금도 통과하는 게 아니다.

## 바뀐 것 (이 감사의 이유)

- 2026-08-08: 스케줄러 3잡 도입 (`AnnualLeaveResetService`·`MonthlyLeaveGrantService`·
  `BirthdayLeaveGrantService`) — `base_days`·`bonus_days`를 갈아 끼우는 새 경로가 생겼다
- 2026-08-10: 복리후생 승인이 `bonus_days`를 더한다 (`WelfareService`)
- **2026-08-12 (오늘): 이메일 발행 호출이 6개 서비스 메서드에 삽입됐다.**
  잔액 계산 자체는 안 건드렸다고 **주장**하지만, 그게 사실인지는 네가 확인할 일이다.
  특히 `BirthdayLeaveGrantService.grant()`는 `user.grantBirthdayLeave()` **직후에**
  이벤트 발행이 들어갔다 — 트랜잭션 경계와 지급 순서에 영향이 없는지 봐라.

## 감사할 것

역할 지시서의 불변식 (A)(B)를 **모든 쓰기 경로**에서 확인한다. 이번엔 특히:

1. **`advance_days`에 단독 대입하는 코드가 새로 생겼는가.**
   규칙: `User.syncAdvanceDays()` 하나만 이 필드에 쓰고, 잔액 3필드를 바꾸는 도메인 메서드가
   마지막에 이걸 호출한다 — **`resetAnnualLeave`까지 예외 없이**.
   이 규칙을 어기는 지점이 리뷰 I-1·I-2·I-8의 원인이었다.
2. **`resetAnnualLeave`가 채무를 재계산하는가.** 빼면 빚이 면제된다 (docs/09 §5 정정 블록에 검산).
3. **채무 계산이 `User.carryOverDebt` 하나인가.** `LeaveResetHistory.create`가 자기 식을 갖고 있던 것이
   실제 결함이었다(기록이 5일 어긋남). 다시 갈라진 곳이 없는지 확인하라.
4. **복리후생 `bonus_days` 가산이 (B)를 갱신하는가.** 보너스가 늘면 `advance_days`가 줄어야 한다.
5. **생일 반차 0.5일 지급이 (B)를 갱신하는가.**
6. **리셋의 `carriedUse`가 회차마다 그 회차 기산일로 다시 집계되는가.** 최종 기산일 기준으로
   한 번에 계산하면 1차 연도 귀속분이 한 회차 일찍 빠져 그 해 이력이 틀린다.
7. **선차감/복구의 대칭성** — 신청 시 빠진 값과 반려·취소 시 돌아오는 값이 같은가.
   `LeaveRequest.advanceUsedDays`는 감사 기록 전용이고 복구 계산에 쓰면 안 된다.

## 검산을 붙일 것

말로 "맞다"고 하지 마라. **구체적인 숫자로 검산**하라. 예:

```
base=15, bonus=0, use=17 → advance = max(0, 17−15−0) = 2
여기서 복리후생 승인으로 bonus +3 → advance = max(0, 17−15−3) = 0 이어야 한다.
addBonusDays()가 syncAdvanceDays()를 호출하는가? → (코드 인용)
```

**깨지는 시나리오를 찾으면 그 숫자를 끝까지 따라가라.** 어느 컬럼이 몇 일 어긋나는지까지.

## 읽어야 할 것

`domain/user/entity/User.java` 전체(잔액 필드를 쓰는 메서드 전부),
`domain/leave/service/`(특히 `*GrantService`·`*ResetService`·`LeaveService`),
`domain/welfare/service/WelfareService.java`,
`domain/policy/entity/LeaveResetHistory.java`,
`docs/09-스케줄러-설계.md` §5 정정 블록.

## 산출물

역할 지시서 형식. **깨지는 게 없으면 "없다"고 명확히 쓰고, 무엇을 확인해서 그렇게 판단했는지**
경로 목록을 남겨라 — 다음 감사가 중복 작업을 안 하도록.
