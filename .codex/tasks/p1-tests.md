# 작업: 잔액 불변식 위반을 잡는 테스트 작성 (지금은 실패해야 정상)

`docs/10-코드리뷰-리포트.md`의 I-1 / I-2 / I-8을 잡는 테스트를 쓴다.
**아직 프로덕션 코드를 고치기 전이므로, 네가 쓴 테스트는 지금 돌리면 실패해야 맞다.**
그게 이 작업의 목적이다 — 수정 전에 결함을 고정한다.

## 지켜야 할 불변식 (기대값의 근거)

```
advance_days = max(0, use_days − base_days − bonus_days)
```

## 대상 파일

- `backend/src/test/java/com/mlsoft/backend/domain/user/entity/UserTest.java`
  → **없으면 새로 만든다.** 도메인 메서드 단위 테스트(Mockito 불필요, 순수 객체)
- `backend/src/test/java/com/mlsoft/backend/domain/leave/service/LeaveServiceTest.java`
  → 서비스 경로 시나리오. 기존 헬퍼(`userWithBalance`, `pendingLeave`, `givenAdvanceEnabled` 등) 재사용

## 써야 할 시나리오 (하나당 테스트 1개)

### I-1 — 복구 순서 의존 (가장 중요)
```
base=10, bonus=0, 당겨쓰기 ON
신청 A 8일  → use=8,  advance=0
신청 B 5일  → use=13, advance=3
A를 취소    → use=5   → advance는 0이어야 한다 (현재 코드는 3으로 남는다)
```
- **역순(B 먼저 취소)도 별도 테스트로** 써서 두 순서가 같은 결과를 내는지 고정할 것
- `User` 도메인 메서드만으로 쓰는 버전(UserTest)과, `LeaveService.apply`/`cancel`을 타는 버전 둘 다

### I-2 — 보너스 가산이 advance를 정산하지 않음
```
base=10, use=13, advance=3  →  addBonusDays(5)
→ bonus=5이면 use 13 ≤ base+bonus 15 이므로 advance=0이어야 한다
```
가산량이 부족한 경우도 별도로: `addBonusDays(1)` → `advance=2`

### I-8 — 관리자 base 변경이 advance를 정산하지 않음
```
base=10, use=13, advance=3  →  updateBaseDays(20)  → advance=0
base=10, use=13, advance=3  →  updateBaseDays(11)  → advance=2
base=15, use=3,  advance=0  →  updateBaseDays(2)   → advance=1   (줄이는 방향)
```
`UserService.updateBaseDays`를 타는 버전도 `UserServiceTest`에 1개 추가 (기존 테스트 263·275줄 부근 스타일 참고)

### 월차 적립
```
base=10, use=13, advance=3  →  addMonthlyLeave()  → base=11, advance=2
```

### 멱등성
같은 상태에서 재계산이 두 번 일어나도 값이 변하지 않아야 한다
(`addBonusDays(BigDecimal.ZERO)` 두 번 호출 등으로 확인)

### 리셋은 예외 — 재계산 대상이 아님 (회귀 방지)
```
base=15, use=0, bonus=0, advance=3  →  resetAnnualLeave(15, 오늘)
→ base=12, use=0, bonus=0, advance=0
```
`newBase(15) < advance(20)`인 과다 당겨쓰기 케이스도 1개:
```
advance=20 → resetAnnualLeave(15, 오늘) → base=-5, advance=0   ← advance가 5로 부활하면 안 된다
```
docs/09 §5 "재차감 후 잔여 음수"가 근거다. 테스트 주석에 그 근거를 남길 것.

## 추가로 할 일

기존 테스트 중 **이 수정으로 깨질 것**을 찾아 목록으로 알려라 (고치지는 말고 목록만).
`LeaveServiceTest.reject_restoresBalance`(214줄 부근)가 유력하다 — 왜 깨지는지, 어떻게 바꿔야 하는지.
