# 2026-08-17 전체 감사 (3/4) — 연차 잔액 불변식

## 지금 상태

- 자동 테스트 **백엔드 333** 전부 통과.
- **2026-08-12에 같은 감사를 한 번 했다.** 그때 확인된 것을 다시 훑지 마라.
  이번엔 **그 이후 잔액에 닿게 된 새 경로**가 불변식을 깨는지만 본다.

## 불변식

```
잔여 = base_days + bonus_days − use_days
advance_days = max(0, use_days − base_days − bonus_days)   ← 파생값
```

`User.syncAdvanceDays()` 하나만 `advance_days`에 쓴다. 잔액 3필드를 바꾸는 도메인 메서드가
마지막에 이걸 부른다 — **`resetAnnualLeave`까지 예외 없이**. `@PrePersist`·`@PreUpdate`가
저장 직전에 한 번 더 재계산하지만 **그물이지 대체재가 아니다**(flush 전 메모리 상태도 맞아야 한다).

## 08-12 이후 잔액에 닿게 된 것

| 경로 | 무엇 |
|---|---|
| 온보딩 1회 수정 | `AuthService.reviseOnboarding` → 범위 안이면 그 자리에서 `grantInitialLeave` |
| 온보딩 반려 | `User.rejectOnboarding` — 입력값과 함께 잔액을 어떻게 두는가 |
| 관리자 승인 | `OnboardingApprovalService.approve` → `grantInitialLeave` (같은 경로 공유) |
| 퇴직 복구 | `UserService.restore` — 잔액을 건드리는가, 건드리지 않는가. 어느 쪽이 맞는가 |
| 역할·부서 변경 | `changeRole` / `assignDepartmentLeader` — 잔액과 무관해야 한다. 정말 무관한가 |

## 특히 볼 것

### 1. 온보딩 수정이 연차를 두 번 주는가

`reviseOnboarding`은 대기 중 입사일을 바꾼다. 바뀐 값이 자동 승인 범위 **안**이면 그 자리에서
연차가 부여된다.

- 대기 상태에서는 연차가 0이어야 한다. 수정 전 상태에 잔액이 남아 있을 수 있는 경로가 있는가
- 수정 → 확정 → (관리자가 뒤늦게) 승인, 이 순서가 가능한가. 가능하면 **두 번 부여**된다
- 반려 후 재제출 → 다시 수정 → 확정, 이 왕복에서 `base_days`가 누적되는가
- `grantInitialLeave`가 **덮어쓰기**인가 **더하기**인가. 그 선택이 위 경로에서 맞는가

### 2. 퇴직 복구와 잔액

- 퇴직 시 잔액을 어떻게 두는가. 복구하면 그게 되살아나는가
- 퇴직 기간 중 기산일이 지났다면(스케줄러가 퇴직자를 건너뛴다면) 복구 후 잔액이 낡아 있는가.
  그걸 보정하는 코드가 있는가, 없어야 맞는가

### 3. `advance_days` 단독 대입

08-12 이후 추가된 코드에서 `advance_days`에 **직접 대입**하는 곳이 새로 생겼는가.
`syncAdvanceDays()` 호출이 빠진 도메인 메서드가 있는가.

### 4. `BigDecimal` 취급

새 코드에서 `double`/`Double`이 섞였는가. 비교가 `compareTo`인가 `equals`인가(scale 함정).
`0.5` 단위가 깨지는 입력이 있는가.

### 5. 검산

발견한 것마다 **숫자로** 재현하라. 예:

```
base 15 / bonus 0 / use 0 → 온보딩 수정으로 base 25가 되면 advance는?
use 18 / base 15 / bonus 0 (advance 3) 상태에서 복리후생 5일 가산 → advance는?
```

## 하지 말 것

- 08-12에 확인된 정상 경로 재설명
- `docs/09 §5`(리셋의 채무 계산)와 `docs/10 I-1·I-2·I-8`은 **이미 해소된 항목**이다. 다시 열지 마라
- 재현 숫자 없는 지적
