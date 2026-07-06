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
