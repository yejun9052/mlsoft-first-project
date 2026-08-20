import { useState } from 'react';
import PageHeader from '../components/ui/PageHeader.jsx';
import Tabs from '../components/ui/Tabs.jsx';
import FilterGroup from '../components/ui/FilterGroup.jsx';
import Chip from '../components/ui/Chip.jsx';
import Card from '../components/ui/Card.jsx';
import TableCard from '../components/ui/TableCard.jsx';
import Table, { THead, Th, TR, Td } from '../components/ui/Table.jsx';
import ResponsiveTable from '../components/ui/ResponsiveTable.jsx';
import StatusBadge from '../components/ui/StatusBadge.jsx';
import Pagination from '../components/ui/Pagination.jsx';
import { usePageClamp } from '../hooks/usePageClamp.js';
import { ACTION_LABEL, ACTION_TONE, ADMIN_ACTION_TONE, LEAVE_TYPE_LABEL } from '../constants/status.js';
import { ROLE } from '../constants/roles.js';
import { useCurrentUser } from '../hooks/useAuth.js';
import { useLeaveHistories, useWelfareHistories } from '../hooks/useHistories.js';
import { useAuditLogs, useAuditActions } from '../hooks/useAudit.js';

// 탭 (연차 / 복리후생 / 관리자 조작) — 백엔드 로그 테이블이 셋으로 나뉘어 있어 화면도 나눈다.
// 관리자 조작(감사 로그)은 결재 이력과 성격이 다르다 — 신청·승인이 아니라 관리자가 사원의
// 권한·잔액을 직접 바꾼 기록이고, 그래서 SYSTEM_ADMIN에게만 보인다 (리뷰 S-3).
const TAB_LEAVE = 'leave';
const TAB_WELFARE = 'welfare';
const TAB_AUDIT = 'audit';

// 액션 필터 칩 — 전체 + RequestAction 7종 (연차·복리후생 탭)
const ACTION_FILTERS = [
  'ALL',
  'PENDING',
  'APPROVED',
  'REJECTED',
  'CANCELLED',
  'CANCEL_PENDING',
  'CANCEL_APPROVED',
  'CANCEL_REJECTED',
];

// 한 페이지에 담을 로그 건수 — 페이지를 넘길 때마다 이만큼만 서버에서 가져온다.
// 20이었을 때 표가 화면보다 길어 페이지 넘김 버튼까지 스크롤해야 했다 (2026-08-17 지적).
// 다른 목록 화면과 같은 값을 쓴다 — 화면마다 다르면 페이지를 옮길 때 표 높이가 널뛴다.
const PAGE_SIZE = 10;

// 처리 이력 — 연차·복리후생 결재 로그 + 관리자 조작 감사 로그 (docs/03 처리 이력, 리뷰 S-3).
// 총관리자는 전사, 팀장은 자기 부서 로그만 본다 — 스코프는 서버가 요청자 부서로 결정하므로
// 화면은 어느 엔드포인트를 부를지만 고른다.
export default function AdminHistoryPage() {
  const { data: me } = useCurrentUser();
  const isAdmin = me?.role === ROLE.SYSTEM_ADMIN;
  // 팀장은 "내가 결재자인 건" 스코프 — 소속 부서 기준이 아니다 (리뷰 S-6)
  const scope = isAdmin ? 'all' : 'my-approvals';

  const [tab, setTab] = useState(TAB_LEAVE);
  const [action, setAction] = useState('ALL');
  const [page, setPage] = useState(0);

  // 탭·필터를 바꾸면 현재 페이지가 범위를 벗어날 수 있으므로 항상 첫 페이지로 되돌린다.
  // 액션 필터도 초기화한다 — 탭마다 액션 종류가 다르므로(RequestAction ↔ AdminAction)
  // 그대로 들고 가면 서버에 없는 값을 보내 400이 된다.
  function changeTab(next) {
    setTab(next);
    setAction('ALL');
    setPage(0);
  }
  function changeAction(next) {
    setAction(next);
    setPage(0);
  }

  const isAuditTab = tab === TAB_AUDIT;
  const params = {
    scope,
    action: action === 'ALL' ? undefined : action,
    page,
    size: PAGE_SIZE,
  };
  // 보이는 탭만 조회한다 — 로그는 건수가 많아 여러 종류를 항상 함께 불러올 이유가 없다
  // (탭 배지도 두지 않았다).
  const leaveQuery = useLeaveHistories({ ...params, enabled: tab === TAB_LEAVE });
  const welfareQuery = useWelfareHistories({ ...params, enabled: tab === TAB_WELFARE });
  // 감사 로그에는 부서 스코프가 없다 — 관리자 전용이라 항상 전사다. scope를 넘기지 않는다.
  const auditQuery = useAuditLogs({
    action: params.action,
    page,
    size: PAGE_SIZE,
    enabled: isAdmin && isAuditTab,
  });
  // 필터 칩 목록도 서버 카탈로그에서 받는다 — 액션 이름을 프론트에 하드코딩하지 않는다
  const auditActionsQuery = useAuditActions({ enabled: isAdmin && isAuditTab });

  const query = isAuditTab ? auditQuery : tab === TAB_LEAVE ? leaveQuery : welfareQuery;
  const rows = query.data?.content ?? [];
  const pageInfo = query.data?.page;
  // 로그는 줄어들지 않지만, 탭·필터를 바꾸면 총 페이지가 달라진다 (그때 setPage(0)도 함께 돈다)
  usePageClamp(page, setPage, pageInfo?.totalPages);

  const tabItems = [
    { value: TAB_LEAVE, label: '연차' },
    { value: TAB_WELFARE, label: '복리후생' },
    // 감사 로그는 전 직원의 권한·연차 변경 내역이라 관리자에게만 노출한다.
    // 서버도 @PreAuthorize로 막지만, 눌러서 403을 보게 두지 않는다.
    ...(isAdmin ? [{ value: TAB_AUDIT, label: '관리자 조작' }] : []),
  ];

  // 감사 로그 필터는 서버가 준 목록, 결재 로그는 RequestAction 고정 목록
  const auditFilters = [
    { name: 'ALL', label: '전체' },
    ...(auditActionsQuery.data ?? []),
  ];

  return (
    <div>
      <PageHeader
        title="처리 이력"
        subtitle={
          isAuditTab
            ? '관리자가 사원의 권한·연차·부서를 직접 바꾼 기록'
            : scope === 'all'
              ? '전사 연차·복리후생 결재 처리 로그'
              // 부서가 아니라 승인자 지정 기준이다 (리뷰 S-6) — 부서로 안내하면
              // 다른 부서 신청이 보일 때 사용자가 데이터 노출 오류로 오해한다
              : '내가 결재자로 지정된 연차·복리후생 처리 로그'
        }
      />

      <Tabs tabs={tabItems} value={tab} onChange={changeTab} className="mb-5" />

      <div className="mb-4 flex flex-wrap items-center justify-end gap-3">
        <FilterGroup label="액션">
          {isAuditTab
            ? auditFilters.map((item) => (
                <Chip
                  key={item.name}
                  active={action === item.name}
                  onClick={() => changeAction(item.name)}
                >
                  {item.label}
                </Chip>
              ))
            : ACTION_FILTERS.map((value) => (
                <Chip key={value} active={action === value} onClick={() => changeAction(value)}>
                  {value === 'ALL' ? '전체' : ACTION_LABEL[value]}
                </Chip>
              ))}
        </FilterGroup>
      </div>

      <TableCard
        loading={query.isLoading}
        error={query.isError}
        errorLabel={
          isAuditTab ? '관리자 조작 이력을 불러오지 못했습니다.' : '처리 이력을 불러오지 못했습니다.'
        }
        onRetry={query.refetch}
        empty={!query.isLoading && rows.length === 0}
        emptyLabel={isAuditTab ? '관리자 조작 이력이 없습니다.' : '처리 이력이 없습니다.'}
      >
        {isAuditTab ? (
          <AuditTable rows={rows} />
        ) : (
          <ApprovalTable rows={rows} isLeaveTab={tab === TAB_LEAVE} />
        )}
        <Pagination
          page={page}
          totalPages={pageInfo?.totalPages}
          totalElements={pageInfo?.totalElements}
          onChange={setPage}
        />
      </TableCard>
    </div>
  );
}

// 연차·복리후생 결재 처리 로그
function ApprovalTable({ rows, isLeaveTab }) {
  const table = (
    <Table className="min-w-[880px]">
      <THead>
        <Th>처리 일시</Th>
        <Th>액션</Th>
        <Th>신청자</Th>
        <Th>부서</Th>
        <Th>{isLeaveTab ? '연차 종류' : '구분'}</Th>
        <Th right>일수</Th>
        <Th>처리자</Th>
        <Th>코멘트</Th>
      </THead>
      <tbody>
        {rows.map((h) => (
          <TR key={h.id}>
            <Td className="text-ink-mute tabular-nums">{formatDateTime(h.createdAt)}</Td>
            <Td>
              <StatusBadge label={ACTION_LABEL[h.action] ?? h.action} tone={ACTION_TONE[h.action]} />
            </Td>
            <Td className="font-medium text-ink-hi">{h.userName}</Td>
            <Td className="text-ink-body">{h.departmentName ?? '미배정'}</Td>
            <Td className="text-ink-body">
              {isLeaveTab ? (LEAVE_TYPE_LABEL[h.leaveType] ?? h.leaveType) : h.category}
            </Td>
            <Td right className="text-ink-body tabular-nums">
              {Number(isLeaveTab ? h.days : h.addDays)}일
            </Td>
            <Td className="text-ink-body">{h.actorName}</Td>
            <Td className="max-w-[240px] truncate text-ink-mute" title={h.comment}>
              {h.comment || '—'}
            </Td>
          </TR>
        ))}
      </tbody>
    </Table>
  );

  const cards = rows.map((history) => (
    <Card key={history.id} padding="tight" as="article">
      <div className="flex items-start justify-between gap-3">
        <div className="min-w-0">
          <p className="font-semibold text-ink-hi">{history.userName}</p>
          <p className="mt-1 text-[11px] text-ink-faint tabular-nums">
            {formatDateTime(history.createdAt)}
          </p>
        </div>
        <StatusBadge
          label={ACTION_LABEL[history.action] ?? history.action}
          tone={ACTION_TONE[history.action]}
        />
      </div>

      <dl className="mt-4 grid grid-cols-[88px_1fr] gap-x-3 gap-y-2 text-[13px]">
        <dt className="text-ink-faint">부서</dt>
        <dd className="min-w-0 text-ink-body">{history.departmentName ?? '미배정'}</dd>
        <dt className="text-ink-faint">{isLeaveTab ? '연차 종류' : '구분'}</dt>
        <dd className="min-w-0 text-ink-body">
          {isLeaveTab ? (LEAVE_TYPE_LABEL[history.leaveType] ?? history.leaveType) : history.category}
        </dd>
        <dt className="text-ink-faint">일수</dt>
        <dd className="text-ink-body tabular-nums">
          {Number(isLeaveTab ? history.days : history.addDays)}일
        </dd>
        <dt className="text-ink-faint">처리자</dt>
        <dd className="min-w-0 text-ink-body">{history.actorName}</dd>
        <dt className="text-ink-faint">코멘트</dt>
        <dd className="min-w-0 break-words text-ink-mute">{history.comment || '—'}</dd>
      </dl>
    </Card>
  ));

  return <ResponsiveTable table={table} cards={cards} />;
}

// 관리자 조작 감사 로그 (리뷰 S-3).
// 이 표의 핵심은 "무엇에서 무엇으로" 열이다 — 조작이 있었다는 사실만으로는 대조가 안 된다.
// 액션 라벨은 서버가 응답에 담아 주므로(actionLabel) 프론트가 매핑하지 않는다.
function AuditTable({ rows }) {
  const table = (
    <Table className="min-w-[880px]">
      <THead>
        <Th>일시</Th>
        <Th>조작</Th>
        <Th>관리자</Th>
        <Th>대상</Th>
        <Th>변경 전</Th>
        <Th>변경 후</Th>
      </THead>
      <tbody>
        {rows.map((log) => (
          <TR key={log.id}>
            <Td className="text-ink-mute tabular-nums">{formatDateTime(log.createdAt)}</Td>
            <Td>
              <StatusBadge label={log.actionLabel ?? log.action} tone={ADMIN_ACTION_TONE[log.action]} />
            </Td>
            <Td className="text-ink-body">{log.actorName}</Td>
            {/* 대상 사원이 없는 조작(시스템 설정 변경)은 설정 키가 대상으로 들어온다 */}
            <Td className="font-medium text-ink-hi">{log.targetLabel}</Td>
            <Td className="text-ink-mute">{log.beforeValue || '—'}</Td>
            <Td className="font-medium text-ink-body">{log.afterValue || '—'}</Td>
          </TR>
        ))}
      </tbody>
    </Table>
  );

  const cards = rows.map((log) => (
    <Card key={log.id} padding="tight" as="article">
      <div className="flex items-start justify-between gap-3">
        <div className="min-w-0">
          <p className="font-semibold text-ink-hi">{log.targetLabel}</p>
          <p className="mt-1 text-[11px] text-ink-faint tabular-nums">
            {formatDateTime(log.createdAt)}
          </p>
        </div>
        <StatusBadge
          label={log.actionLabel ?? log.action}
          tone={ADMIN_ACTION_TONE[log.action]}
        />
      </div>

      <dl className="mt-4 grid grid-cols-[72px_1fr] gap-x-3 gap-y-2 text-[13px]">
        <dt className="text-ink-faint">관리자</dt>
        <dd className="min-w-0 text-ink-body">{log.actorName}</dd>
        <dt className="text-ink-faint">변경 전</dt>
        <dd className="min-w-0 break-words text-ink-mute">{log.beforeValue || '—'}</dd>
        <dt className="text-ink-faint">변경 후</dt>
        <dd className="min-w-0 break-words font-medium text-ink-body">{log.afterValue || '—'}</dd>
      </dl>
    </Card>
  ));

  return <ResponsiveTable table={table} cards={cards} />;
}

// 로그 타임스탬프 — 'YYYY-MM-DD HH:mm' (서버는 LocalDateTime을 ISO 문자열로 내려준다).
// 초 단위는 로그를 훑는 데 필요 없어 잘라낸다.
function formatDateTime(value) {
  if (!value) return '—';
  return String(value).replace('T', ' ').slice(0, 16);
}
