import { useMemo, useState } from 'react';
import dayjs from 'dayjs';
import { Loader2 } from 'lucide-react';
import PageHeader from '../components/ui/PageHeader.jsx';
import StatusBadge from '../components/ui/StatusBadge.jsx';
import Stat from '../components/ui/Stat.jsx';
import StatStrip from '../components/ui/StatStrip.jsx';
import Chip from '../components/ui/Chip.jsx';
import FilterGroup from '../components/ui/FilterGroup.jsx';
import TableCard from '../components/ui/TableCard.jsx';
import Table, { THead, Th, TR, Td } from '../components/ui/Table.jsx';
import { LEAVE_TYPE_LABEL } from '../constants/status.js';
import { useLeaveSummary, useMyLeaves } from '../hooks/useLeaves.js';

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

// 기간 표기 — 단일일이면 'M.D', 여러 날이면 'M.D ~ M.D'
function formatPeriod(dates) {
  const start = dayjs(dates[0]).format('M.D');
  if (dates.length === 1) return start;
  return `${start} ~ ${dayjs(dates[dates.length - 1]).format('M.D')}`;
}

// 사유 셀 — truncate + hover 시 전문을 툴팁으로 노출 (한 줄 요약은 유지하되 전문 확인 수단 제공)
function ReasonCell({ reason }) {
  return (
    <span className="group relative inline-block max-w-[220px] truncate align-bottom">
      {reason}
      <span className="pointer-events-none absolute left-0 top-full z-10 mt-1.5 hidden w-max max-w-xs rounded-btn border border-white/10 bg-navy-header px-3 py-2 text-[12px] leading-relaxed text-ink-body opacity-0 shadow-card transition-opacity group-hover:block group-hover:opacity-100">
        {reason}
      </span>
    </span>
  );
}

// 사용 내역 — 내 연차·복리후생 신청 이력 필터·조회 (docs/05 §④)
// WELFARE 항목은 복리후생 API가 아직 없어 이 목록엔 나타나지 않는다 (종류 필터의 '경조/복리'는 항상 빈 결과).
export default function HistoryPage() {
  const { data: page, isLoading } = useMyLeaves();
  const { data: summary } = useLeaveSummary();
  const myLeaveRequests = useMemo(() => page?.content ?? [], [page]);

  // 데이터에 존재하는 연도만 필터 칩으로 노출 (최신 연도 우선)
  const yearOptions = useMemo(
    () => [...new Set(myLeaveRequests.map((req) => dayjs(req.dates[0]).year()))].sort((a, b) => b - a),
    [myLeaveRequests],
  );
  const [yearFilter, setYearFilter] = useState(null);
  const [typeFilter, setTypeFilter] = useState('ALL');
  const [statusFilter, setStatusFilter] = useState('ALL');
  const activeYear = yearFilter ?? yearOptions[0];

  // 필터(연도·종류·상태) 적용 결과
  const filtered = useMemo(() => {
    const typeOption = TYPE_FILTERS.find((option) => option.value === typeFilter);
    return myLeaveRequests.filter((req) => {
      if (dayjs(req.dates[0]).year() !== activeYear) return false;
      if (typeOption?.match && !typeOption.match(req.leaveType)) return false;
      if (statusFilter !== 'ALL' && req.status !== statusFilter) return false;
      return true;
    });
  }, [myLeaveRequests, activeYear, typeFilter, statusFilter]);

  if (isLoading || !summary) {
    return (
      <div className="flex h-40 items-center justify-center gap-2 text-ink-mute">
        <Loader2 size={18} className="animate-spin" />
        <span className="text-[13px]">불러오는 중…</span>
      </div>
    );
  }

  // 통계 스트립 값 (선차감 정책상 '사용'은 확정 + 대기 합산이라 캡션으로 구분)
  const grantedDays = Number(summary.baseDays) + Number(summary.bonusDays);
  const confirmedUsed = Number(summary.useDays) - Number(summary.pendingDays);

  return (
    <div>
      <PageHeader title="사용 내역" subtitle="내 연차·복리후생 신청 내역" />

      {/* 필터 칩 — 한 행으로 압축(연도/종류/상태), 좁은 화면에서만 줄바꿈 */}
      <div className="mb-6 flex flex-wrap items-center gap-x-8 gap-y-3">
        <FilterGroup label="연도">
          {yearOptions.map((year) => (
            <Chip key={year} active={activeYear === year} onClick={() => setYearFilter(year)}>
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
      <StatStrip className="mb-6 border-b border-white/6 pb-6">
        <Stat label="총 부여" value={grantedDays} unit="일" />
        <Stat
          label="사용"
          value={summary.useDays}
          unit="일"
          caption={
            Number(summary.pendingDays) > 0
              ? `확정 ${confirmedUsed}일 · 대기 ${summary.pendingDays}일`
              : null
          }
        />
        <Stat label="잔여" value={summary.remainingDays} unit="일" tone="text-accent-light" />
        {/* 미사용 이월 없이 소멸되는 정책이라 소멸 예정 = 잔여와 동일 (백엔드에 별도 필드 없음) */}
        <Stat label="소멸 예정" value={summary.remainingDays} unit="일" tone="text-warn" />
      </StatStrip>

      {/* 테이블 카드 */}
      <TableCard empty={filtered.length === 0} emptyLabel="조건에 맞는 신청 내역이 없습니다.">
        <Table className="min-w-[760px]">
          <THead>
            <Th>신청일</Th>
            <Th>종류</Th>
            <Th>기간</Th>
            <Th right>일수</Th>
            <Th>사유</Th>
            <Th>상태</Th>
          </THead>
          <tbody>
            {filtered.map((req) => (
              <TR key={req.id}>
                <Td>{dayjs(req.createdAt).format('YYYY.MM.DD')}</Td>
                <Td className="font-medium text-ink-hi">{LEAVE_TYPE_LABEL[req.leaveType]}</Td>
                <Td>{formatPeriod(req.dates)}</Td>
                <Td right className="font-semibold text-ink-hi">
                  {req.days}일
                </Td>
                <Td className="text-ink-mute">
                  <ReasonCell reason={req.requestReason} />
                </Td>
                <Td>
                  <StatusBadge status={req.status} />
                </Td>
              </TR>
            ))}
          </tbody>
        </Table>
      </TableCard>
    </div>
  );
}
