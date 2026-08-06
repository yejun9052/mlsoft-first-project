# 작업: 관리자 설정 카탈로그 + 당겨쓰기 상한(I-3) 변경분 리뷰

## 무엇을 만들었나

네가 앞선 조사에서 지적한 세 가지를 한 번에 처리했다:
① `advance_max_days` 미구현 ② 저장 시점 검증 부재(`"ture"`가 조용히 false가 되는 문제)
③ 프론트 `CONFIG_META` 하드코딩으로 새 설정이 화면에서 사라지는 문제

**설정 카탈로그를 서버 단일 정의로 올렸다** — `PolicyConfigKey` enum이 키·타입·기본값·허용 범위·
라벨·설명·동작여부(ACTIVE/PENDING_FEATURE)를 갖는다. 시딩·검증·화면 렌더가 전부 여기서 파생된다.

- 신규: `PolicyConfigKey`, `ConfigValueType`, `PolicyConfigStatus`, `PolicyConfigReader`
- 변경: `LeavePolicyConfigService`(목록을 카탈로그 기준으로, 저장 전 검증),
  `LeavePolicyConfigResponse`(메타데이터 동봉), `DataInitializer`(카탈로그 순회 시딩),
  `User.deductLeave`(3번째 인자 `advanceMaxDays` + `ADVANCE_LIMIT_EXCEEDED`),
  `LeaveService`(설정 리더 주입, 날짜 개수 상한), `LeaveCreateRequest`(`@Size(max=366)` 절대 가드),
  `ErrorCode`(4개 추가)
- 프론트: `AdminPolicyPage`(CONFIG_META 삭제, 서버 메타로 렌더, 변경분만 PUT, 클라 범위 검증),
  `api/policies.js` 주석, `mocks/data.js`의 죽은 `adminConfigs` 제거

## 신규 설정 7개

| 키 | 타입 | 기본 | 범위 | 상태 |
|---|---|---|---|---|
| advance_leave_enabled | BOOLEAN | false | — | ACTIVE |
| advance_max_days | DECIMAL | 5.0 | 0 ~ 25.0 | ACTIVE |
| leave_max_dates_per_request | INTEGER | 30 | 1 ~ 366 | ACTIVE |
| bonus_carry_over_enabled | BOOLEAN | false | — | PENDING_FEATURE |
| monthly_leave_max_days | INTEGER | 11 | 0 ~ 12 | PENDING_FEATURE |
| reminder_list_days | INTEGER | 30 | 0 ~ 365 | PENDING_FEATURE |
| reminder_auto_cycle | ENUM | NONE | NONE/D30/D60/D90/QUARTER | PENDING_FEATURE |

## 이미 확인된 것 (다시 보고하지 말 것)

- 백엔드 127개 그린 (직전 103 + 신규 24), 프론트 30개 그린, `npm run build` 성공,
  lint는 기존 StatusBadge 경고 1건만
- `restoreLeave`·`syncAdvanceDays` 불변식 작업은 이전 커밋에서 끝났다 — 그 부분은 리뷰 범위 밖

## 특별히 봐야 할 것

1. **상한 판정이 정확한가.** `User.deductLeave`의
   `advanceAfter = getRemainingDays().subtract(days).negate().max(ZERO)`가
   차감 후의 `advance_days`(= `syncAdvanceDays` 결과)와 **모든 입력에서** 같은 값인가.
   `bonusDays`가 null인 경우, `days`가 0인 경우, 이미 잔여가 음수인 경우를 각각 확인해라.
2. **예외 전에 상태가 바뀌지 않는가.** 상한 초과로 던질 때 `useDays`가 이미 증가했으면
   호출부가 트랜잭션 롤백에 의존하게 된다.
3. **상한을 낮췄을 때** 이미 상한을 넘은 사원이 어떻게 되는가. 신규 신청이 전부 막히는 게 의도인데,
   그 사원이 **연차를 쓸 수 없는 상태에서 벗어날 방법**이 있는가 (관리자 base-days 조정?).
   막다른 길이면 지적해라 — I-3의 원래 결함이 "자력 복구 불가"였다.
4. `PolicyConfigReader`의 fallback이 위험한 경우가 있는가. 예: 관리자가 상한을 0으로 두려 했는데
   값이 깨져 기본값 5로 읽히면 의도와 반대가 된다. 이런 "조용한 완화"가 발생할 수 있는 키가 있는가.
5. `PolicyConfigKey.validate`의 경계 — `min`/`max`가 null인 조합, `stripTrailingZeros().scale()`
   판정이 `"1e3"`·`"0.50"`·음수 부호·공백에 대해 의도대로 동작하는가.
6. `LeavePolicyConfigService.update`가 행이 없을 때 `save` 후 `updateValue`를 부르는데,
   같은 트랜잭션에서 문제 없는가. 그리고 `@Transactional` 없는 `PolicyConfigReader` 호출이
   `LeaveService.apply`의 트랜잭션에 어떻게 참여하는가.
7. 프론트 — `configValues[c.name] ?? c.value` fallback이 새 설정 추가 직후(로컬 상태에 키가 없을 때)
   올바르게 동작하는가. 저장 후 재조회로 로컬 상태가 갱신되는가.
