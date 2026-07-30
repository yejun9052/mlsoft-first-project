// 연차 종류·신청 상태 라벨/색 매핑 — 백엔드 enum(LeaveType/RequestStatus)과 1:1.
// 공용 컴포넌트(StatusBadge)와 페이지들이 함께 사용하므로 mock이 아닌 constants에 둔다 (검증 F3).

export const LEAVE_TYPE_LABEL = {
  ANNUAL: '연차',
  HALF_AM: '오전 반차',
  HALF_PM: '오후 반차',
  WELFARE: '경조/복리',
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
