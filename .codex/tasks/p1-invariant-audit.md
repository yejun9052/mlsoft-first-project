# 작업: 잔액 불변식 감사 — 1순위 수정 착수 전 전수 확인

`docs/10-코드리뷰-리포트.md`의 **I-1 / I-2 / I-8**은 별개 버그가 아니라 하나의 원인이라는 가설을 세웠다:

> `advance_days`는 파생값인데, 여러 도메인 메서드가 각자 독립적으로 이 필드를 더하고 뺀다.

이 가설을 **코드로 검증**하고, 아래 수정안이 안전한지 판정해라.

## 검증할 수정안

`User`에 파생값 재계산 메서드를 하나 두고, 잔액 3필드(`base`/`bonus`/`use`)를 바꾸는 모든
도메인 메서드가 마지막에 이걸 호출하게 바꾼다:

```java
/** advance_days 재계산 — 파생값이므로 이 메서드만 이 필드를 쓴다 */
private void syncAdvanceDays() {
    this.advanceDays = getRemainingDays().negate().max(BigDecimal.ZERO);
}
```

`getRemainingDays()` = `base + bonus − use` 이므로 `negate()` = `use − base − bonus`다.

호출을 붙일 대상으로 다음 5개를 후보로 본다:
`deductLeave`, `restoreLeave`, `addBonusDays`, `updateBaseDays`, `addMonthlyLeave`

**`resetAnnualLeave`는 제외** 후보다 (docs/09 §5 근거).

## 반드시 답할 것

1. 위 5개가 **전부**인가. 빠진 대입 지점이 있나 (벌크 `@Query` update, 빌더 직접 호출, 스케줄러 포함)
2. `deductLeave`의 반환값(= 그 신청이 당겨쓴 일수)을 재계산 방식에서 어떻게 구해야 같은 값이 되는가.
   아래가 기존과 모든 입력에서 동일한지 숫자로 검증해라 — 특히 **`remaining`이 이미 음수인 상태에서 추가 신청**하는 경우:
   ```java
   BigDecimal before = this.advanceDays;
   this.useDays = this.useDays.add(days);
   syncAdvanceDays();
   return this.advanceDays.subtract(before).max(BigDecimal.ZERO);
   ```
3. `restoreLeave(days, advanceUsedDays)`의 2번째 인자가 **불필요해지는가**.
   재계산이 있으면 `LeaveRequest.advanceUsedDays` 스냅샷에 의존할 필요가 없어져 시그니처를
   `restoreLeave(days)`로 줄일 수 있다고 본다. 이게 맞으면:
   - `docs/09 §4`의 4단계("잔존 신청 스냅샷 정리")와 **리뷰 I-4가 소멸하는가**? 근거를 들어 판정
   - `advanceUsedDays` 필드는 감사 기록으로 남겨야 하나, 지워야 하나
4. `resetAnnualLeave`를 제외하는 판단이 맞는가. `base_days`가 음수가 되는 경우로 숫자 검증해라.
   (`newBase=15, advance=20` → `base=-5`. 여기서 재계산하면 어떻게 되는가)
5. 이 수정으로 **깨지는 기존 테스트**가 무엇인가. 파일:줄 + 왜 깨지는지.
   특히 `LeaveServiceTest`의 `reject_restoresBalance`(214줄 부근)를 확인해라.
