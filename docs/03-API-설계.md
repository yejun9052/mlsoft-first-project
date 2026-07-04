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
| GET | `/api/leaves/calendar?year=&month=` | 캘린더용 승인 연차 (타인 사유 마스킹) | 전체 |
| GET | `/api/leaves/pending` | 내가 승인자인 대기 목록 (취소 대기 포함, 페이징) | TL·SA |
| GET | `/api/leaves` | 전체 신청 목록 (페이징·필터) | SA |
| GET | `/api/leaves/team` | 내 팀 연차 현황 (기간 필터) | 전체 |
| POST | `/api/leaves/{id}/approval` | 승인/반려 `{approved, comment}` — 조건부 갱신 | TL·SA(승인자) |
| POST | `/api/leaves/{id}/cancel` | 취소 신청 `{reason}` — 미래=즉시, 과거 포함=CANCEL_PENDING | 본인 |
| POST | `/api/leaves/{id}/cancel-approval` | 소급 취소 승인/반려 `{approved, comment}` | TL·SA(승인자) |
| GET | `/api/leaves/{id}/histories` | 해당 건 처리 이력 | 본인·승인자·SA |

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
| GET | `/api/admin/configs` | 설정 전체 조회 | SA |
| PUT | `/api/admin/configs` | 설정 변경 `{name, value}` | SA |
| GET | `/api/admin/leave-policies` | 근속년수별 정책 목록 | SA |
| PATCH | `/api/admin/leave-policies/{id}` | 정책 일수 수정 | SA |
| GET | `/api/admin/reset-histories` | 기산일 리셋·소멸 이력 (페이징) | SA |

## 구현 순서 (백엔드)

1. ✅ 토대 (엔티티·공통 모듈) — 2026-07-02
2. **인증**: SecurityConfig + JWT + OAuth2 핸들러 + auth API ← 진행 중
3. 연차 코어: leaves API + 스케줄러
4. 부서·사용자 관리 API
5. 복리후생 API
6. 이메일 (공통 발송 인프라 → 알림 → 일괄 발송)
7. 이력·관리자 설정 API
