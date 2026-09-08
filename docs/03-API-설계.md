# 03. API 설계

> 2026-07-02 확정판 (기존 초안의 빈 표를 확정 규칙 기반으로 채움).
> 이전 프로젝트 참고: `참고자료/MLsoft-분석보고서.md` §2.4

## 공통 규칙

### 응답 포맷 (CommonResponse)
```json
// 성공
{ "success": true, "message": "성공 메시지(ResponseMessage 상수)", "data": { } }
// 실패 (GlobalExceptionHandler + ErrorCode enum)
{ "success": false, "message": "에러 메시지(한글)", "data": null }
```

### URL 컨벤션
- **kebab-case + 복수형 리소스명만** (camelCase·동사·오타 URL 금지)
- 페이징: `?page=0&size=10` (Spring Pageable), 응답은 Page 구조 그대로 data에
- 본인 리소스는 `/me` 세그먼트 (`/api/leaves/me`) — 사용자 ID를 요청으로 받지 않음 (Authentication에서 추출)

### 인증·권한
- JWT HttpOnly Cookie (`token`, SameSite=Lax, prod는 Secure) — OAuth2 성공 핸들러가 발급
- 권한 표기: 전체(로그인) / TL(TEAM_LEADER) / SA(SYSTEM_ADMIN)
- 온보딩 미완료(`onboarding_status != COMPLETED`) 유저는 `/api/auth/*` 외 차단. `hire_date` 존재 여부만으로 판별하지 않음

### 에러 코드 (ErrorCode enum과 1:1)
| 코드 | HTTP | 메시지 |
|---|---|---|
| USER_NOT_FOUND | 404 | 사용자를 찾을 수 없습니다 |
| ACCESS_DENIED | 403 | 접근 권한이 없습니다 |
| INSUFFICIENT_LEAVE_BALANCE | 400 | 잔여 연차가 부족합니다 |
| ADVANCE_LIMIT_EXCEEDED | 400 | 당겨쓸 수 있는 연차 상한을 초과했습니다 (설정 `advance_max_days`) |
| TOO_MANY_LEAVE_DATES | 400 | 한 번에 신청할 수 있는 날짜 수를 초과했습니다 (설정 `leave_max_dates_per_request`) |
| LEAVE_DATE_TOO_FAR | 400 | 다음 기산일 이후 1회차까지만 신청할 수 있습니다 |
| NEXT_CYCLE_RESERVATION_EXCEEDED | 400 | 다음 회차에 예약할 수 있는 연차를 초과했습니다 |
| PURGE_RETENTION_NOT_MET | 400 | 퇴직 후 3년이 지나야 파기할 수 있습니다. 보존 기간이 지난 뒤 다시 시도해주세요 |
| PURGE_ON_HOLD | 400 | 파기 보류 상태입니다. 보류를 해제한 뒤 다시 시도해주세요 |
| ALREADY_PURGED | 400 | 이미 파기된 사원입니다. 원본은 복구할 수 없으므로 다시 파기하지 마세요 |
| REHIRE_DATE_BEFORE_RETIREMENT | 400 | 재입사일은 퇴직일보다 빠를 수 없습니다 |
| INVALID_CONFIG_VALUE | 400 | 설정 값 형식이 올바르지 않습니다 |
| CONFIG_VALUE_OUT_OF_RANGE | 400 | 설정 값이 허용 범위를 벗어났습니다 |
| HOLIDAY_API_KEY_NOT_CONFIGURED | 400 | 공휴일 API 키가 설정되지 않았습니다 |
| HOLIDAY_API_CALL_FAILED | 502 | 공휴일 API 호출에 실패했습니다 |
| HOLIDAY_API_BAD_RESPONSE | 502 | 공휴일 API 응답이 올바르지 않습니다 |
| OVERLAPPING_LEAVE_REQUEST | 409 | 이미 신청된 기간과 중복됩니다 |
| ALREADY_PROCESSED | 400 | 이미 처리된 신청입니다 |
| UNAUTHORIZED_DOMAIN | 401 | 허용되지 않은 도메인입니다 |
| (그 외는 backend ErrorCode.java 참조) | | |

---

## 인증 (auth)

| Method | URL | 설명 | 권한 |
|---|---|---|---|
| — | `/oauth2/authorization/google` | Google 로그인 진입 (Spring 제공) | 공개 |
| GET | `/api/auth/me` | 내 정보 조회 (id, name, email, role, department, 일수, hireDate, birthDay, 온보딩 여부) | 전체 |
| POST | `/api/auth/onboarding` | 최초 온보딩 — `{birthDay, hireDate}` → base_days 정책 자동 계산 | 전체(미온보딩) |
| PATCH | `/api/auth/onboarding` | 승인 대기 중 입사일·생일 **1회** 수정 — `{birthDay, hireDate}` | 전체(승인 대기) |
| POST | `/api/auth/logout` | 쿠키 만료 | 전체 |

**PATCH가 `/api/auth/` 아래인 것은 취향이 아니다.** 승인 대기 사원은 `OnboardingCheckInterceptor`가
`/api/auth/*` 밖을 전부 403으로 막으므로 다른 경로에 두면 본인이 호출할 수 없다. 같은 이유로
"수정해도 되는가"를 설정 API로 물어볼 수 없어서, `GET /api/auth/me`가 서버에서 판정한
`onboardingRevisable`(상태 == PENDING_APPROVAL && `onboarding_revision_enabled` && 미사용)과
`onboardingRevised`를 함께 내려준다. 프론트가 세 조건을 재조합하지 않는다.

거부 응답: 대기 상태 아님 `ONBOARDING_NOT_PENDING`(400) → 설정 꺼짐 `ONBOARDING_REVISION_DISABLED`(403)
→ 수정권 소진 `ONBOARDING_REVISION_EXHAUSTED`(400) → 미래 입사일 `FUTURE_HIRE_DATE`(400) 순으로 판정한다.
미래일 검증을 통과해야 수정권이 소진된다 — 앞에 두면 날짜를 잘못 찍은 사원이 수정권만 잃는다.
수정한 입사일이 자동 승인 범위 **안**이면 최초 제출과 같은 경로로 그 자리에서 확정된다.

OAuth 처리 규칙 (01 §2-1): 도메인·email_verified 검증 → 미가입이면 자동 가입(EMPLOYEE, 미배정) → ADMIN_EMAILS면 SYSTEM_ADMIN → is_active=false면 `/login?error=retired` → JWT 쿠키 발급 후 `/oauth-callback` 리다이렉트. 실패 시 `/login?error=...&message=...`

### 온보딩 승인 (admin)

승인 대기로 들어간 입사일을 총관리자가 확정·반려한다. 사원 본인이 쓰는 `/api/auth/onboarding`과
경로가 갈리는 이유는 위와 같다 — 대기 사원은 `/api/auth/*` 밖으로 나가지 못한다.

| Method | URL | 설명 | 권한 |
|---|---|---|---|
| GET | `/api/admin/onboardings` | 승인 대기 목록 (페이징, 오래 기다린 순) | SA |
| POST | `/api/admin/onboardings/{userId}/approval` | 승인 — 신고 입사일을 확정하고 그 시점에 연차 부여 | SA |
| POST | `/api/admin/onboardings/{userId}/rejection` | 반려 — 입력값을 지우고 다시 낼 수 있게 되돌린다 | SA |

## 사용자 (users)

| Method | URL | 설명 | 권한 |
|---|---|---|---|
| GET | `/api/users` | 전체 목록 (페이징, keyword·role 필터) | SA |
| GET | `/api/users/team-members` | 내 부서 팀원 목록 | 전체 |
| GET | `/api/users/approvers` | 서브 승인자 후보 (재직 TL+SA) | 전체 |
| GET | `/api/users/leader-candidates` | 팀장 후보 (재직 TL+SA — 승인자 후보와 달리 본인 포함) | SA |
| GET | `/api/users/retired` | 퇴직자 목록 (페이징) — 경과 기간·파기 가능 여부·보류 사유·`purgedAt` 포함. `AUTO` 모드에서는 보존 기간 경과자만 조회 | SA |
| PATCH | `/api/users/me` | 내 정보 수정 (이름·생일) | 전체 |
| PATCH | `/api/users/{id}/role` | 권한 변경 | SA |
| PATCH | `/api/users/{id}/department` | 부서 변경 | SA |
| PATCH | `/api/users/{id}/role-and-department` | 역할·부서 동시 변경 `{role, departmentId}` — 팀장 승격의 부분 성공 방지 | SA |
| PATCH | `/api/users/{id}/base-days` | 연차 직접 설정 `{baseDays}` | SA |
| POST | `/api/users/{id}/retire` | 퇴직 처리 (leader 해제·결재 이관 포함) | SA |
| POST | `/api/users/{id}/restore` | 퇴직 복구 — 재직 상태로 되돌린다 | SA |
| POST | `/api/users/{id}/rehire` | 재입사 처리 `{hireDate, departmentId?}` — 과거 근속 저장·대기 신청 취소 후 연차를 0부터 시작 | SA |
| POST | `/api/users/{id}/purge` | 수동 파기 — 사용자 식별정보와 자유 텍스트 본문을 익명화하고 감사 로그를 남긴다 | SA |
| POST | `/api/users/{id}/purge-hold` | 파기 보류 설정 `{reason}` (사유 필수) | SA |
| DELETE | `/api/users/{id}/purge-hold` | 파기 보류 해제 | SA |

**복구는 퇴직의 완전한 역연산이 아니다.** `is_active`·`retired_at` 두 플래그만 되돌리고,
퇴직이 함께 수행한 **팀장직 해제와 대기 결재 이관은 그대로 둔다** — 그사이 다른 사람이 팀장이
됐거나 이관된 결재가 이미 처리됐을 수 있어, 되살리면 그쪽을 말없이 덮어쓴다. 팀장은 부서 관리에서
다시 지정한다. 퇴직자가 아닌 대상이면 `NOT_RETIRED`(400).

**재입사 처리는 퇴직 복구와 다르다.** 퇴직자의 기존 근속을 `employment_periods`에 저장한 뒤
`hireDate`와 `lastResetDate`를 재입사일로 바꾸고, `baseDays`·`bonusDays`·`useDays`·`advanceDays`를
0으로 초기화한다. `PENDING`·`CANCEL_PENDING` 연차·복리후생 신청은 `CANCELLED`로 종결하고
`APPROVED` 기록은 보존한다. `departmentId`가 없으면 부서를 미배정으로 비우며, 파기된 사원은
`ALREADY_PURGED`로 거부한다.

## 부서 (departments)

| Method | URL | 설명 | 권한 |
|---|---|---|---|
| GET | `/api/departments` | 전체 목록 (플랫, 드롭다운용) | 전체 |
| GET | `/api/departments/tree` | 2단계 계층 트리 | 전체 |
| POST | `/api/departments` | 생성 `{name, description, leaderId?, parentId?}` | SA |
| PUT | `/api/departments/{id}` | 수정 (팀장 지정 포함) | SA |
| DELETE | `/api/departments/{id}` | 비활성화 (소프트 삭제) | SA |

## 연차 (leaves)

| Method | URL | 설명 | 권한 |
|---|---|---|---|
| POST | `/api/leaves` | 신청 `{leaveType, dates[], reason, subApproverId?}` — 선차감, 중복·잔여·휴일 검증 | 전체 |
| GET | `/api/leaves/me` | 내 신청 내역 (페이징, status 필터) | 전체 |
| GET | `/api/leaves/me/summary` | 잔여 현황 (base/bonus/use/잔여/현재 회차 대기/다음 기산일·차감 예정·다음 회차 예약/한도/허용 여부) | 전체 |
| GET | `/api/leaves/me/annual-usage?year=` | 내 연차 사용 히트맵 — 승인 완료 날짜별 사용 일수 | 전체 |
| GET | `/api/leaves/calendar?year=&month=&keyword=&departmentId=` | 캘린더용 승인 연차 (타인 사유 마스킹). `keyword`=신청자명 부분일치, `departmentId`=부서 — 둘 다 선택 | 전체 |
| GET | `/api/leaves/pending` | 내가 승인자인 대기 목록 (취소 대기 포함, 페이징) | TL·SA |
| GET | `/api/leaves` | 전체 신청 목록 (페이징·필터) | SA |
| GET | `/api/leaves/team` | 내 팀 연차 현황 (기간 필터) | 전체 |
| GET | `/api/leaves/team/annual-usage?year=` | 팀 연차 사용 히트맵 — 현재 부서의 날짜별 인원 수 | 전체 |
| POST | `/api/leaves/{id}/approval` | 승인/반려 `{approved, comment}` — 조건부 갱신 | TL·SA(승인자) |
| POST | `/api/leaves/{id}/cancel` | 취소 신청 `{reason}` — 미래=즉시, 과거 포함=CANCEL_PENDING | 본인 |
| POST | `/api/leaves/{id}/cancel-approval` | 소급 취소 승인/반려 `{approved, comment}` | TL·SA(승인자) |
| GET | `/api/leaves/{id}/histories` | 해당 건 처리 이력 | 본인·승인자·SA |

## 개인 일정 (schedules) — 2026-08-07 신규

외근·출장·재택근무·교육을 캘린더에 기록한다. **연차와 세 가지가 다르다** —
잔액을 차감하지 않고, 결재를 거치지 않고, 상태 전이가 없다. 그래서 "신청"이 아니라 "등록"이다.

| Method | URL | 설명 | 권한 |
|---|---|---|---|
| POST | `/api/schedules` | 등록 `{scheduleType, dates[], memo?}` — 승인 없이 즉시 확정 | 전체 |
| PUT | `/api/schedules/{id}` | 수정 (종류·날짜·메모) | 본인 |
| DELETE | `/api/schedules/{id}` | 삭제 | 본인 |
| GET | `/api/schedules/calendar?year=&month=&keyword=&departmentId=` | 캘린더용 전 직원 일정 (타인 메모 마스킹) | 전체 |
| GET | `/api/schedules/me` | 내 일정 목록 (페이징) | 전체 |
| GET | `/api/schedules/types` | 선택 가능한 종류 + 한글 라벨 `[{value, label}]` | 전체 |

**왜 `LeaveRequest`에 얹지 않았나** — 승인·차감·승인자가 전부 없는데 같은 엔티티에 두면
신청·결재·취소 흐름마다 "차감 안 하면 건너뛰기" 분기가 생긴다. 그 조건 분기 산재가
리뷰 I-1·I-5의 원인이었다. 별도 도메인(`domain/schedule`)으로 두면 그 분기가 아예 없다.

**역할 게이트 없음** — 본인 일정을 본인이 등록·삭제하는 것이고, 조회는 캘린더가 이미 전사 공개다.
소유권 검증은 서비스 계층에서 한다 (docs/04).

**마스킹** — 메모는 본인·SYSTEM_ADMIN에게만 채워 보낸다. 캘린더가 전 직원 일정을 보여주므로
방문처·개인 사정이 그대로 노출되면 안 된다 (연차 사유 마스킹과 같은 기준 — 검증 Y-4).
연차와 달리 승인자가 없어 열람 권한자가 둘뿐이다.

**주말·과거 허용** — 연차와 달리 막지 않는다. 주말 출장·지난주 외근을 뒤늦게 기록하는 것이
정상 사용이다. 같은 날짜에 같은 종류를 두 번 등록하는 것만 막는다 (409 `DUPLICATE_SCHEDULE`).

**종류 추가 방법** — `ScheduleType` enum에 상수 한 줄. `/types`가 라벨까지 내려주므로
프론트는 수정 없이 따라온다 (설정 카탈로그와 같은 의도 — 리뷰 I-3).

## 복리후생 (welfare-policies / welfare-requests)

| Method | URL | 설명 | 권한 |
|---|---|---|---|
| GET | `/api/welfare-policies` | 정책 목록 (페이징, keyword·category 필터) | 전체 |
| GET | `/api/welfare-policies/categories` | 카테고리 목록 | 전체 |
| GET | `/api/welfare-policies/all` | 활성 정책 전체 (신청 폼용) | 전체 |
| POST | `/api/welfare-policies` | 정책 추가 (구분·대상 중복 검증) | SA |
| PATCH | `/api/welfare-policies/{id}` | 정책 수정 | SA |
| DELETE | `/api/welfare-policies/{id}` | 비활성화 | SA |
| POST | `/api/welfare-requests` | 신청 `{policyId, reason, subApproverId?}` | 전체 |
| GET | `/api/welfare-requests/me` | 내 신청 내역 (페이징) | 전체 |
| GET | `/api/welfare-requests/pending` | 내가 승인자인 대기 목록 | TL·SA |
| GET | `/api/welfare-requests` | 전체 목록 (페이징) | SA |
| POST | `/api/welfare-requests/{id}/approval` | 승인(bonus_days 가산)/반려 | TL·SA(승인자) |
| POST | `/api/welfare-requests/{id}/cancel` | 취소 (PENDING만) | 본인 |

## 공휴일 (holidays)

| Method | URL | 설명 | 권한 |
|---|---|---|---|
| GET | `/api/holidays?year=` | 연도별 공휴일. year 생략 시 올해(KST) | 전체 |
| POST | `/api/holidays/sync?year=` | 강제 재동기화 — 대체공휴일이 뒤늦게 지정된 경우 | SA |

**연 1회 조회 + DB 캐시** (검증 Y-6). 요청마다 외부를 부르면 data.go.kr 장애가 곧 우리 장애가 되고,
공휴일은 연중 바뀌지 않는 데이터라 캐시가 자연스럽다. 캐시가 비어 있으면 조회 시 1회 적재를 시도한다.

**자동 조회 실패를 예외로 만들지 않는다** — 키가 없거나 외부 API가 죽어도 결과를 빈 목록으로
degrade한다. 공휴일을 못 받았다고 캘린더가 안 그려지거나 사원의 연차 신청이 막히면 안 된다.
대신 그 해 공휴일 검증이 느슨해지므로 WARN을 남긴다. 결과는 정상(`OK`) 0건과 키 없음·호출 실패·
이상 응답을 구분하며, 관리자 수동 동기화·키 검증에서는 후자의 실패를 원인별 오류로 표면화한다.
`HOLIDAY_API_KEY`가 비어도 기동은 된다 (보안에 직결되는 `COOKIE_SECURE`·`ALLOWED_DOMAIN`만
fail-fast로 막는다).

`isHoliday=Y`인 것만 저장한다 — 이 API는 공휴일이 아닌 기념일(식목일 등)도 함께 준다.
결과가 1건이면 배열이 아니라 객체로 오는 알려진 특성도 함께 처리한다.

**연차 신청 시 공휴일 거부** — `LeaveService.validateDates`가 주말·과거를 먼저 보고,
통과한 경우에만 DB에서 공휴일을 조회한다(어차피 거부될 요청 때문에 DB를 볼 이유가 없다).
`HOLIDAY_NOT_ALLOWED`(400).

**API 키 보관** — `HolidayApiClient`는 `holiday_api_credentials`의 활성 암호문을
`HOLIDAY_CREDENTIAL_ENCRYPTION_KEY`로 복호화해 사용한다. 암호화 설정이 아직 없는 로컬
환경에서는 `HOLIDAY_API_KEY`를 fallback으로 사용할 수 있지만, 원문 키를 소스·DB의
`holidays` 행·`admin_audit_log`에 기록하지 않는다. 저장된 자격 증명은 제공자별 한 행이며,
날짜별 캘린더 이벤트에는 `date`·`name`만 투영된다.
최초 시드·명시적 교체가 필요한 경우에만 `HOLIDAY_CREDENTIAL_SEED=true`를 사용하며,
기본값은 false다. 외부 API 응답은 `response.header.resultCode == "00"`일 때만 성공으로
처리하고, 인증·쿼터 오류는 자동 경로에서 빈 결과와 WARN으로 degrade한다.

## 처리 이력 (histories — 관리자·팀장 로그 화면)

| Method | URL | 설명 | 권한 |
|---|---|---|---|
| GET | `/api/leave-histories` | 연차 처리 로그 (페이징, action 필터) | SA |
| GET | `/api/leave-histories/my-approvals` | 내가 결재자인 건의 로그 | TL·SA |
| GET | `/api/leave-histories/my-actions` | 내가 처리한 로그 | 전체 |
| GET | `/api/welfare-histories` | 복리후생 처리 로그 | SA |
| GET | `/api/welfare-histories/my-approvals` | 내가 결재자인 건의 로그 | TL·SA |
| GET | `/api/welfare-histories/my-actions` | 내가 처리한 로그 | 전체 |

## 관리자 조작 감사 로그 (audit — 리뷰 S-3)

| Method | URL | 설명 | 권한 |
|---|---|---|---|
| GET | `/api/admin/audit-logs` | 감사 로그 (페이징 최신순, `action`·`targetUserId` 필터) | SA |
| GET | `/api/admin/audit-logs/actions` | 필터용 액션 목록 (`{name, label}`) | SA |

> **쓰기 엔드포인트가 없다.** 기록은 조작이 일어나는 서비스 안에서만 만들어지고 수정·삭제 경로가
> 존재하지 않는다(append-only). 기록 대상은 `AdminAction` enum 7종 —
> 권한·부서·연차 직접 설정 변경, 퇴직 처리, 온보딩 승인/반려, 시스템 설정 변경.
>
> 응답은 `before/after` 문자열 한 쌍을 담는다. 감사의 목적이 대조이므로 타입별 컬럼으로 쪼개지 않는다.
> `targetUserId`는 시스템 설정 변경처럼 사원이 대상이 아닌 조작에서 `null`이고, `targetLabel`에는
> **조작 시점의** 대상 표시명(사원명 또는 설정 키)이 들어간다 — 개명 후에도 그때 기록이 유지된다.

## 이메일 (emails — 관리자)

> 관리자 이메일 API는 `EmailAdminController`가 제공한다. 모든 응답은 `CommonResponse`이며
> SYSTEM_ADMIN만 접근할 수 있다. 본문·제목은 아웃박스 `PENDING`으로 먼저 저장한 뒤 커밋 후 발송한다.

| Method | URL | 설명 | 권한 | 상태 |
|---|---|---|---|---|
| GET | `/api/emails/reminder-targets` | `{userId,name,departmentName,remainingDays,nextResetDate,daysUntilReset,emailAvailable}` 대상 목록 | SA | 구현 |
| POST | `/api/emails/bulk` | `{userIds[],title,content}` 일괄 발송 → `{requested,queued,skipped}` | SA | 구현 |
| GET | `/api/emails` | 발송 이력 Page (`type`, `status` 필터, 수신자 주소 마스킹) | SA | 구현 |
| POST | `/api/emails/{id}/resend` | `FAILED` 건만 retry_count를 0으로 초기화해 재발송 | SA | 구현 |

이력 응답의 `type`은 `LEAVE`·`NOTICE`·`REMINDER`·`WELFARE`, `status`는
`PENDING`·`SENDING`·`SENT`·`FAILED`다. `GET /api/emails`의 `data`는
`{content:[{id,recipientName,recipientEmailMasked,type,status,title,retryCount,errorMessage,sentAt,createdAt}],page:{number,size,totalElements,totalPages}}`다.

> 재발송은 **`FAILED`만** 허용한다 — `SENT` 재발송은 수신자에게 중복 수신이고 자동 발송 ledger의
> "한 번만" 규칙과 충돌한다. 일괄 발송 상한은 회당 100건·일 400건이다
> (개인 Gmail 일 500건에 건별 알림 몫을 남긴다). 결정 근거는
> [`설계-초안/연차-소진-안내-메일-설계-2026-08-28.md`](설계-초안/연차-소진-안내-메일-설계-2026-08-28.md) §9.

### 메일 양식 (email-templates — 관리자)

| Method | URL | 설명 | 권한 | 상태 |
|---|---|---|---|---|
| GET | `/api/admin/email-templates` | 양식 목록·본문·version·수정자·placeholder 조회 | SA | 구현 |
| PUT | `/api/admin/email-templates/{templateKey}` | `{subjectTemplate,bodyTemplate}` 수정 — 평문 저장 후 서버가 escaping | SA | 구현 |
| POST | `/api/admin/email-templates/{templateKey}/preview` | 저장하지 않고 `{subject,html}` 미리보기 | SA | 구현 |

### 외부 연동 설정 (integrations — 관리자)

공휴일 API 키와 메일 발신 계정을 한 화면(`/admin/integrations`)에서 관리한다.
**비밀번호·키는 응답에 싣지 않는다** — 조회는 마스킹만 내려주고 저장은 쓰기 전용 필드다.

| Method | URL | 설명 | 권한 | 상태 |
|---|---|---|---|---|
| GET | `/api/admin/integrations` | SMTP·공휴일 키 마스킹 조회 + `encryptionConfigured` | SA | 구현 |
| PUT | `/api/admin/integrations/mail` | 메일 발신 계정 저장·회전 (쓰기 전용) | SA | 구현 |
| PUT | `/api/admin/integrations/holiday` | 공휴일 API 키 저장·회전 (쓰기 전용) | SA | 구현 |
| PUT | `/api/admin/integrations/{provider}` | 위 둘 외의 이름은 `EMAIL_PROVIDER_NOT_FOUND`(404) | SA | 구현 |
| POST | `/api/admin/integrations/mail/test` | 테스트 발송 — 인증 주체 본인에게만 큐 등록 | SA | 구현 |
| POST | `/api/admin/integrations/holiday/verify` | 공휴일 키 검증 — 저장하지 않고 올해 조회 | SA | 구현 |

연동 조회 응답은 `{mail:{provider,username,maskedSecret,active}|null,
holiday:{provider,maskedKey,active}|null,encryptionConfigured}`이며, 테스트 메일의 성공
메시지는 `테스트 메일을 큐에 넣었습니다`다.

키 검증 응답은 `{count}`뿐이다 — **성공 응답에만 도달하고, 실패는 `HOLIDAY_API_*` 오류로 나간다.**
"유효 여부" 불리언을 두면 항상 참인 값이 화면에 분기를 만든다.

## 시스템 설정 (admin)

| Method | URL | 설명 | 권한 |
|---|---|---|---|
> **처리 이력 — 스코프 3종** (2026-08-06 신설 · **2026-08-10 중간 스코프 확정**)
>
> | URL | 스코프 | 권한 |
> |---|---|---|
> | `GET /api/leave-histories` · `/api/welfare-histories` | 전사 | SA |
> | `.../my-approvals` | **내가 primary·sub 승인자로 지정된 신청** — 남이 처리한 건도 포함 | TL·SA |
> | `.../my-actions` | **actor가 본인인 이력만** | 로그인 전체 |
>
> **`my-team` → `my-approvals` 개명 (리뷰 S-6 확정, 2026-08-10).** 예전 `my-team`은 *신청자 소속 부서*
> 기준이었고 그게 실제 결재 권한과 어긋났다 — 부서를 옮긴 사원의 과거 이력이 새 팀장에게 보이고,
> 반대로 퇴직 이관으로 결재를 넘겨받은 건은 내 부서가 아니라서 안 보였다. 승인자 지정 기준으로
> 바꾸면 둘 다 맞는다. 이름도 함께 바꾼 이유는 `my-team`이 더 이상 "내 팀"이 아니기 때문이다.
>
> 부서를 보지 않으므로 **부서 미배정 분기가 사라졌다** — 예전에는 부서가 없으면 빈 페이지였다.
>
> `my-approvals`와 `my-actions`의 차이: 전자는 내가 승인자인 신청의 **모든** 이력이라 같은 건을
> 서브 승인자가 처리한 기록도 포함되고, 후자는 **내가 직접 누른 것**만이다. 결재 화면의
> "승인·반려 완료" 탭이 필요한 것은 후자라 역할 게이트가 없다 — 본인이 한 일만 보인다.
>
> 연차 로그 응답에는 `dates`(신청 날짜 목록)가 포함된다 — 결재 화면이 기간을 표시한다.

| GET | `/api/admin/configs` | 설정 전체 조회 (값 + 메타데이터) | SA |
| PUT | `/api/admin/configs` | 설정 변경 `{name, value}` | SA |
| GET | `/api/admin/leave-policies` | 근속년수별 정책 목록 | SA |
| PATCH | `/api/admin/leave-policies/{id}` | 정책 일수 수정 | SA |
| GET | `/api/admin/reset-histories` | 기산일 리셋·소멸 이력 (페이징) | SA |

### GET /api/admin/configs — 값과 메타데이터를 함께 준다 (2026-08-06)

응답 항목마다 렌더에 필요한 정보가 모두 들어 있어, **프론트가 설정 키 이름을 알 필요가 없다.**
목록·순서는 서버 카탈로그(`PolicyConfigKey`)가 정한다. 아직 시딩되지 않은 키도 `id: null` +
기본값으로 함께 내려온다.

```json
{
  "id": 2,
  "name": "advance_max_days",
  "value": "5.0",
  "type": "DECIMAL",
  "defaultValue": "5.0",
  "min": 0,
  "max": 25.0,
  "unit": "일",
  "options": [],
  "label": "당겨쓰기 상한",
  "description": "한 사원이 당겨쓸 수 있는 최대 일수. 초과하는 신청은 거부된다. ...",
  "status": "ACTIVE"
}
```

| 필드 | 의미 |
|---|---|
| `type` | `BOOLEAN` \| `INTEGER` \| `DECIMAL` \| `ENUM` — 화면 컨트롤을 이걸로 고른다 |
| `min` / `max` | 숫자 타입의 허용 범위(포함). 그 외 타입은 `null` |
| `unit` | 값 뒤에 붙일 단위 (없으면 `null`) |
| `options` | `ENUM` 선택지. 그 외 타입은 빈 배열 |
| `status` | `ACTIVE`면 지금 동작하는 설정, `PENDING_FEATURE`면 값만 저장되고 읽는 기능이 아직 없다 |

### PUT /api/admin/configs — 저장 시점에 검증한다

| 상황 | 응답 |
|---|---|
| 카탈로그에 없는 키 | 404 `LEAVE_POLICY_CONFIG_NOT_FOUND` |
| 타입 불일치 (BOOLEAN에 `"ture"`, 숫자에 `"abc"`, INTEGER에 `"10.5"`, ENUM 선택지 외) | 400 `INVALID_CONFIG_VALUE` |
| 범위 초과 | 400 `CONFIG_VALUE_OUT_OF_RANGE` |

값은 정규화해 저장한다 — 공백 제거, BOOLEAN은 소문자(`"TRUE"` → `"true"`).
정수 설정의 `"30.0"`은 허용한다 (숫자 입력이 소수점을 붙여 보낼 수 있다).

**왜 저장 시점인가** — 읽는 시점에 검사하면 잘못 넣은 관리자가 아니라 **사원이 피해를 본다.**
`advance_leave_enabled`에 오타가 들어가면 당겨쓰기가 조용히 꺼지고, 사원은 몇 시간 뒤
이유 모를 잔여 부족 거부를 받는다. 읽기 쪽은 2차 방어선으로 기본값 fallback + WARN 로그만 남긴다.

## 구현 순서 (백엔드)

1. ✅ 토대 (엔티티·공통 모듈) — 2026-07-02
2. **인증**: SecurityConfig + JWT + OAuth2 핸들러 + auth API ← 진행 중
3. 연차 코어: leaves API + 스케줄러
4. 부서·사용자 관리 API
5. 복리후생 API
6. 이메일 (공통 발송 인프라 → 알림 → 일괄 발송)
7. 이력·관리자 설정 API
