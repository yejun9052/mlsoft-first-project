import { useState } from 'react';
import dayjs from 'dayjs';
import toast from 'react-hot-toast';
import { Check, X, Inbox } from 'lucide-react';
import {
  pendingApprovals,
  processedApprovals,
  LEAVE_TYPE_LABEL,
} from '../mocks/data.js';
import PageHeader from '../components/ui/PageHeader.jsx';
import StatusBadge from '../components/ui/StatusBadge.jsx';

// 탭 정의 (대기 / 승인 / 반려)
const TABS = [
  { key: 'PENDING', label: '대기' },
  { key: 'APPROVED', label: '승인' },
  { key: 'REJECTED', label: '반려' },
];

// 신청 종류 라벨 (취소/복리는 별도 표기, 그 외는 휴가 종류 매핑)
function typeText(item) {
  if (item.kind === 'CANCEL') return '취소';
  if (item.kind === 'WELFARE') return '복리';
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

// 결재 카드 한 건 — mode 'pending'이면 승인/반려 버튼, 'processed'면 상태 배지+처리일
function ApprovalCard({ item, mode }) {
  const isCancel = item.kind === 'CANCEL';

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
          <div className="flex shrink-0 items-center gap-2">
            <button
              type="button"
              onClick={() => toast(`${item.applicantName}님의 신청을 반려했습니다. (데모)`, { icon: '↩️' })}
              className="flex items-center gap-1 rounded-btn border border-danger/50 px-3 py-2 text-[13px] font-semibold text-danger transition-colors hover:bg-danger/10"
            >
              <X size={15} />반려
            </button>
            <button
              type="button"
              onClick={() => toast.success(`${item.applicantName}님의 신청을 승인했습니다. (데모)`)}
              className="flex items-center gap-1 rounded-btn bg-accent px-3.5 py-2 text-[13px] font-semibold text-white shadow-btn transition-colors hover:bg-accent-dark"
            >
              <Check size={15} />승인
            </button>
          </div>
        ) : (
          <div className="flex shrink-0 flex-col items-end gap-1">
            <StatusBadge status={item.status} />
            <span className="text-[12px] text-ink-faint">{item.processedAt} 처리</span>
          </div>
        )}
      </div>

      {/* 결재 의견 (처리 완료 & comment 있을 때만) */}
      {mode === 'processed' && item.comment && (
        <div className="border-t border-white/6 px-4 py-2.5 text-[12px] text-ink-mute">
          결재 의견 · {item.comment}
        </div>
      )}
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

  // 활성 탭에 노출할 목록 계산
  const list =
    activeTab === 'PENDING'
      ? pendingApprovals
      : processedApprovals.filter((p) => p.status === activeTab);
  const mode = activeTab === 'PENDING' ? 'pending' : 'processed';
  const activeLabel = TABS.find((t) => t.key === activeTab)?.label ?? '';

  return (
    <div>
      <PageHeader title="결재 관리" subtitle="팀원 연차·복리후생 신청 결재" />

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
              {tab.key === 'PENDING' && pendingApprovals.length > 0 && (
                <span className="rounded-badge bg-accent/16 px-1.5 py-0.5 text-[11px] font-semibold text-accent-light">
                  {pendingApprovals.length}
                </span>
              )}
            </button>
          );
        })}
      </div>

      {/* 결재 카드 리스트 */}
      {list.length === 0 ? (
        <EmptyState label={activeLabel} />
      ) : (
        <div className="flex flex-col gap-3">
          {list.map((item) => (
            <ApprovalCard key={item.id} item={item} mode={mode} />
          ))}
        </div>
      )}
    </div>
  );
}
