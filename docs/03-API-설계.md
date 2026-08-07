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
- 온보딩 미완료(hire_date null) 유저는 `/api/auth/*` 외 차단

### 에러 코드 (ErrorCode enum과 1:1)
| 코드 | HTTP | 메시지 |
|---|---|---|
| USER_NOT_FOUND | 404 | 사용자를 찾을 수 없습니다 |
| ACCESS_DENIED | 403 | 접근 권한이 없습니다 |
| INSUFFICIENT_LEAVE_BALANCE | 400 | 잔여 연차가 부족합니다 |
| ADVANCE_LIMIT_EXCEEDED | 400 | 당겨쓸 수 있는 연차 상한을 초과했습니다 (설정 `advance_max_days`) |
| TOO_MANY_LEAVE_DATES | 400 | 한 번에 신청할 수 있는 날짜 수를 초과했습니다 (설정 `leave_max_dates_per_request`) |
| INVALID_CONFIG_VALUE | 400 | 설정 값 형식이 올바르지 않습니다 |
| CONFIG_VALUE_OUT_OF_RANGE | 400 | 설정 값이 허용 범위를 벗어났습니다 |
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
| POST | `/api/auth/logout` | 쿠키 만료 | 전체 |

OAuth 처리 규칙 (01 §2-1): 도메인·email_verified 검증 → 미가입이면 자동 가입(EMPLOYEE, 미배정) → ADMIN_EMAILS면 SYSTEM_ADMIN → is_active=false면 `/login?error=retired` → JWT 쿠키 발급 후 `/oauth-callback` 리다이렉트. 실패 시 `/login?error=...&message=...`

## 사용자 (users)

| Method | URL | 설명 | 권한 |
|---|---|---|---|
| GET | `/api/users` | 전체 목록 (페이징, keyword·role 필터) | SA |
| GET | `/api/users/team-members` | 내 부서 팀원 목록 | 전체 |
| GET | `/api/users/approvers` | 서브 승인자 후보 (재직 TL+SA) | 전체 |
| GET | `/api/users/retired` | 퇴직자 목록 (페이징) | SA |
| PATCH | `/api/users/me` | 내 정보 수정 (이름·생일) | 전체 |
| PATCH | `/api/users/{id}/role` | 권한 변경 | SA |
| PATCH | `/api/users/{id}/department` | 부서 변경 | SA |
| PATCH | `/api/users/{id}/base-days` | 연차 직접 설정 `{baseDays}` | SA |
| POST | `/api/users/{id}/retire` | 퇴직 처리 (leader 해제·결재 이관 포함) | SA |

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
| GET | `/api/leaves/me/summary` | 잔여 현황 (base/bonus/use/잔여/대기/다음 기산일·차감 예정) | 전체 |
| GET | `/api/leaves/calendar?year=&month=&keyword=&departmentId=` | 캘린더용 승인 연차 (타인 사유 마스킹). `keyword`=신청자명 부분일치, `departmentId`=부서 — 둘 다 선택 | 전체 |
| GET | `/api/leaves/pending` | 내가 승인자인 대기 목록 (취소 대기 포함, 페이징) | TL·SA |
| GET | `/api/leaves` | 전체 신청 목록 (페이징·필터) | SA |
| GET | `/api/leaves/team` | 내 팀 연차 현황 (기간 필터) | 전체 |
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
| GET | `/api/holidays?year=` | 연도별 공휴일 (data.go.kr 연동 캐시) | 전체 |

## 처리 이력 (histories — 관리자·팀장 로그 화면)

| Method | URL | 설명 | 권한 |
|---|---|---|---|
| GET | `/api/leave-histories` | 연차 처리 로그 (페이징, action 필터) | SA |
| GET | `/api/leave-histories/my-team` | 팀 처리 로그 | TL |
| GET | `/api/welfare-histories` | 복리후생 처리 로그 | SA |
| GET | `/api/welfare-histories/my-team` | 팀 처리 로그 | TL |

## 이메일 (emails — 관리자)

| Method | URL | 설명 | 권한 |
|---|---|---|---|
| GET | `/api/emails/reminder-targets` | 기산일 임박 + 연차 잔여 대상자 리스트 | SA |
| POST | `/api/emails/bulk` | 일괄 발송 `{userIds[], title, content}` (비동기) | SA |
| GET | `/api/emails` | 발송 이력 (페이징, type·status 필터) | SA |
| POST | `/api/emails/{id}/resend` | FAILED 건 재발송 | SA |

## 시스템 설정 (admin)

| Method | URL | 설명 | 권한 |
|---|---|---|---|
> **처리 이력 — 스코프 3종 (2026-08-06)**
>
> | URL | 스코프 | 권한 |
> |---|---|---|
> | `GET /api/leave-histories` · `/api/welfare-histories` | 전사 | SA |
> | `.../my-team` | **신청자 소속 부서** — 남이 처리한 건도 포함 | TL·SA |
> | `.../my-actions` | **actor가 본인인 이력만** | 로그인 전체 |
>
> `my-actions`는 결재 화면의 "승인·반려 완료" 탭용이다. `my-team`은 부서 기준이라 "내가 처리한 것"과
> 다르고, 전사는 너무 넓다. actor를 토큰에서 가져오므로 부서 스코프 논쟁(리뷰 S-6)과 무관하고
> 역할 게이트가 없다 — 본인이 한 일만 보인다.
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
