/**
 * 다음 회차 예약 표시 문구 — 잔여 연차를 보여주는 화면 4곳(대시보드·사이드바·내 정보·사용 내역)이
 * 같은 문구를 써야 한다(QA 7-7). 라벨을 한 곳에서 관리해 화면마다 표기가 갈라지지 않게 한다.
 */
export const NEXT_CYCLE_RESERVATION_LABEL = '다음 회차 예약';

// nextCycleReservedDays가 0이면 이 줄 자체를 감춘다 — 예약이 없는데 "0일"을 보여주면 소음이다.
export function hasNextCycleReservation(nextCycleReservedDays) {
  return Number(nextCycleReservedDays) > 0;
}

export function formatNextCycleReservation(nextCycleReservedDays) {
  return `${NEXT_CYCLE_RESERVATION_LABEL} ${Number(nextCycleReservedDays)}일`;
}
