// ─────────────────────────────────────────────────────────────────────────────
// 목업 데이터 (UI 스캐폴딩 전용 — 실제 API 연동 전 임시 데이터)
//
// ⚠️ 이 파일은 화면 확인용 mock 입니다. 실제 서버 연동 시 각 페이지의 import 를
//    src/api/* 호출로 교체하고 이 파일은 삭제하세요. (프로젝트 원칙: API 연동은 직접)
//
// 가상 회사 MLsoft — 부서 3개(개발팀/디자인팀/경영지원팀) + 미배정.
// 모든 날짜 기준(today)은 2026-07-05 로 앵커링. 값들은 서로 정합적으로 맞춰져 있음.
// ─────────────────────────────────────────────────────────────────────────────

import { ROLE } from '../constants/roles.js';

/** 데모 기준일 (모든 mock 계산의 '오늘') */
export const TODAY = '2026-07-05';

// ── 부서 ─────────────────────────────────────────────────────────────────────
export const departments = [
  { id: 1, name: '개발팀', leaderId: 11, leaderName: '김철수', memberCount: 5, description: '백엔드·프론트엔드 개발' },
  { id: 2, name: '디자인팀', leaderId: 21, leaderName: '이영희', memberCount: 3, description: 'UI/UX·브랜드 디자인' },
  { id: 3, name: '경영지원팀', leaderId: 31, leaderName: '박민수', memberCount: 3, description: '인사·회계·총무' },
  { id: 99, name: '미배정', leaderId: null, leaderName: null, memberCount: 1, description: '부서 배정 전 기본 소속' },
];

// ── 구성원 ───────────────────────────────────────────────────────────────────
// baseDays: 정책 기본 연차, bonusDays: 복리후생 가산, usedDays: 사용(선차감 포함),
// remainingDays = base+bonus-used. isActive=false 는 퇴직자.
export const members = [
  { id: 11, name: '김철수', email: 'kim@mlsoft.com', role: ROLE.TEAM_LEADER, departmentId: 1, departmentName: '개발팀', position: '팀장', hireDate: '2018-03-02', birthDay: '1988-05-12', baseDays: 25, bonusDays: 1, usedDays: 8.5, isActive: true },
  { id: 12, name: '정우성', email: 'jung@mlsoft.com', role: ROLE.EMPLOYEE, departmentId: 1, departmentName: '개발팀', position: '시니어 개발자', hireDate: '2020-07-01', birthDay: '1992-11-03', baseDays: 18, bonusDays: 0, usedDays: 6, isActive: true },
  { id: 13, name: '한지민', email: 'han@mlsoft.com', role: ROLE.EMPLOYEE, departmentId: 1, departmentName: '개발팀', position: '개발자', hireDate: '2022-09-15', birthDay: '1995-02-20', baseDays: 16, bonusDays: 0, usedDays: 3.5, isActive: true },
  { id: 14, name: '오세훈', email: 'oh@mlsoft.com', role: ROLE.EMPLOYEE, departmentId: 1, departmentName: '개발팀', position: '주니어 개발자', hireDate: '2025-01-06', birthDay: '1998-08-30', baseDays: 11, bonusDays: 0, usedDays: 2, isActive: true },
  { id: 15, name: '신동엽', email: 'shin@mlsoft.com', role: ROLE.EMPLOYEE, departmentId: 1, departmentName: '개발팀', position: '개발자', hireDate: '2021-11-22', birthDay: '1990-04-18', baseDays: 17, bonusDays: 2, usedDays: 11, isActive: true },
  { id: 21, name: '이영희', email: 'lee.yh@mlsoft.com', role: ROLE.TEAM_LEADER, departmentId: 2, departmentName: '디자인팀', position: '팀장', hireDate: '2019-06-03', birthDay: '1987-09-09', baseDays: 22, bonusDays: 0, usedDays: 5, isActive: true },
  { id: 22, name: '강하늘', email: 'kang@mlsoft.com', role: ROLE.EMPLOYEE, departmentId: 2, departmentName: '디자인팀', position: 'UI 디자이너', hireDate: '2023-02-13', birthDay: '1996-12-25', baseDays: 15, bonusDays: 0, usedDays: 4, isActive: true },
  { id: 23, name: '수지', email: 'suzy@mlsoft.com', role: ROLE.EMPLOYEE, departmentId: 2, departmentName: '디자인팀', position: 'UX 디자이너', hireDate: '2024-08-01', birthDay: '1999-10-10', baseDays: 12, bonusDays: 0, usedDays: 1.5, isActive: true },
  { id: 31, name: '박민수', email: 'park@mlsoft.com', role: ROLE.TEAM_LEADER, departmentId: 3, departmentName: '경영지원팀', position: '팀장', hireDate: '2017-04-10', birthDay: '1985-01-27', baseDays: 25, bonusDays: 0, usedDays: 9, isActive: true },
  { id: 32, name: '전지현', email: 'jeon@mlsoft.com', role: ROLE.SYSTEM_ADMIN, departmentId: 3, departmentName: '경영지원팀', position: '인사 매니저', hireDate: '2018-08-20', birthDay: '1989-06-15', baseDays: 24, bonusDays: 1, usedDays: 7.5, isActive: true },
  { id: 33, name: '차은우', email: 'cha@mlsoft.com', role: ROLE.EMPLOYEE, departmentId: 3, departmentName: '경영지원팀', position: '회계 담당', hireDate: '2023-05-02', birthDay: '1997-03-30', baseDays: 15, bonusDays: 1, usedDays: 6, isActive: true },
  // 퇴직자 (데이터 보존 — 3년 이상)
  { id: 41, name: '송중기', email: 'song@mlsoft.com', role: ROLE.EMPLOYEE, departmentId: 1, departmentName: '개발팀', position: '개발자', hireDate: '2019-02-01', birthDay: '1991-09-19', baseDays: 20, bonusDays: 0, usedDays: 12, isActive: false, retiredAt: '2026-03-31' },
].map((m) => ({ ...m, remainingDays: round1(m.baseDays + m.bonusDays - m.usedDays) }));

// ── 현재 로그인 사용자 (me) ───────────────────────────────────────────────────
// 실제 로그인 유저의 이름/역할은 localStorage(userInfo)에서 가져오고, 화면용 상세
// 값(부서·연차·입사일 등)은 mock 으로 보강. → 사이드바(실제 유저)와 이름이 일치.
const MOCK_ME = {
  id: 32,
  name: '전지현',
  email: 'jeon@mlsoft.com',
  role: ROLE.SYSTEM_ADMIN, // 관리자 화면까지 확인 가능하도록 데모는 총관리자
  departmentId: 3,
  departmentName: '경영지원팀',
  position: '인사 매니저',
  hireDate: '2018-08-20',
  birthDay: '1989-06-15',
  phone: '010-1234-5678',
  baseDays: 24,
  bonusDays: 1, // 형제·자매 결혼(2001 승인)으로 가산 — myWelfareRequests와 정합
  usedDays: 7.5, // 승인 5.5(0.5+3+1+1) + 대기 선차감 2 — myLeaveRequests와 정합 (검증 F2)
  advanceDays: 0,
};

/** 화면용 현재 사용자 — 실제 로그인 정보(localStorage) + mock 상세 병합 */
export function getCurrentUser() {
  let stored = null;
  try {
    stored = JSON.parse(localStorage.getItem('userInfo'));
  } catch {
    stored = null;
  }
  const merged = {
    ...MOCK_ME,
    ...(stored?.name ? { name: stored.name } : {}),
    ...(stored?.email ? { email: stored.email } : {}),
    ...(stored?.role ? { role: stored.role } : {}),
    ...(stored?.departmentName ? { departmentName: stored.departmentName } : {}),
  };
  return { ...merged, remainingDays: round1(merged.baseDays + merged.bonusDays - merged.usedDays) };
}

// ── 내 연차 요약 (대시보드·사용내역 통계 스트립) — myLeaveRequests 합계와 정합 (검증 F2)
export const leaveSummary = {
  baseDays: 24, // 정책 기본 연차
  bonusDays: 1, // 복리후생 가산 (형제·자매 결혼 승인 2001)
  usedDays: 7.5, // 사용 선차감 포함 = 승인 5.5(0.5+3+1+1) + 대기 2
  pendingDays: 2, // 대기 중(선차감된) 일수 — 신청 1001(7/20~21)
  remainingDays: 17.5, // base+bonus-used = 25-7.5
  expiringDays: 17.5, // 다음 기산일까지 미사용 → 소멸 예정
  nextResetDate: '2026-08-20', // 입사일 기준 다음 기산일
};

// ── 공휴일 (2026) — 실제로는 data.go.kr 연동 ─────────────────────────────────
export const holidays = [
  { date: '2026-01-01', name: '신정' },
  { date: '2026-02-16', name: '설날' },
  { date: '2026-02-17', name: '설날' },
  { date: '2026-02-18', name: '설날' },
  { date: '2026-03-01', name: '삼일절' },
  { date: '2026-03-02', name: '대체공휴일' },
  { date: '2026-05-05', name: '어린이날' },
  { date: '2026-05-24', name: '부처님오신날' },
  { date: '2026-06-06', name: '현충일' },
  { date: '2026-07-17', name: '창립기념일' }, // 데모용 사내 휴일 (7월 색상 확인)
  { date: '2026-08-15', name: '광복절' },
  { date: '2026-09-24', name: '추석' },
  { date: '2026-09-25', name: '추석' },
  { date: '2026-09-26', name: '추석' },
  { date: '2026-10-03', name: '개천절' },
  { date: '2026-10-09', name: '한글날' },
  { date: '2026-12-25', name: '성탄절' },
];

// ── 내 연차 신청 내역 (사용 내역 페이지) ─────────────────────────────────────
// status: APPROVED | PENDING | REJECTED | CANCELLED | CANCEL_PENDING
// type:   ANNUAL | HALF_AM | HALF_PM | WELFARE
export const myLeaveRequests = [
  { id: 1001, type: 'ANNUAL', dates: ['2026-07-20', '2026-07-21'], days: 2, reason: '개인 휴가 (여행)', status: 'PENDING', appliedAt: '2026-07-03', approver: '박민수', subApprover: null },
  { id: 1002, type: 'HALF_PM', dates: ['2026-07-10'], days: 0.5, reason: '병원 진료', status: 'APPROVED', appliedAt: '2026-07-01', approver: '박민수', subApprover: null },
  { id: 1003, type: 'ANNUAL', dates: ['2026-06-15', '2026-06-16', '2026-06-17'], days: 3, reason: '가족 행사', status: 'APPROVED', appliedAt: '2026-06-05', approver: '박민수', subApprover: '전지현' },
  { id: 1004, type: 'WELFARE', dates: ['2026-05-28'], days: 1, reason: '형제·자매 결혼 (경조 휴가)', status: 'APPROVED', appliedAt: '2026-05-10', approver: '박민수', subApprover: null },
  { id: 1005, type: 'HALF_AM', dates: ['2026-04-22'], days: 0.5, reason: '개인 사유', status: 'REJECTED', appliedAt: '2026-04-20', approver: '박민수', subApprover: null },
  { id: 1006, type: 'ANNUAL', dates: ['2026-03-09', '2026-03-10'], days: 2, reason: '휴식', status: 'CANCELLED', appliedAt: '2026-03-01', approver: '박민수', subApprover: null },
  { id: 1007, type: 'ANNUAL', dates: ['2026-02-02'], days: 1, reason: '설 연휴 연장', status: 'APPROVED', appliedAt: '2026-01-20', approver: '박민수', subApprover: null },
];

// ── 팀 캘린더 이벤트 (승인된 연차 — 대시보드·캘린더) ─────────────────────────
// personName 이 me(전지현)이면 mine=true. 사유는 마스킹 정책상 캘린더엔 미노출.
export const calendarLeaves = [
  { date: '2026-07-02', personName: '한지민', type: 'ANNUAL', mine: false },
  { date: '2026-07-03', personName: '한지민', type: 'ANNUAL', mine: false },
  { date: '2026-07-08', personName: '강하늘', type: 'HALF_PM', mine: false },
  { date: '2026-07-10', personName: '전지현', type: 'HALF_PM', mine: true },
  { date: '2026-07-13', personName: '정우성', type: 'ANNUAL', mine: false },
  { date: '2026-07-14', personName: '정우성', type: 'ANNUAL', mine: false },
  { date: '2026-07-16', personName: '차은우', type: 'ANNUAL', mine: false },
  // 전지현 7/20~21(신청 1001)은 PENDING이라 미표시 — 캘린더는 승인 건만 (검증 F2)
  { date: '2026-07-23', personName: '신동엽', type: 'ANNUAL', mine: false },
  { date: '2026-07-24', personName: '수지', type: 'HALF_AM', mine: false },
  { date: '2026-07-28', personName: '김철수', type: 'ANNUAL', mine: false },
  { date: '2026-07-29', personName: '오세훈', type: 'ANNUAL', mine: false },
];

/** 특정 연·월의 캘린더 셀 데이터 (연차 + 공휴일)를 날짜별로 묶어 반환 */
export function getCalendarData(year, month /* 1-12 */) {
  const mm = String(month).padStart(2, '0');
  const prefix = `${year}-${mm}-`;
  const map = {};
  for (const ev of calendarLeaves) {
    if (!ev.date.startsWith(prefix)) continue;
    const day = Number(ev.date.slice(8, 10));
    (map[day] ??= { leaves: [], holiday: null }).leaves.push(ev);
  }
  for (const h of holidays) {
    if (!h.date.startsWith(prefix)) continue;
    const day = Number(h.date.slice(8, 10));
    (map[day] ??= { leaves: [], holiday: null }).holiday = h.name;
  }
  return map;
}

// ── 복리후생 정책 (복리후생 페이지 — 6개 카테고리) ───────────────────────────
// icon 은 lucide-react 아이콘 '이름' 문자열. 페이지에서 아이콘 컴포넌트로 매핑.
export const welfareCategories = [
  {
    key: 'family', label: '경조사', icon: 'HeartHandshake', color: 'danger',
    items: [
      { id: 1, name: '본인 결혼', days: 7, target: '본인', proof: '청첩장 또는 혼인관계증명서' },
      { id: 2, name: '형제·자매 결혼', days: 1, target: '형제/자매', proof: '청첩장 및 가족관계증명서' },
      { id: 3, name: '배우자 출산', days: 5, target: '배우자', proof: '출생증명서' },
      { id: 4, name: '부모 조의', days: 7, target: '부모', proof: '부고장 또는 가족관계증명서' },
      { id: 5, name: '조부모 조의', days: 3, target: '조부모', proof: '부고장 또는 가족관계증명서' },
    ],
  },
  {
    key: 'health', label: '건강검진', icon: 'Stethoscope', color: 'ok',
    items: [
      { id: 6, name: '종합 건강검진', days: 1, target: '본인', proof: '검진 예약 확인서' },
      { id: 7, name: '가족 건강검진 동행', days: 0.5, target: '직계가족', proof: '검진 확인서' },
    ],
  },
  {
    key: 'growth', label: '자기계발', icon: 'GraduationCap', color: 'accent',
    items: [
      { id: 8, name: '자격증 시험 응시', days: 1, target: '본인', proof: '수험표' },
      { id: 9, name: '본인 졸업', days: 1, target: '본인', proof: '졸업증명서' },
    ],
  },
  {
    key: 'gift', label: '명절 선물', icon: 'Gift', color: 'warn',
    items: [
      { id: 10, name: '설 명절 선물', days: 0, target: '전 직원', proof: '자동 지급' },
      { id: 11, name: '추석 명절 선물', days: 0, target: '전 직원', proof: '자동 지급' },
    ],
  },
  {
    key: 'club', label: '동호회', icon: 'Users', color: 'purple',
    items: [
      { id: 12, name: '사내 동호회 활동비', days: 0, target: '동호회원', proof: '활동 내역' },
    ],
  },
  {
    key: 'refresh', label: '리프레시', icon: 'Sun', color: 'cyan',
    items: [
      { id: 13, name: '근속 5년 리프레시 휴가', days: 5, target: '근속 5년', proof: '자동 부여' },
      { id: 14, name: '근속 10년 리프레시 휴가', days: 10, target: '근속 10년', proof: '자동 부여' },
    ],
  },
];

/** 복리후생 정책 플랫 목록 (신청 폼 드롭다운용) */
export const welfarePolicies = welfareCategories.flatMap((c) =>
  c.items.map((it) => ({ ...it, category: c.label })),
);

// ── 내 복리후생 신청 내역 — 2001 승인이 bonusDays 1 가산의 근거 (연차 사용 1004와 짝, 검증 F2)
export const myWelfareRequests = [
  { id: 2001, category: '경조사', name: '형제·자매 결혼', days: 1, reason: '2026-05-28 형제 결혼', status: 'APPROVED', appliedAt: '2026-05-10', approver: '박민수' },
  { id: 2002, category: '건강검진', name: '종합 건강검진', days: 1, reason: '연 1회 정기 검진', status: 'PENDING', appliedAt: '2026-07-02', approver: '박민수' },
];

// 처리 완료된 결재 (승인/반려 탭) — 대기 목록은 실 API(GET /api/leaves/pending)로 대체되어 여기선 삭제됨
export const processedApprovals = [
  { id: 3101, kind: 'LEAVE', applicantName: '정우성', applicantDept: '개발팀', type: 'ANNUAL', dates: ['2026-07-13', '2026-07-14'], days: 2, reason: '여름 휴가', status: 'APPROVED', processedAt: '2026-07-02', comment: '승인합니다.' },
  { id: 3102, kind: 'LEAVE', applicantName: '수지', applicantDept: '디자인팀', type: 'HALF_AM', dates: ['2026-07-24'], days: 0.5, reason: '개인 사유', status: 'APPROVED', processedAt: '2026-07-01', comment: '' },
  { id: 3103, kind: 'LEAVE', applicantName: '신동엽', applicantDept: '개발팀', type: 'ANNUAL', dates: ['2026-05-02'], days: 1, reason: '사유 불충분', status: 'REJECTED', processedAt: '2026-04-28', comment: '업무 일정 조정 후 재신청 바랍니다.' },
];

// ── 관리자: 연차 정책 (근속년수별 1~21년차) ──────────────────────────────────
// 근로기준법 §60: MIN(15 + (년차-1)/2, 25)
export const leavePolicies = Array.from({ length: 21 }, (_, i) => {
  const year = i + 1;
  return { id: year, years: year, days: Math.min(15 + Math.floor((year - 1) / 2), 25) };
});

// ── 관리자: 연차 시스템 설정 ─────────────────────────────────────────────────
export const adminConfigs = [
  { name: 'advance_leave_enabled', label: '연차 당겨쓰기 허용', value: 'false', type: 'boolean', description: '잔여가 부족해도 당겨쓰기로 접수 (다음 기산일 정산)' },
  { name: 'reminder_list_days', label: '소진 안내 기준일', value: '30', type: 'number', description: '기산일 N일 전부터 소진 안내 대상에 표시' },
  { name: 'reminder_auto_cycle', label: '자동 발송 주기', value: 'NONE', type: 'select', options: ['NONE', 'D30', 'D60', 'D90', 'QUARTER'], description: '기산일 임박 사원에게 자동 메일 발송 주기' },
];

// ── 관리자: 기산일 리셋·소멸 이력 ────────────────────────────────────────────
export const resetHistories = [
  { id: 1, userName: '박민수', resetDate: '2026-04-10', prevBase: 24, prevUsed: 20, expiredDays: 4 },
  { id: 2, userName: '차은우', resetDate: '2026-05-02', prevBase: 15, prevUsed: 15, expiredDays: 0 },
  { id: 3, userName: '이영희', resetDate: '2026-06-03', prevBase: 22, prevUsed: 14, expiredDays: 8 },
];

// ── 라벨 매핑 — constants/status.js로 이동 (검증 F3: 공용 컴포넌트의 mock 의존 제거).
// 기존 페이지들의 import 경로 호환을 위해 재수출만 유지 — mock 삭제 시 각 페이지 import를 constants로 교체.
export { LEAVE_TYPE_LABEL, STATUS_LABEL, STATUS_TONE } from '../constants/status.js';

// 소수 첫째자리 반올림 (연차 0.5일 단위)
function round1(n) {
  return Math.round(n * 10) / 10;
}
