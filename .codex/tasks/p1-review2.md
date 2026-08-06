# 작업: 1순위 수정 최종 리뷰 (2차)

## 1차 리뷰 이후 바뀐 것

너(diff-reviewer)가 올린 🔴 "리셋 정산분이 재계산으로 부활" 지적을 받아 **재계산 예외를 제거**했다.
`resetAnnualLeave`도 이제 `syncAdvanceDays()`를 호출한다.

단, **네가 제안한 기대값(`advance=0` 유지)은 채택하지 않았다.** 다년 검산 결과 그 값이면
사원의 초과 사용분이 면제된다:

```
Y1 부여 15 · 사용 35 → advance 20
리셋  base = 15 − 20 = −5        ← base는 덮어쓰기다(가감 아님)
      advance 0 유지 시 → 다음 리셋 base = 15 − 0 = 15
      재계산 시        → advance 5 → 다음 리셋 base = 15 − 5 = 10
검산  Σ부여 45 − Σ사용 35 = 10   ← 재계산 쪽이 정답
```

같은 판정을 leave-invariant-auditor 역할로도 독립 검증했고 결론이 일치했다
(A~D 시나리오 표, E 단조감소 종료, F 초과분 이월).

docs/09 §5의 "재귀적으로 이어진다" 서술은 틀린 것으로 판정해 문서를 정정했다.
`carriedUse` 도입 시의 이중 반영 위험은 별도 항목(리뷰 I-11)으로 분리 기록했다.

## 이번 리뷰 범위

프로덕션:
- `backend/.../domain/user/entity/User.java`
- `backend/.../domain/leave/service/LeaveService.java`
- `backend/.../domain/leave/entity/LeaveRequest.java`

테스트:
- `backend/.../domain/user/entity/UserTest.java` (신규)
- `backend/.../domain/leave/service/LeaveServiceTest.java`
- `backend/.../domain/user/service/UserServiceTest.java`
- `backend/.../domain/welfare/service/WelfareServiceTest.java`

문서(코드와 어긋나는지만 확인):
- `AGENTS.md`, `CLAUDE.md` 연차 잔액 모델 절, `docs/09` §4·§5, `docs/10` "1순위 수정 결과", `docs/11` §3-2

`frontend/` 변경분은 범위 밖이다(다른 작업).

## 확인해달라는 것

1. **주석·문서가 코드와 일치하는가.** 특히 "예외 없다"고 쓴 곳과 실제 호출부 6곳이 맞는지.
   1차 리뷰에서 너는 주석 모순(User.java:161 vs :210)을 지적했다 — 지금은 해소됐는지
2. `resetAnnualLeave`의 대입 **순서**가 안전한가. `base = newBase − advance`가 `advance = 0`보다
   먼저 실행되는지, 그리고 `syncAdvanceDays()`가 맨 마지막인지
3. `AuthService.completeOnboarding`의 `resetAnnualLeave` 호출 2곳이 이 변경으로 달라지는가.
   신규 가입자는 `advance=0`이니 무영향이라고 판단했는데 맞는지
4. 남은 정확성 문제 — `BigDecimal` scale, `bonusDays` null, `deductLeave` 반환값 경계
5. 테스트 기대값이 검산과 일치하는가. **틀린 기대값을 통과시키고 있지 않은가**
   (특히 `UserTest.resetAnnualLeave_twiceWithoutActivity_settlesDebtExactlyOnce`,
   `resetAnnualLeave_debtLargerThanSeveralYears_terminates`)

이미 확인된 것 — 전체 테스트 **103개 그린**. 다시 보고하지 말 것.
