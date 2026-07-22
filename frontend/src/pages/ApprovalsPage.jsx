import { useState } from 'react';
import dayjs from 'dayjs';
import toast from 'react-hot-toast';
import { Check, X } from 'lucide-react';
import { processedApprovals } from '../mocks/data.js'; // TODO(backend): "내가 처리한 결재" 조회 API 없어 처리완료 탭은 아직 mock
import { LEAVE_TYPE_LABEL } from '../constants/status.js';
import { useCurrentUser } from '../hooks/useAuth.js';
import {
  useAllLeavesCount,
  usePendingApprovals,
  useProcessApproval,
  useProcessCancelApproval,
} from '../hooks/useLeaves.js';
import { usePendingWelfareApprovals, useProcessWelfareApproval } from '../hooks/useWelfare.js';
import PageHeader from '../components/ui/PageHeader.jsx';
import Card from '../components/ui/Card.jsx';
import StatStrip from '../components/ui/StatStrip.jsx';
import Stat from '../components/ui/Stat.jsx';
import Avatar from '../components/ui/Avatar.jsx';
import Button from '../components/ui/Button.jsx';
import StatusBadge from '../components/ui/StatusBadge.jsx';
import Tabs from '../components/ui/Tabs.jsx';
import TableCard from '../components/ui/TableCard.jsx';
import Table, { THead, Th, TR, Td } from '../components/ui/Table.jsx';
import ConfirmDialog from '../components/ui/ConfirmDialog.jsx';
import Field from '../components/ui/Field.jsx';
import Textarea from '../components/ui/Textarea.jsx';

// 탭 정의 (대기 / 승인 / 반려)
const TABS = [
  { key: 'PENDING', label: '대기' },
  { key: 'APPROVED', label: '승인' },
  { key: 'REJECTED', label: '반려' },
];

// 대기 목록 응답(LeaveResponse) → 테이블 행이 기대하는 모양으로 정규화.
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

// 복리후생 대기 목록 응답(WelfareResponse) → 테이블 행이 기대하는 모양으로 정규화.
// 연차와 달리 dates 배열이 없어(단발성 신청) periodText가 appliedAt으로 대체 표시한다.
function mapPendingWelfareItem(item) {
  return {
    id: item.id,
    kind: 'WELFARE',
    applicantName: item.userName,
    applicantDept: item.departmentName ?? '미배정',
    days: item.addDays,
    reason: item.reason,
    appliedAt: item.createdAt,
  };
}

// 신청 종류 라벨 (취소는 별도 표기, 복리는 공용 라벨, 그 외는 휴가 종류 매핑)
function typeText(item) {
  if (item.kind === 'CANCEL') return '취소';
  if (item.kind === 'WELFARE') return LEAVE_TYPE_LABEL.WELFARE;
  return LEAVE_TYPE_LABEL[item.type];
}

// 기간 문자열 (단일일이면 하나, 여러 날이면 시작~종료). 복리후생은 dates가 없는 단발성 신청이라
// 신청일(appliedAt)을 대신 보여준다.
function periodText(item) {
  if (item.kind === 'WELFARE') return dayjs(item.appliedAt).format('YYYY.MM.DD');
  const { dates } = item;
  const start = dayjs(dates[0]).format('YYYY.MM.DD');
  if (dates.length <= 1) return start;
  return `${start} ~ ${dayjs(dates[dates.length - 1]).format('MM.DD')}`;
}

// 결재 테이블 한 행 — mode 'pending'이면 승인/반려 버튼, 'processed'면 상태 배지+처리일.
// 승인/반려를 눌러도 바로 처리하지 않고 onOpenConfirm으로 부모에 알려, 페이지 전체에서
// ConfirmDialog가 하나만 뜨도록 한다(여러 건이 대기 중이어도 입력창은 하나).
function ApprovalRow({ item, mode, onOpenConfirm, actionDisabled }) {
  const isCancel = item.kind === 'CANCEL';

  return (
    <TR className={isCancel ? 'border-l-2 border-warn/60' : ''}>
      {/* 신청자 */}
      <Td>
        <div className="flex items-center gap-3">
          <Avatar name={item.applicantName} />
          <div className="min-w-0">
            <div className="truncate text-[14px] font-semibold text-ink-hi">{item.applicantName}</div>
            <div className="truncate text-[12px] text-ink-mute">{item.applicantDept}</div>
          </div>
        </div>
      </Td>

      {/* 종류 · 일수 */}
      <Td>
        <div className="flex items-center gap-1.5 text-[13px] font-semibold text-ink-body">
          {typeText(item)}
          {isCancel && (
            <span className="rounded-badge bg-warn/13 px-2 py-0.5 text-[10px] font-semibold text-warn">
              취소 요청
            </span>
          )}
        </div>
        <div className="mt-0.5 text-[12px] text-ink-mute">{item.days}일</div>
      </Td>

      {/* 기간 */}
      <Td className="text-ink-body">{periodText(item)}</Td>

      {/* 사유 */}
      <Td className="text-ink-mute">
        <span className="block max-w-[240px] truncate" title={item.reason}>
          {item.reason}
        </span>
      </Td>

      {/* 상태-액션 */}
      <Td right>
        {mode === 'pending' ? (
          <div className="flex items-center justify-end gap-2">
            <Button
              variant="danger"
              size="sm"
              Icon={X}
              onClick={() => onOpenConfirm(item, 'reject')}
              disabled={actionDisabled}
            >
              반려
            </Button>
            <Button
              variant="primary"
              size="sm"
              Icon={Check}
              onClick={() => onOpenConfirm(item, 'approve')}
              disabled={actionDisabled}
            >
              승인
            </Button>
          </div>
        ) : (
          <div className="flex flex-col items-end gap-1">
            <StatusBadge status={item.status} />
            <span className="text-[11px] text-ink-faint">{item.processedAt} 처리</span>
          </div>
        )}
      </Td>
    </TR>
  );
}

// 회사 전체 대기 현황 (SYSTEM_ADMIN 전용) — 팀장 개인의 "내 결재함"과 별개로, 관리자는 지금 회사
// 전체에 몇 건이 들어와 있는지 한눈에 봐야 해서 추가. 팀장용 화면은 이후 별도로 만들 예정이라
// 이 통계는 관리자 화면에만 노출한다.
function AdminOverview({ pendingCount, cancelPendingCount }) {
  return (
    <Card className="mb-5">
      <StatStrip>
        <Stat label="전체 신규 신청 대기" value={pendingCount} unit="건" />
        <Stat label="전체 취소 요청 대기" value={cancelPendingCount} unit="건" />
      </StatStrip>
    </Card>
  );
}

export default function ApprovalsPage() {
  const [activeTab, setActiveTab] = useState('PENDING');
  const { data: me } = useCurrentUser();
  const isAdmin = me?.role === 'SYSTEM_ADMIN';
  const pendingQuery = usePendingApprovals();
  const pendingWelfareQuery = usePendingWelfareApprovals();
  const approvalMutation = useProcessApproval();
  const cancelApprovalMutation = useProcessCancelApproval();
  const welfareApprovalMutation = useProcessWelfareApproval();
  // 회사 전체 대기 건수 — 관리자만 조회(그 외 역할이 부르면 403이라 enabled로 막아둠)
  const allPendingCountQuery = useAllLeavesCount('PENDING', isAdmin);
  const allCancelPendingCountQuery = useAllLeavesCount('CANCEL_PENDING', isAdmin);

  // 승인/반려 확인 다이얼로그 — 클릭한 건 하나만 담는다(여러 건이 대기 중이어도 다이얼로그는 1개).
  const [confirmTarget, setConfirmTarget] = useState(null); // { item, action: 'approve' | 'reject' } | null
  const [comment, setComment] = useState('');

  // 연차(신규·취소) 대기 + 복리후생 대기를 한 탭에 병합. id는 테이블이 서로 달라 겹칠 수 있어
  // 렌더링 key는 kind까지 포함해서 만든다.
  const pendingList = [
    ...(pendingQuery.data?.content ?? []).map(mapPendingItem),
    ...(pendingWelfareQuery.data?.content ?? []).map(mapPendingWelfareItem),
  ];
  const pendingLoading = pendingQuery.isLoading || pendingWelfareQuery.isLoading;
  const actionDisabled =
    approvalMutation.isPending || cancelApprovalMutation.isPending || welfareApprovalMutation.isPending;

  // 건 종류별로 처리 엔드포인트 분기: CANCEL_PENDING은 cancel-approval, 복리후생은 welfare 승인,
  // 그 외 일반 연차 대기 건은 approval 엔드포인트로.
  function mutationFor(kind) {
    if (kind === 'CANCEL') return cancelApprovalMutation;
    if (kind === 'WELFARE') return welfareApprovalMutation;
    return approvalMutation;
  }

  function openConfirm(item, action) {
    setConfirmTarget({ item, action });
    setComment('');
  }

  function closeConfirm() {
    setConfirmTarget(null);
    setComment('');
  }

  function handleConfirm() {
    if (!confirmTarget) return;
    const { item, action } = confirmTarget;
    const approved = action === 'approve';
    mutationFor(item.kind).mutate(
      { id: item.id, approved, comment },
      {
        onSuccess: () =>
          toast.success(`${item.applicantName}님의 신청을 ${approved ? '승인' : '반려'}했습니다.`),
      },
    );
    closeConfirm();
  }

  // 처리 완료(승인/반려) 탭 — "내가 처리한 결재" 목록을 주는 API가 아직 없어 mock 유지.
  // (SA는 GET /api/leaves?status=로 회사 전체 조회가 가능하지만, 이 화면이 원하는 "내가 처리한 것"과는
  //  범위가 다름 — TEAM_LEADER 기준 처리 이력 API가 필요, 다음 백엔드 라운드에서 결정)
  const list =
    activeTab === 'PENDING' ? pendingList : processedApprovals.filter((p) => p.status === activeTab);
  const mode = activeTab === 'PENDING' ? 'pending' : 'processed';
  const activeLabel = TABS.find((t) => t.key === activeTab)?.label ?? '';

  const tabItems = TABS.map((tab) => ({
    value: tab.key,
    label: tab.label,
    count: tab.key === 'PENDING' && pendingList.length > 0 ? pendingList.length : undefined,
  }));

  return (
    <div>
      <PageHeader title="결재 관리" subtitle="팀원 연차·복리후생 신청 결재" />

      {isAdmin && (
        <AdminOverview
          pendingCount={allPendingCountQuery.data ?? 0}
          cancelPendingCount={allCancelPendingCountQuery.data ?? 0}
        />
      )}

      <Tabs tabs={tabItems} value={activeTab} onChange={setActiveTab} className="mb-5" />

      {/* 결재 테이블 */}
      <TableCard
        loading={activeTab === 'PENDING' && pendingLoading}
        empty={list.length === 0}
        emptyLabel={`${activeLabel} 결재 건이 없습니다.`}
      >
        <Table className="min-w-[820px]">
          <THead>
            <Th>신청자</Th>
            <Th>종류·일수</Th>
            <Th>기간</Th>
            <Th>사유</Th>
            <Th right>상태</Th>
          </THead>
          <tbody>
            {list.map((item) => (
              <ApprovalRow
                key={`${item.kind}-${item.id}`}
                item={item}
                mode={mode}
                onOpenConfirm={openConfirm}
                actionDisabled={actionDisabled}
              />
            ))}
          </tbody>
        </Table>
      </TableCard>

      {/* 승인/반려 확인 다이얼로그 — 카드마다 상시 노출되던 결재의견 인풋 + 인라인 2단계 확인을 대체.
          승인/반려 클릭 시 이 다이얼로그 하나만 뜬다(여러 건 대기 중이어도 입력창은 하나). */}
      <ConfirmDialog
        open={Boolean(confirmTarget)}
        title={confirmTarget?.action === 'approve' ? '신청 승인' : '신청 반려'}
        message={
          confirmTarget &&
          `${confirmTarget.item.applicantName}님의 ${typeText(confirmTarget.item)} 신청을 ${
            confirmTarget.action === 'approve' ? '승인' : '반려'
          }하시겠습니까?`
        }
        tone={confirmTarget?.action === 'reject' ? 'danger' : 'default'}
        confirmLabel={confirmTarget?.action === 'approve' ? '승인' : '반려'}
        onConfirm={handleConfirm}
        onCancel={closeConfirm}
        loading={actionDisabled}
      >
        <Field label="결재 의견" hint="선택 입력 — 신청자에게 표시됩니다." className="mt-3">
          <Textarea
            value={comment}
            onChange={(e) => setComment(e.target.value)}
            rows={2}
            placeholder="결재 의견을 입력하세요 (선택)"
          />
        </Field>
      </ConfirmDialog>
    </div>
  );
}
