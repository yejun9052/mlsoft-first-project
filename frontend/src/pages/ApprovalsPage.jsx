import { useState } from 'react';
import dayjs from 'dayjs';
import toast from 'react-hot-toast';
import { Check, X, Inbox, Loader2 } from 'lucide-react';
import { processedApprovals } from '../mocks/data.js'; // TODO(backend): "내가 처리한 결재" 조회 API 없어 처리완료 탭은 아직 mock
import { LEAVE_TYPE_LABEL } from '../constants/status.js';
import { useCurrentUser } from '../hooks/useAuth.js';
import {
  useAllLeavesCount,
  usePendingApprovals,
  useProcessApproval,
  useProcessCancelApproval,
} from '../hooks/useLeaves.js';
import PageHeader from '../components/ui/PageHeader.jsx';
import StatusBadge from '../components/ui/StatusBadge.jsx';

// 탭 정의 (대기 / 승인 / 반려)
const TABS = [
  { key: 'PENDING', label: '대기' },
  { key: 'APPROVED', label: '승인' },
  { key: 'REJECTED', label: '반려' },
];

// 대기 목록 응답(LeaveResponse) → 카드가 기대하는 모양으로 정규화.
// CANCEL_PENDING(소급취소 승인 대기)은 kind='CANCEL'로 표시하고, 사유는 취소 사유를 우선 보여준다.
function mapPendingItem(item) {
  return {
    id: item.id,
    kind: item.status === 'CANCEL_PENDING' ? 'CANCEL' : 'LEAVE',
    applicantName: item.userName,
    applicantDept: item.departmentName ?? '미배정',
    type: item.leaveType,
    dates: item.dates,
    days: item.days,
    reason: item.cancelReason || item.requestReason,
    appliedAt: item.createdAt,
  };
}

// 신청 종류 라벨 (취소는 별도 표기, 복리는 공용 라벨, 그 외는 휴가 종류 매핑)
function typeText(item) {
  if (item.kind === 'CANCEL') return '취소';
  if (item.kind === 'WELFARE') return LEAVE_TYPE_LABEL.WELFARE;
  return LEAVE_TYPE_LABEL[item.type];
}

// 기간 문자열 (단일일이면 하나, 여러 날이면 시작~종료)
function periodText(dates) {
  const start = dayjs(dates[0]).format('YYYY.MM.DD');
  if (dates.length <= 1) return start;
  return `${start} ~ ${dayjs(dates[dates.length - 1]).format('MM.DD')}`;
}

// 아바타 — 이름 첫 글자
function Avatar({ name }) {
  return (
    <span className="flex h-9 w-9 shrink-0 items-center justify-center rounded-full bg-navy-avatar text-[13px] font-semibold text-accent-light">
      {name.charAt(0)}
    </span>
  );
}

// 결재 카드 한 건 — mode 'pending'이면 승인/반려 버튼(+의견 입력), 'processed'면 상태 배지+처리일
function ApprovalCard({ item, mode, onApprove, onReject, actionDisabled }) {
  const [comment, setComment] = useState('');
  // 승인/반려 오클릭 방지 — 버튼을 누르면 바로 처리하지 않고 확인 문구로 한 번 더 확인받는다.
  const [confirming, setConfirming] = useState(null); // null | 'approve' | 'reject'
  const isCancel = item.kind === 'CANCEL';

  function confirmAction() {
    if (confirming === 'approve') onApprove(item, comment);
    else onReject(item, comment);
    setConfirming(null);
  }

  return (
    <div
      className={`flex flex-col rounded-card bg-navy-card shadow-card ${
        isCancel ? 'border-l-2 border-warn/60' : ''
      }`}
    >
      <div className="flex flex-wrap items-center gap-4 p-4">
        {/* 신청자 */}
        <div className="flex w-[180px] shrink-0 items-center gap-3">
          <Avatar name={item.applicantName} />
          <div className="min-w-0">
            <div className="truncate text-[14px] font-semibold text-ink-hi">{item.applicantName}</div>
            <div className="truncate text-[12px] text-ink-mute">{item.applicantDept}</div>
          </div>
        </div>

        {/* 종류 · 일수 */}
        <div className="w-[130px] shrink-0">
          <div className="flex items-center gap-1.5 text-[13px] font-semibold text-ink-body">
            {typeText(item)}
            {isCancel && (
              <span className="rounded-badge bg-warn/13 px-2 py-0.5 text-[10px] font-semibold text-warn">
                취소 요청
              </span>
            )}
          </div>
          <div className="mt-0.5 text-[12px] text-ink-mute">{item.days}일</div>
        </div>

        {/* 기간 */}
        <div className="w-[170px] shrink-0 text-[13px] text-ink-body">{periodText(item.dates)}</div>

        {/* 사유 */}
        <div className="min-w-0 flex-1 truncate text-[13px] text-ink-mute" title={item.reason}>
          {item.reason}
        </div>

        {/* 우측: 버튼(대기) 또는 상태(처리 완료) */}
        {mode === 'pending' ? (
          confirming ? (
            <div className="flex shrink-0 items-center gap-2">
              <span className="text-[13px] text-ink-mute">
                정말 {confirming === 'approve' ? '승인' : '반려'}하시겠습니까?
              </span>
              <button
                type="button"
                onClick={() => setConfirming(null)}
                className="rounded-btn px-2.5 py-1.5 text-[12px] font-semibold text-ink-mute transition-colors hover:bg-white/6 hover:text-ink-body"
              >
                아니오
              </button>
              <button
                type="button"
                onClick={confirmAction}
                disabled={actionDisabled}
                className={
                  confirming === 'approve'
                    ? 'rounded-btn bg-accent px-3 py-1.5 text-[12px] font-semibold text-white shadow-btn transition-colors hover:bg-accent-dark disabled:cursor-not-allowed disabled:opacity-50'
                    : 'rounded-btn border border-danger/50 px-3 py-1.5 text-[12px] font-semibold text-danger transition-colors hover:bg-danger/10 disabled:cursor-not-allowed disabled:opacity-50'
                }
              >
                예, {confirming === 'approve' ? '승인' : '반려'}
              </button>
            </div>
          ) : (
            <div className="flex shrink-0 items-center gap-2">
              <button
                type="button"
                onClick={() => setConfirming('reject')}
                disabled={actionDisabled}
                className="flex items-center gap-1 rounded-btn border border-danger/50 px-3 py-2 text-[13px] font-semibold text-danger transition-colors hover:bg-danger/10 disabled:cursor-not-allowed disabled:opacity-50"
              >
                <X size={15} />반려
              </button>
              <button
                type="button"
                onClick={() => setConfirming('approve')}
                disabled={actionDisabled}
                className="flex items-center gap-1 rounded-btn bg-accent px-3.5 py-2 text-[13px] font-semibold text-white shadow-btn transition-colors hover:bg-accent-dark disabled:cursor-not-allowed disabled:opacity-50"
              >
                <Check size={15} />승인
              </button>
            </div>
          )
        ) : (
          <div className="flex shrink-0 flex-col items-end gap-1">
            <StatusBadge status={item.status} />
            <span className="text-[12px] text-ink-faint">{item.processedAt} 처리</span>
          </div>
        )}
      </div>

      {/* 결재 의견 — 대기 탭은 입력, 처리완료 탭은 기록된 의견 표시 */}
      {mode === 'pending' && (
        <div className="border-t border-white/6 px-4 py-2.5">
          <input
            type="text"
            value={comment}
            onChange={(event) => setComment(event.target.value)}
            placeholder="결재 의견 (선택)"
            className="w-full rounded-btn border border-white/8 bg-navy-btn2 px-3 py-1.5 text-[12px] text-ink-hi placeholder:text-ink-dim focus:border-accent/50 focus:outline-none"
          />
        </div>
      )}
      {mode === 'processed' && item.comment && (
        <div className="border-t border-white/6 px-4 py-2.5 text-[12px] text-ink-mute">
          결재 의견 · {item.comment}
        </div>
      )}
    </div>
  );
}

// 회사 전체 대기 현황 (SYSTEM_ADMIN 전용) — 팀장 개인의 "내 결재함"과 별개로, 관리자는 지금 회사
// 전체에 몇 건이 들어와 있는지 한눈에 봐야 해서 추가. 팀장용 화면은 이후 별도로 만들 예정이라
// 이 통계는 관리자 화면에만 노출한다.
function AdminOverview({ pendingCount, cancelPendingCount }) {
  return (
    <div className="mb-5 flex items-end gap-8 rounded-card bg-navy-card px-5 py-4 shadow-card">
      <AdminStat label="전체 신규 신청 대기" value={pendingCount} />
      <AdminStat label="전체 취소 요청 대기" value={cancelPendingCount} />
    </div>
  );
}

function AdminStat({ label, value }) {
  return (
    <div className="flex flex-col gap-1">
      <span className="text-[12px] font-medium text-ink-mute">{label}</span>
      <span className="text-[28px] font-bold leading-none text-ink-hi">
        {value}
        <span className="ml-1 text-[13px] font-medium text-ink-mute">건</span>
      </span>
    </div>
  );
}

// 비어 있는 탭 안내
function EmptyState({ label }) {
  return (
    <div className="flex flex-col items-center gap-2 rounded-card bg-navy-card py-16 text-ink-faint shadow-card">
      <Inbox size={28} />
      <span className="text-[13px]">{label} 결재 건이 없습니다.</span>
    </div>
  );
}

export default function ApprovalsPage() {
  const [activeTab, setActiveTab] = useState('PENDING');
  const { data: me } = useCurrentUser();
  const isAdmin = me?.role === 'SYSTEM_ADMIN';
  const pendingQuery = usePendingApprovals();
  const approvalMutation = useProcessApproval();
  const cancelApprovalMutation = useProcessCancelApproval();
  // 회사 전체 대기 건수 — 관리자만 조회(그 외 역할이 부르면 403이라 enabled로 막아둠)
  const allPendingCountQuery = useAllLeavesCount('PENDING', isAdmin);
  const allCancelPendingCountQuery = useAllLeavesCount('CANCEL_PENDING', isAdmin);

  const pendingList = (pendingQuery.data?.content ?? []).map(mapPendingItem);
  const actionDisabled = approvalMutation.isPending || cancelApprovalMutation.isPending;

  // CANCEL_PENDING 건은 cancel-approval 엔드포인트로, 그 외 일반 대기 건은 approval 엔드포인트로 분기
  function handleApprove(item, comment) {
    const mutation = item.kind === 'CANCEL' ? cancelApprovalMutation : approvalMutation;
    mutation.mutate(
      { id: item.id, approved: true, comment },
      { onSuccess: () => toast.success(`${item.applicantName}님의 신청을 승인했습니다.`) },
    );
  }
  function handleReject(item, comment) {
    const mutation = item.kind === 'CANCEL' ? cancelApprovalMutation : approvalMutation;
    mutation.mutate(
      { id: item.id, approved: false, comment },
      { onSuccess: () => toast.success(`${item.applicantName}님의 신청을 반려했습니다.`) },
    );
  }

  // 처리 완료(승인/반려) 탭 — "내가 처리한 결재" 목록을 주는 API가 아직 없어 mock 유지.
  // (SA는 GET /api/leaves?status=로 회사 전체 조회가 가능하지만, 이 화면이 원하는 "내가 처리한 것"과는
  //  범위가 다름 — TEAM_LEADER 기준 처리 이력 API가 필요, 다음 백엔드 라운드에서 결정)
  const list =
    activeTab === 'PENDING' ? pendingList : processedApprovals.filter((p) => p.status === activeTab);
  const mode = activeTab === 'PENDING' ? 'pending' : 'processed';
  const activeLabel = TABS.find((t) => t.key === activeTab)?.label ?? '';

  return (
    <div>
      <PageHeader title="결재 관리" subtitle="팀원 연차·복리후생 신청 결재" />

      {isAdmin && (
        <AdminOverview
          pendingCount={allPendingCountQuery.data ?? 0}
          cancelPendingCount={allCancelPendingCountQuery.data ?? 0}
        />
      )}

      {/* 탭 */}
      <div className="mb-5 flex items-center gap-1 border-b border-white/6">
        {TABS.map((tab) => {
          const active = tab.key === activeTab;
          return (
            <button
              key={tab.key}
              type="button"
              onClick={() => setActiveTab(tab.key)}
              className={`-mb-px flex items-center gap-2 border-b-2 px-4 py-2.5 text-[13px] font-semibold transition-colors ${
                active
                  ? 'border-accent text-accent-light'
                  : 'border-transparent text-ink-mute hover:text-ink-body'
              }`}
            >
              {tab.label}
              {tab.key === 'PENDING' && pendingList.length > 0 && (
                <span className="rounded-badge bg-accent/16 px-1.5 py-0.5 text-[11px] font-semibold text-accent-light">
                  {pendingList.length}
                </span>
              )}
            </button>
          );
        })}
      </div>

      {/* 결재 카드 리스트 */}
      {activeTab === 'PENDING' && pendingQuery.isLoading ? (
        <div className="flex items-center justify-center gap-2 rounded-card bg-navy-card py-16 text-ink-mute shadow-card">
          <Loader2 size={18} className="animate-spin" />
          <span className="text-[13px]">불러오는 중…</span>
        </div>
      ) : list.length === 0 ? (
        <EmptyState label={activeLabel} />
      ) : (
        <div className="flex flex-col gap-3">
          {list.map((item) => (
            <ApprovalCard
              key={item.id}
              item={item}
              mode={mode}
              onApprove={handleApprove}
              onReject={handleReject}
              actionDisabled={actionDisabled}
            />
          ))}
        </div>
      )}
    </div>
  );
}
