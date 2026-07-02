# MLsoft 휴가관리 시스템 — 코드 분석 보고서

> 분석일: 2026-07-02 | 대상: `참고자료/MLsoft` (커밋 d017e3d "프로젝트 미완(1차 프로젝트 보류)")
> 목적: 새 프로젝트(mlsoft-leave-system) 재구축 시 참고 자료

---

## 1. 전체 개요

- **스택**: React 19 (Vite) + Spring Boot 4.0.6 (Java 21) + MySQL(Docker)
- **인증**: JWT (HttpOnly Cookie) + Google OAuth2 (테스트 단계)
- **역할**: `EMPLOYEE` / `MANAGER` / `SYSTEM_ADMIN`
- **상태**: 연차 핵심 기능은 3차 완성본까지 도달, 이후 복리후생·Google 로그인 작업 중 **1차 보류**로 중단

---

## 2. 백엔드 구조

### 2.1 엔티티 (핵심 필드)

| 엔티티 | 핵심 필드 / 비고 |
|---|---|
| **User** | employeeId(로그인ID), email(OAuth용), totalDays/usedDays/advance_days(당겨쓰기 누적), isLocked(연차 최초설정 잠금), birthDay, hireDate, lastResetDate, role |
| **LeaveRequest** | leaveType(ANNUAL 1.0 / HALF_AM·HALF_PM 0.5), dates(ElementCollection→leave_dates), days, reason, status, isAdvance, approverId(지정 승인자, NULL=부서 매니저) |
| **WelfareLeaveRequest** | policyId(신청 시점 스냅샷), category, target(enum), addDays, evidenceGuide, processorId/Comment, status(PENDING/APPROVED/REJECTED/CANCELLED) |
| **WelfarePolicies** | category(결혼/출산/조의/회갑·칠순/졸업), target(SELF/PARENT/SPOUSE/CHILD/SIBLING/GRANDPARENT/SPOUSE_PARENT/SPOUSE_GRANDPARENT/OTHER), defaultDays, defaultEvidence, active(소프트삭제) |
| **LeavePolicy** | yearsOfService(unique), annualLeaveDays — 근로기준법 공식 `MIN(15 + (년수-1)/2, 25)` |
| **LeavePolicyConfig** | key-value: `reset_type`(true=입사일 기준/false=회계연도), `advance_leave_enabled` |
| **ApprovalHistory** | targetType(LEAVE/WELFARE), targetId, approverId, action(7종: PENDING~CANCEL_REJECTED), comment |
| **Department** | parentId 자기참조, **최대 2단계 계층**, active 소프트삭제 |
| **Holiday** | data.go.kr API 연동으로 저장 (date unique, name, year, month) |

### 2.2 연차 상태 흐름

```
PENDING ─→ APPROVED ─→ CANCELLED          (미래 날짜만: 즉시 취소)
   │           └────→ CANCEL_PENDING ─→ CANCELLED (소급 취소 승인)
   │                        └────────→ APPROVED  (소급 취소 반려)
   └─→ REJECTED
PENDING 상태 취소는 날짜 무관 즉시 CANCELLED
```

### 2.3 핵심 비즈니스 규칙

- **신청 검증**: 같은 날짜 중복 불가, 잔여 부족 시 `advance_leave_enabled=true`면 당겨쓰기(advance_days 누적), false면 거부
- **승인 권한**: SYSTEM_ADMIN 전체 / MANAGER는 approverId 일치 건만
- **기산일 초기화** (`resetAndApplyPolicy`): `newTotal = 정책일수 - advance_days`, 새 기산기간 내 미래 승인 연차 재차감
- **스케줄러** (매일 자정 cron):
  - 입사일 기준 초기화 (입사 N주년 도래 시, reset_type=true)
  - 매년 1/1 회계연도 초기화 (reset_type=false, 1년 미만 스킵)
  - 생일자 반차 0.5일 자동 지급
- **reason 마스킹**: EMPLOYEE가 타인 신청 조회 시 사유 null 처리 (EntityManager.detach 사용)
- **기본 승인자**: 부서 첫 MANAGER → 없으면 SYSTEM_ADMIN fallback

### 2.4 API 표면 (컨트롤러 10개)

- `/api/auth` — me, logout, check-id, department-list (**signup/login 미구현** — OAuth 전환 중이었음)
- `/api/leave` — 신청/조회/승인/취소/취소승인/페이징/팀현황/사이드바용(대기일수·차감예정) 등 18개
- `/api/user` — set-info, list, search, role 변경, approvers, default-approver, my-team-users
- `/api/welfare-leave` — 신청/my/pending/approval/all/cancel (**팀장용 /pending/my-team은 권한 미정으로 주석 처리**)
- `/api/welfare-policy` — CRUD + categories + all
- `/api/departments` — CRUD + tree + options
- `/api/leave-policy` — 정책 목록/수정 + config 조회/변경
- `/api/approval-history` — 전체/my-team/건별 (LEAVE·WELFARE 통합, targetType 필터)
- `/api/holiday/all`, `/api/test/*` (스케줄러 강제실행·생일반차·강제리셋)

### 2.5 보안

- SecurityConfig: CSRF off, STATELESS, JWT 쿠키 필터, CORS는 localhost:5173만
- JWT: subject=employeeId, claim=role, 24시간, HMAC-SHA256
- OAuth2: CustomOAuth2UserService가 미가입 이메일 자동 가입(부서 "미배정", EMPXXX 자동 채번) → SuccessHandler가 JWT 쿠키 발급 후 `/oauth-callback` 리다이렉트

---

## 3. 프론트엔드 구조

### 3.1 라우트

| 경로 | 페이지 | 비고 |
|---|---|---|
| /login, /oauth-callback | LoginPage, OAuthCallback | Google OAuth 단일 로그인, me() 후 localStorage에 userInfo 저장 |
| /dashboard | DashboardPage | 이번주 승인 연차, 공휴일 D-day 5개, 내 대기 신청 |
| /calendar | CalendarPage | FullCalendar, 날짜 다중 선택 토글, 주말·공휴일 클릭 차단, 필터 |
| /leaveManagePage | LeaveManagePage | 전체 승인 내역 + 내 현황 카드 |
| /myInfo | MyInfoPage | 정보 수정 + 연차/복리후생 신청 내역 (subTab) |
| /welfare | WelfarePage | 정책 테이블 → tr 클릭으로 바로 신청 |
| /admin | AdminPage | 8개 탭 (설정/정책/할당/승인(연차·복지)/직원/팀/복지정책/테스트), 1500줄+ |
| /team/leaves | TeamLeavePage | 팀장: 대기/취소대기/로그 탭 |
| /teamInfo | TeamInfoPage | 팀원 목록 + 팀 연차 현황 |

### 3.2 주요 패턴

- **API 레이어**: axios 인스턴스(withCredentials) + 응답 인터셉터에서 에러 toast 일괄 처리. **요청 인터셉터·토큰 갱신 없음**
- **LeaveFormModal**: 드래그 가능한 플로팅 모달 (마우스/터치, clamp로 화면 이탈 방지), 승인자 선택 드롭다운, 신청 후 잔여 연차 미리 계산 표시
- **useConfirm 훅**: Promise 기반 확인 다이얼로그 (`await confirm(msg)`)
- **RecentLeaveList**: 3가지 모드(전체/내연차/복지) 겸용, 300ms 검색 디바운스, 서버사이드 페이징(size=8)
- **constants/welfare.js**: 카테고리×대상 한글 라벨 매핑 + 카테고리 배지 클래스
- **테마**: 파란 계열 #042c53 / #185fa5 / #378add / #b5d4f4 / #eef5fc, Noto Sans KR

### 3.3 프론트 약점

- 권한 가드가 useEffect 내 me() 호출 후 리다이렉트 → 렌더 깜박임, 라우트 가드 컴포넌트 부재
- 전역 상태 관리 없음 (localStorage userInfo 의존)
- 401 처리 미흡 (토스트만 뜨고 로그인 리다이렉트 없음)
- AdminPage 비대 (탭 8개 단일 파일)

---

## 4. 미완성/미해결 목록 (기획서 + 코드 교차 확인)

### 중단 시점에 작업 중이던 것
1. **Google OAuth 전환**: 구현됐으나 @mlsoft.com Workspace 어드민 계정 필요 (테스트 클라이언트로만 확인). 기존 signup/login은 비워둔 상태
2. **복리후생 팀장 승인 권한**: 1차(팀장)/2차(담당자) 승인 필요 여부 질문 대기 → `/pending/my-team` 엔드포인트 주석 처리
3. **이메일 알림**: noreply@mlsoft.com 계정 필요로 보류 (승인 시 담당자 메일 발송 요구사항 있음)

### 검증 안 된 것
- 회계연도 기준(reset_type=false) 초기화 실동작
- 자정 스케줄러 실제 실행
- 생일 반차 UI 흐름
- 당겨쓰기 엣지 케이스 (advance_days 차감이 다음 기산에서 완전히 정리되는지)

### 남은 요구사항 (기획서 🔴 항목)
- 퇴사자 기록 3년 보존 (근로기준법)
- 연차 소진 안내 메일 일괄 발송 (60/90일/분기 기준 대상자 조회 + 관리자 작성 + 발송 내역)
- 부서 드래그앤드롭 하위팀 편성 (코드만 존재, 백엔드 연동 미확인)

### ⚠️ 보안 이슈 (재구축 시 반드시 개선)
- `기능명세서&초기기획서.txt`와 `application.yml`에 **Google OAuth 클라이언트 시크릿, DB 비밀번호(root/1234), JWT secret 평문 커밋** → 새 프로젝트는 환경변수/`.env` 분리 필수, 기존 시크릿은 rotate 권장

---

## 5. 재구축 시 우선순위 제안

**Phase 1 (검증된 코어 이식)**: 엔티티 구조, 연차 신청·승인·취소 워크플로(CANCEL_PENDING 포함), JWT 인증, 승인 이력, 부서 2단계 계층, 근로기준법 정책 엔진

**Phase 2 (미완 기능 완성)**: OAuth 온보딩 흐름 정리(회원가입 대체 여부 결정), 복리후생 승인 라인 확정, 당겨쓰기 정산 로직 검증 + 테스트 코드, 회계연도 초기화 테스트 수단

**Phase 3 (신규)**: 이메일 알림, 연차 소진 안내 일괄 메일, 퇴사자 기록 보존, TypeScript/라우트 가드/전역 상태 등 프론트 구조 개선

상세 원문: 백엔드 `backend/md-files/db-schema.md`, `api-methods.md` / 기획 원문 `기능명세서&초기기획서.txt`
