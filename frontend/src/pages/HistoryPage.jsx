import { useEffect, useMemo, useState } from 'react';
import dayjs from 'dayjs';
import toast from 'react-hot-toast';
import { Loader2, X } from 'lucide-react';
import PageHeader from '../components/ui/PageHeader.jsx';
import StatusBadge from '../components/ui/StatusBadge.jsx';
import Stat from '../components/ui/Stat.jsx';
import StatStrip from '../components/ui/StatStrip.jsx';
import Chip from '../components/ui/Chip.jsx';
import FilterGroup from '../components/ui/FilterGroup.jsx';
import TableCard from '../components/ui/TableCard.jsx';
import Table, { THead, Th, TR, Td } from '../components/ui/Table.jsx';
import Button from '../components/ui/Button.jsx';
import ConfirmDialog from '../components/ui/ConfirmDialog.jsx';
import Modal from '../components/ui/Modal.jsx';
import ErrorState from '../components/ui/ErrorState.jsx';
import Field from '../components/ui/Field.jsx';
import Textarea from '../components/ui/Textarea.jsx';
import Tabs from '../components/ui/Tabs.jsx';
import MySchedulesTable from '../components/schedule/MySchedulesTable.jsx';
import Pagination from '../components/ui/Pagination.jsx';
import { paginate } from '../utils/paginate.js';
import { LEAVE_TYPE_LABEL } from '../constants/status.js';
import { useCancelLeave, useLeaveSummary, useMyLeaves } from '../hooks/useLeaves.js';

// 탭 — 연차·복리후생(결재를 거치고 잔액을 차감)과 개인 일정(둘 다 없음)은 성격이 달라
// 같은 표에 섞지 않는다. 상태·사유·취소 열이 개인 일정에는 아예 없다.
const TABS = [
  { value: 'LEAVE', label: '연차·복리후생' },
  { value: 'SCHEDULE', label: '개인 일정' },
];

// 취소할 수 있는 상태 — 대기 중이거나 승인된 건만 (반려·취소·취소대기는 대상 아님).
// 과거 날짜가 포함된 승인 건은 서버가 소급 취소(CANCEL_PENDING)로 돌려 승인자 승인을 받는다.
const CANCELLABLE_STATUSES = ['PENDING', 'APPROVED'];

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

// 현재 셀 폭에서 한글 한 줄을 안정적으로 유지하는 상한이다.
// 이 기준을 넘은 사유만 상세 동작을 제공해 짧은 문장을 가짜 버튼으로 만들지 않는다.
const REASON_PREVIEW_LENGTH = 20;

function ReasonCell({ reason, onOpen }) {
  const text = reason ?? '';
  const truncated = text.length > REASON_PREVIEW_LENGTH;

  if (!truncated) {
    return <span>{text || '-'}</span>;
  }

  return (
    <button
      type="button"
      aria-label={`사유 상세 보기: ${text}`}
      onClick={() => onOpen(text)}
      className="inline-block max-w-[220px] truncate align-bottom text-left text-accent-light underline decoration-accent/40 underline-offset-2 transition-colors hover:text-ink-hi"
    >
      {text.slice(0, REASON_PREVIEW_LENGTH)}…
    </button>
  );
}

const PAGE_SIZE = 10;

// 사용 내역 — 내 연차·복리후생 신청 이력 필터·조회 (docs/05 §④)
// WELFARE 항목은 복리후생 API가 아직 없어 이 목록엔 나타나지 않는다 (종류 필터의 '경조/복리'는 항상 빈 결과).
export default function HistoryPage() {
  const leavesQuery = useMyLeaves();
  const summaryQuery = useLeaveSummary();
  const cancelMutation = useCancelLeave();
  const { data: page, isLoading } = leavesQuery;
  const { data: summary } = summaryQuery;
  const myLeaveRequests = useMemo(() => page?.content ?? [], [page]);

  // 취소 확인 다이얼로그 — 클릭한 건 하나만 담는다
  const [cancelTarget, setCancelTarget] = useState(null);
  const [cancelReason, setCancelReason] = useState('');
  const [reasonDetail, setReasonDetail] = useState(null);

  function openCancel(request) {
    setCancelTarget(request);
    setCancelReason('');
  }

  function closeCancel() {
    setCancelTarget(null);
    setCancelReason('');
  }

  function handleCancel() {
    if (!cancelTarget) return;
    const reason = cancelReason.trim();
    if (reason === '') {
      toast.error('취소 사유를 입력해주세요.');
      return;
    }
    cancelMutation.mutate(
      { id: cancelTarget.id, reason },
      {
        // 서버가 결과 상태를 정한다 — 미래 날짜만이면 즉시 취소, 과거가 섞였으면 승인 대기.
        // 즉시 취소된 것처럼 안내하면 사용자가 연차가 복구됐다고 오해한다.
        onSuccess: (status) => {
          if (status === 'CANCEL_PENDING') {
            toast.success('지난 날짜가 포함되어 결재자 승인 후 취소됩니다.');
          } else {
            toast.success('연차 신청을 취소했습니다.');
          }
          closeCancel();
        },
      },
    );
  }

  // 데이터에 존재하는 연도만 필터 칩으로 노출 (최신 연도 우선)
  const yearOptions = useMemo(
    () => [...new Set(myLeaveRequests.map((req) => dayjs(req.dates[0]).year()))].sort((a, b) => b - a),
    [myLeaveRequests],
  );
  const [tab, setTab] = useState('LEAVE');
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

  // 화면에서 거른 뒤에 끊는다 — 서버가 먼저 끊으면 필터가 그 페이지 안에서만 걸린다 (paginate 주석).
  // 이름이 tablePage인 것은 위에서 `page`가 이미 서버 응답(Page)을 가리키고 있어서다.
  const [tablePage, setTablePage] = useState(0);
  const paged = paginate(filtered, tablePage, PAGE_SIZE);

  // 필터를 바꾸면 첫 페이지로 — 3페이지를 보다 조건을 좁히면 남은 건이 1페이지뿐이라 빈 표가 된다
  useEffect(() => {
    setTablePage(0);
  }, [activeYear, typeFilter, statusFilter]);

  // 연차 탭의 조회 상태 — 실패를 로딩과 구분한다.
  // !summary만 보면 조회 실패 시 스피너가 영원히 돈다 (리뷰 F-6).
  // 개인 일정 탭은 자체 쿼리를 쓰므로 여기서 막지 않는다 — 예전처럼 early return을 두면
  // 연차 조회가 실패했을 때 멀쩡한 일정 탭까지 못 보게 된다.
  const leaveFailed = leavesQuery.isError || summaryQuery.isError;
  const leaveLoading = isLoading || !summary;

  // 통계 스트립 값 (선차감 정책상 '사용'은 확정 + 대기 합산이라 캡션으로 구분)
  const grantedDays = summary ? Number(summary.baseDays) + Number(summary.bonusDays) : 0;
  const confirmedUsed = summary ? Number(summary.useDays) - Number(summary.pendingDays) : 0;

  return (
    <div>
      <PageHeader title="사용 내역" subtitle="내 연차·복리후생 신청 내역과 개인 일정" />

      <Tabs tabs={TABS} value={tab} onChange={setTab} className="mb-6" />

      {tab === 'SCHEDULE' && <MySchedulesTable />}

      {tab === 'LEAVE' && leaveFailed && (
        <ErrorState
          label="사용 내역을 불러오지 못했습니다."
          onRetry={() => {
            leavesQuery.refetch();
            summaryQuery.refetch();
          }}
        />
      )}

      {tab === 'LEAVE' && !leaveFailed && leaveLoading && (
        <div className="flex h-40 items-center justify-center gap-2 text-ink-mute">
          <Loader2 size={18} className="animate-spin" />
          <span className="text-[13px]">불러오는 중…</span>
        </div>
      )}

      {tab === 'LEAVE' && !leaveFailed && !leaveLoading && (
        <>
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
      <StatStrip className="mb-6 border-b border-white/[0.12] pb-6">
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
            <Th right>취소</Th>
          </THead>
          <tbody>
            {paged.rows.map((req) => (
              <TR key={req.id}>
                <Td>{dayjs(req.createdAt).format('YYYY.MM.DD')}</Td>
                <Td className="font-medium text-ink-hi">{LEAVE_TYPE_LABEL[req.leaveType]}</Td>
                <Td>{formatPeriod(req.dates)}</Td>
                <Td right className="font-semibold text-ink-hi">
                  {req.days}일
                </Td>
                <Td className="text-ink-mute">
                  <ReasonCell reason={req.requestReason} onOpen={setReasonDetail} />
                </Td>
                <Td>
                  <StatusBadge status={req.status} />
                </Td>
                <Td right>
                  {CANCELLABLE_STATUSES.includes(req.status) && (
                    <Button
                      variant="ghost"
                      size="sm"
                      Icon={X}
                      onClick={() => openCancel(req)}
                      disabled={cancelMutation.isPending}
                    >
                      취소
                    </Button>
                  )}
                </Td>
              </TR>
            ))}
          </tbody>
        </Table>
        <Pagination
          page={paged.page}
          totalPages={paged.totalPages}
          totalElements={paged.totalElements}
          onChange={setTablePage}
        />
      </TableCard>
        </>
      )}

      {reasonDetail && (
        <Modal title="신청 사유" onClose={() => setReasonDetail(null)} maxWidth={520}>
          <p className="whitespace-pre-wrap break-words text-[14px] leading-6 text-ink-body">
            {reasonDetail}
          </p>
        </Modal>
      )}

      {/* 취소 확인 — 사유는 필수다(서버 CancelRequest도 필수). 승인된 건은 지난 날짜가 섞였는지에 따라
          즉시 취소 / 결재자 승인 대기로 갈리므로 그 사실을 미리 알린다. */}
      <ConfirmDialog
        open={Boolean(cancelTarget)}
        title="연차 신청 취소"
        message={
          cancelTarget &&
          `${formatPeriod(cancelTarget.dates)} ${LEAVE_TYPE_LABEL[cancelTarget.leaveType]} ${cancelTarget.days}일 신청을 취소하시겠습니까?`
        }
        tone="danger"
        confirmLabel="취소 신청"
        onConfirm={handleCancel}
        onCancel={closeCancel}
        loading={cancelMutation.isPending}
      >
        <Field
          label="취소 사유"
          hint={
            cancelTarget?.status === 'APPROVED'
              ? '지난 날짜가 포함된 승인 건은 결재자 승인 후 취소됩니다.'
              : '결재 이력에 남습니다.'
          }
          className="mt-3"
        >
          <Textarea
            value={cancelReason}
            onChange={(e) => setCancelReason(e.target.value)}
            rows={2}
            placeholder="취소 사유를 입력하세요"
          />
        </Field>
      </ConfirmDialog>
    </div>
  );
}
