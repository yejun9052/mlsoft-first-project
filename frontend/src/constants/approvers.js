// 서브 승인자 후보 — 승인자 후보 API가 아직 없어(다음에 직접 설계 예정) 로컬 DB에 미리
// 넣어둔 실제 TEAM_LEADER 테스트 계정(id 2, 3)을 임시로 하드코딩. 실제 존재하는 유저라
// 신청 시 정상적으로 서브 승인자로 지정된다. API가 생기면 이 배열만 걷어내면 됨.
// (연차 신청 패널 · 복리후생 신청 모달이 함께 사용 — 값이 갈라지지 않도록 한 곳에서 관리)
export const TEST_APPROVER_CANDIDATES = [
  { id: 2, name: '테스트팀장1', departmentName: '미배정' },
  { id: 3, name: '테스트팀장2', departmentName: '미배정' },
];
