import { useMemo, useState } from 'react';
import dayjs from 'dayjs';
import { Inbox } from 'lucide-react';
import PageHeader from '../components/ui/PageHeader.jsx';
import StatusBadge from '../components/ui/StatusBadge.jsx';
import { leaveSummary, myLeaveRequests, LEAVE_TYPE_LABEL } from '../mocks/data.js';

// 테이블 그리드 (docs/05 §④): 신청일 / 종류 / 기간 / 일수 / 사유 / 상태
const GRID_COLS = 'grid grid-cols-[120px_100px_190px_80px_1fr_100px] gap-3';

// 종류 필터 — 반차는 오전·오후를 하나로, 경조복리는 WELFARE 로 묶음
const TYPE_FILTERS = [
  { value: 'ALL', label: '전체' },
  { value: 'ANNUAL', label: '연차', match: (type) => type === 'ANNUAL' },
  { value: 'HALF', label: '반차', match: (type) => type === 'HALF_AM' || type === 'HALF_PM' },
  { value: 'WELFARE', label: LEAVE_TYPE_LABEL.WELFARE, match: (type) => type === 'WELFARE' },
];

// 상태 필터 — 값이 곧 status 코드 (ALL 은 전체)
const STATUS_FILTERS = [
  { value: 'ALL', label: '전체' },
  { value: 'APPROVED', label: '승인' },
  { value: 'PENDING', label: '대기' },
  { value: 'REJECTED', label: '반려' },
  { value: 'CANCELLED', label: '취소' },
  { value: 'CANCEL_PENDING', label: '취소 대기' },
];

// 데이터에 존재하는 연도만 필터 칩으로 노출 (최신 연도 우선)
// ⚠️ mock 파생 계산 — 실제 연동 시 API 응답 기반으로 컴포넌트 내부(useMemo)로 이동
const YEAR_OPTIONS = [...new Set(myLeaveRequests.map((req) => dayjs(req.dates[0]).year()))].sort(
  (a, b) => b - a,
);

// 기간 표기 — 단일일이면 'M.D', 여러 날이면 'M.D ~ M.D'
function formatPeriod(dates) {
  const start = dayjs(dates[0]).format('M.D');
  if (dates.length === 1) return start;
  return `${start} ~ ${dayjs(dates[dates.length - 1]).format('M.D')}`;
}

// 사용 내역 — 내 연차·복리후생 신청 이력 필터·조회 (docs/05 §④)
export default function HistoryPage() {
  const [yearFilter, setYearFilter] = useState(YEAR_OPTIONS[0]);
  const [typeFilter, setTypeFilter] = useState('ALL');
  const [statusFilter, setStatusFilter] = useState('ALL');

  // 통계 스트립 값 (선차감 정책상 '사용'은 확정 + 대기 합산이라 캡션으로 구분)
  const grantedDays = leaveSummary.baseDays + leaveSummary.bonusDays;
  const confirmedUsed = leaveSummary.usedDays - leaveSummary.pendingDays;

  // 필터(연도·종류·상태) 적용 결과
  const filtered = useMemo(() => {
    const typeOption = TYPE_FILTERS.find((option) => option.value === typeFilter);
    return myLeaveRequests.filter((req) => {
      if (dayjs(req.dates[0]).year() !== yearFilter) return false;
      if (typeOption?.match && !typeOption.match(req.type)) return false;
      if (statusFilter !== 'ALL' && req.status !== statusFilter) return false;
      return true;
    });
  }, [yearFilter, typeFilter, statusFilter]);

  return (
    <div>
      <PageHeader title="사용 내역" subtitle="내 연차·복리후생 신청 내역" />

      {/* 필터 칩 행 */}
      <div className="mb-6 flex flex-col gap-3">
        <FilterGroup label="연도">
          {YEAR_OPTIONS.map((year) => (
            <Chip key={year} active={yearFilter === year} onClick={() => setYearFilter(year)}>
              {year}년
            </Chip>
          ))}
        </FilterGroup>
        <FilterGroup label="종류">
          {TYPE_FILTERS.map((option) => (
            <Chip
              key={option.value}
              active={typeFilter === option.value}
              onClick={() => setTypeFilter(option.value)}
            >
              {option.label}
            </Chip>
          ))}
        </FilterGroup>
        <FilterGroup label="상태">
          {STATUS_FILTERS.map((option) => (
            <Chip
              key={option.value}
              active={statusFilter === option.value}
              onClick={() => setStatusFilter(option.value)}
            >
              {option.label}
            </Chip>
          ))}
        </FilterGroup>
      </div>

      {/* 통계 스트립 (박스 없음 — 세로 구분선으로 분리) */}
      <div className="mb-6 flex items-end divide-x divide-white/8 border-b border-white/6 pb-6">
        <Stat label="총 부여" value={grantedDays} unit="일" />
        <div className="pl-8">
          <Stat
            label="사용"
            value={leaveSummary.usedDays}
            unit="일"
            caption={
              leaveSummary.pendingDays > 0
                ? `확정 ${confirmedUsed}일 · 대기 ${leaveSummary.pendingDays}일`
                : null
            }
          />
        </div>
        <div className="pl-8">
          <Stat label="잔여" value={leaveSummary.remainingDays} unit="일" tone="text-accent-light" />
        </div>
        <div className="pl-8">
          <Stat label="소멸 예정" value={leaveSummary.expiringDays} unit="일" tone="text-warn" />
        </div>
      </div>

      {/* 테이블 카드 */}
      <section className="overflow-hidden rounded-card bg-navy-card shadow-card">
        <div className="overflow-x-auto">
          <div className="min-w-[760px]">
            {/* 헤더 행 */}
            <div
              className={`${GRID_COLS} border-b border-white/6 bg-navy-header px-5 py-3 text-[12px] font-semibold text-ink-mute`}
            >
              <span>신청일</span>
              <span>종류</span>
              <span>기간</span>
              <span className="text-right">일수</span>
              <span>사유</span>
              <span>상태</span>
            </div>

            {/* 데이터 행 / 빈 상태 */}
            {filtered.length === 0 ? (
              <div className="flex flex-col items-center justify-center gap-2 px-5 py-16 text-center">
                <Inbox size={28} className="text-ink-dim" />
                <p className="text-[13px] text-ink-mute">조건에 맞는 신청 내역이 없습니다.</p>
              </div>
            ) : (
              <div className="divide-y divide-white/5">
                {filtered.map((req) => (
                  <div
                    key={req.id}
                    className={`${GRID_COLS} items-center px-5 py-3.5 text-[13px] transition-colors hover:bg-white/3`}
                  >
                    <span className="text-ink-body">{dayjs(req.appliedAt).format('YYYY.MM.DD')}</span>
                    <span className="font-medium text-ink-hi">{LEAVE_TYPE_LABEL[req.type]}</span>
                    <span className="text-ink-body">{formatPeriod(req.dates)}</span>
                    <span className="text-right font-semibold text-ink-hi">{req.days}일</span>
                    <span className="min-w-0 truncate text-ink-mute" title={req.reason}>
                      {req.reason}
                    </span>
                    <span>
                      <StatusBadge status={req.status} />
                    </span>
                  </div>
                ))}
              </div>
            )}
          </div>
        </div>
      </section>
    </div>
  );
}

// 필터 그룹 — 라벨 + 칩들
function FilterGroup({ label, children }) {
  return (
    <div className="flex items-center gap-3">
      <span className="w-9 shrink-0 text-[12px] font-semibold text-ink-faint">{label}</span>
      <div className="flex flex-wrap items-center gap-2">{children}</div>
    </div>
  );
}

// 필터 칩 — 활성 시 accent 틴트
function Chip({ active, onClick, children }) {
  return (
    <button
      type="button"
      onClick={onClick}
      className={`rounded-badge px-3 py-1.5 text-[12px] font-semibold transition-colors ${
        active ? 'bg-accent/16 text-accent-light' : 'bg-navy-btn2 text-ink-mute hover:text-ink-body'
      }`}
    >
      {children}
    </button>
  );
}

// 통계 스트립 한 칸 (박스 없음 — 대시보드 Stat 스타일)
function Stat({ label, value, unit, tone, caption }) {
  return (
    <div className="flex flex-col gap-1 pr-8">
      <span className="text-[12px] font-medium text-ink-mute">{label}</span>
      <span className={`text-[28px] font-bold leading-none ${tone ?? 'text-ink-hi'}`}>
        {value}
        {unit && <span className="ml-1 text-[13px] font-medium text-ink-mute">{unit}</span>}
      </span>
      {caption && <span className="text-[11px] text-ink-faint">{caption}</span>}
    </div>
  );
}
