# 03. API 설계

> 노션 초안 (작성 중) + 공통 규칙. 빈 표는 설계를 채워야 할 부분.
> 참고: 이전 프로젝트 전체 API 목록은 `참고자료/MLsoft-분석보고서.md` §2.4,
> 엔터프라이즈 API 규칙 예시는 `참고자료/MLsoft/전달받은참고자료/API_SPECIFICATION.md`

## 공통 규칙

### 응답 포맷 (CommonResponse — 이전 프로젝트 패턴 유지)
```json
// 성공
{ "success": true, "message": "성공 메시지(한글)", "data": { } }
// 실패 (GlobalExceptionHandler에서 일괄 처리)
{ "success": false, "message": "에러 메시지(한글)", "data": null }
```

### URL 컨벤션 (이번엔 엄격히)
- **kebab-case만 사용** — 이전 프로젝트의 `getPaddingLeave`, `add-brith-day-leave` 같은 camelCase/오타 URL 재발 금지
- 리소스 중심: `/api/leaves`, `/api/welfare-requests` 형태 권장, 동사 최소화
- 페이징: `?page=0&size=10&sort=createdAt,desc` (Spring Pageable 표준)

### 인증
- JWT HttpOnly Cookie (OAuth2 로그인 성공 핸들러에서 발급)
- 권한: `@PreAuthorize("hasRole('TEAM_LEADER') or hasRole('SYSTEM_ADMIN')")` 메서드 레벨

### 에러 코드 (전달받은참고자료 방식 도입 검토)
| 코드 | HTTP | 메시지 |
|---|---|---|
| USER_NOT_FOUND | 404 | 사용자를 찾을 수 없습니다 |
| ACCESS_DENIED | 403 | 접근 권한이 없습니다 |
| INSUFFICIENT_LEAVE_BALANCE | 400 | 잔여 연차가 부족합니다 |
| OVERLAPPING_LEAVE_REQUEST | 409 | 이미 신청된 기간과 중복됩니다 |
| ALREADY_PROCESSED | 400 | 이미 처리된 신청입니다 |
| UNAUTHORIZED_DOMAIN | 401 | 허용되지 않은 도메인입니다 |

## users (노션 초안)

| Method | URL | 설명 | 권한 | 비고 |
|---|---|---|---|---|
| POST | /api/user/baseInfo | 기본 유저 정보를 불러온다 | 전체 | auth로 변경 예정 |
| POST | /api/user/signup | 유저 정보 저장 (최초 온보딩 한정) | 전체 | |
| GET | /api/user/me | 유저 정보 조회 | 로그인 유저 | |
| GET | /api/user/teamMembers | 팀원 조회 | 로그인 유저 | 특정 정보만 |
| GET | /api/user/… | (작성 중) | | |

> 컨벤션 적용 제안: `/api/users/me`, `/api/users/team-members`, `POST /api/users/onboarding`

## department (노션 초안)

| Method | URL | 설명 | 권한 |
|---|---|---|---|
| GET | /api/department/departments | 부서 전체 조회 | |
| POST | /api/department/createDept | 부서 생성 | |

> 컨벤션 적용 제안: `GET /api/departments`, `POST /api/departments`, `GET /api/departments/tree`

## 작성해야 할 영역 (노션에서 빈 표)

- **leave_requests**: 신청 / 내 내역(페이징) / 잔여 현황 / 승인·반려 / 취소 / 취소 승인 / 대기 목록(승인자 기준) / 캘린더용 조회
- **welfare**: 정책 목록(페이징·카테고리 필터) / 정책 CRUD(관리자) / 신청 / 내 신청 / 승인·반려 / 취소
- **holidays**: 연도별 조회
- **leave_action_history / welfare_action_history**: 전체 로그(관리자, 필터) / 팀 로그(팀장) / 건별 로그
- **email**: 발송 대상 리스트(잔여 연차 기준) / 일괄 발송 / 발송 이력 / 자동 발송 설정
- **retired**: 퇴직 처리(관리자) / 퇴직자 목록

> 이전 프로젝트의 `/api/leave/*` 18개 엔드포인트(분석보고서 §2.4)를 베이스로 삼되,
> primary/sub 승인자 구조와 선차감 정책에 맞게 요청/응답 DTO를 다시 정의할 것.
