// 연차 종류·신청 상태 라벨/색 매핑 — 백엔드 enum(LeaveType/RequestStatus)과 1:1.
// 공용 컴포넌트(StatusBadge)와 페이지들이 함께 사용하므로 mock이 아닌 constants에 둔다 (검증 F3).

export const LEAVE_TYPE_LABEL = {
  ANNUAL: '연차',
  HALF_AM: '오전 반차',
  HALF_PM: '오후 반차',
  WELFARE: '경조/복리',
};

// 개인 일정 종류 (외근·출장·재택·교육) — 백엔드 ScheduleType enum과 1:1.
// 연차와 달리 잔액을 차감하지 않고 결재도 없어서 LEAVE_TYPE_LABEL과 분리해 둔다.
// 서버가 /api/schedules/types로 라벨을 내려주므로 이건 폴백이다 —
// 캘린더 렌더처럼 목록 응답만 있는 자리에서 쓴다.
export const SCHEDULE_TYPE_LABEL = {
  FIELD_WORK: '외근',
  BUSINESS_TRIP: '출장',
  REMOTE: '재택근무',
  TRAINING: '교육·연수',
};

// 일정 종류 → 색 토큰. 연차(accent-cyan 계열)와 확실히 구분되게 골랐다 —
// 캘린더 한 칸에 둘이 같이 뜨므로 색이 겹치면 "쉬는 사람"과 "일하는 사람"이 헷갈린다.
export const SCHEDULE_TYPE_TONE = {
  FIELD_WORK: 'amber',
  BUSINESS_TRIP: 'violet',
  REMOTE: 'emerald',
  TRAINING: 'sky',
};

export const STATUS_LABEL = {
  APPROVED: '승인',
  PENDING: '대기',
  REJECTED: '반려',
  CANCELLED: '취소',
  CANCEL_PENDING: '취소 대기',
};

// 상태 → 색 토큰 (StatusBadge와 동일 기준)
export const STATUS_TONE = {
  APPROVED: 'ok',
  PENDING: 'warn',
  REJECTED: 'danger',
  CANCELLED: 'muted',
  CANCEL_PENDING: 'warn',
};

// 처리 이력 액션 라벨 — 백엔드 RequestAction enum 7종과 1:1 (docs/02 3-5).
// 상태(STATUS_LABEL)와 값이 겹치지만 의미가 다르다 — 상태는 "지금 어떤 상태인가",
// 액션은 "그때 무슨 일이 일어났는가"라서 PENDING을 각각 '대기'/'신청'으로 다르게 읽는다.
export const ACTION_LABEL = {
  PENDING: '신청',
  APPROVED: '승인',
  REJECTED: '반려',
  CANCELLED: '취소',
  CANCEL_PENDING: '취소 요청',
  CANCEL_APPROVED: '취소 승인',
  CANCEL_REJECTED: '취소 거부',
};

// 액션 → 색 토큰. 신청 접수는 정보(accent), 승인 계열은 ok, 거부 계열은 danger,
// 취소 완료는 중립(muted) — 로그를 훑을 때 반려·거부가 먼저 눈에 들어오게 한다.
export const ACTION_TONE = {
  PENDING: 'accent',
  APPROVED: 'ok',
  REJECTED: 'danger',
  CANCELLED: 'muted',
  CANCEL_PENDING: 'warn',
  CANCEL_APPROVED: 'muted',
  CANCEL_REJECTED: 'danger',
};

// 관리자 조작 감사 로그 액션 → 색 토큰 (리뷰 S-3).
// **라벨은 여기 없다** — 서버가 GET /api/admin/audit-logs/actions로 내려준다.
// 색만 두는 이유: 새 조작이 추가되면 라벨·필터는 서버 카탈로그를 따라 자동으로 붙고,
// 색만 기본값(muted)으로 떨어져 화면이 깨지지 않는다.
// 연차·잔액을 직접 바꾸는 조작(warn)과 계정을 잠그는 조작(danger)이 먼저 눈에 들어오게 골랐다.
export const ADMIN_ACTION_TONE = {
  ROLE_CHANGED: 'accent',
  DEPARTMENT_CHANGED: 'accent',
  BASE_DAYS_CHANGED: 'warn',
  USER_RETIRED: 'danger',
  ONBOARDING_APPROVED: 'ok',
  ONBOARDING_REJECTED: 'danger',
  CONFIG_CHANGED: 'warn',
};

// 이메일 유형·상태 — 백엔드 EmailType/EmailStatus enum과 1:1 (docs/02 3-12).
// 연차 신청 상태(STATUS_*)와 값이 겹치지 않아 별도 맵으로 둔다 —
// 같은 맵에 합치면 'PENDING'이 "결재 대기"인지 "발송 대기"인지 읽는 쪽에서 갈린다.
export const EMAIL_TYPE_LABEL = {
  LEAVE: '연차',
  WELFARE: '복리후생',
  REMINDER: '소진 안내',
  NOTICE: '공지',
};

export const EMAIL_STATUS_LABEL = {
  PENDING: '발송 대기',
  SENDING: '발송 중',
  SENT: '발송 완료',
  FAILED: '실패',
};

// 실패만 danger로 세운다 — 이력을 훑는 목적이 "무엇이 안 나갔는가"이기 때문이다.
export const EMAIL_STATUS_TONE = {
  PENDING: 'warn',
  SENDING: 'accent',
  SENT: 'ok',
  FAILED: 'danger',
};
