import { useState } from 'react';
import PageHeader from '../components/ui/PageHeader.jsx';
import Tabs from '../components/ui/Tabs.jsx';
import FilterGroup from '../components/ui/FilterGroup.jsx';
import Chip from '../components/ui/Chip.jsx';
import TableCard from '../components/ui/TableCard.jsx';
import Table, { THead, Th, TR, Td } from '../components/ui/Table.jsx';
import StatusBadge from '../components/ui/StatusBadge.jsx';
import Pagination from '../components/ui/Pagination.jsx';
import { ACTION_LABEL, ACTION_TONE, LEAVE_TYPE_LABEL } from '../constants/status.js';
import { ROLE } from '../constants/roles.js';
import { useCurrentUser } from '../hooks/useAuth.js';
import { useLeaveHistories, useWelfareHistories } from '../hooks/useHistories.js';

// 탭 (연차 / 복리후생) — 백엔드 로그 테이블이 둘로 나뉘어 있어 화면도 나눈다
const TAB_LEAVE = 'leave';
const TAB_WELFARE = 'welfare';

// 액션 필터 칩 — 전체 + RequestAction 7종
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

// 한 페이지에 담을 로그 건수 — 페이지를 넘길 때마다 이만큼만 서버에서 가져온다
const PAGE_SIZE = 20;

// 처리 이력 — 연차·복리후생 결재 로그 (docs/03 처리 이력).
// 총관리자는 전사, 팀장은 자기 부서 로그만 본다 — 스코프는 서버가 요청자 부서로 결정하므로
// 화면은 어느 엔드포인트를 부를지만 고른다.
export default function AdminHistoryPage() {
  const { data: me } = useCurrentUser();
  const scope = me?.role === ROLE.SYSTEM_ADMIN ? 'all' : 'my-team';

  const [tab, setTab] = useState(TAB_LEAVE);
  const [action, setAction] = useState('ALL');
  const [page, setPage] = useState(0);

  // 탭·필터를 바꾸면 현재 페이지가 범위를 벗어날 수 있으므로 항상 첫 페이지로 되돌린다
  function changeTab(next) {
    setTab(next);
    setPage(0);
  }
  function changeAction(next) {
    setAction(next);
    setPage(0);
  }

  const params = {
    scope,
    action: action === 'ALL' ? undefined : action,
    page,
    size: PAGE_SIZE,
  };
  // 보이는 탭만 조회한다 — 로그는 건수가 많아 두 종류를 항상 함께 불러올 이유가 없다
  // (탭 배지도 두지 않았다).
  const leaveQuery = useLeaveHistories({ ...params, enabled: tab === TAB_LEAVE });
  const welfareQuery = useWelfareHistories({ ...params, enabled: tab === TAB_WELFARE });

  const query = tab === TAB_LEAVE ? leaveQuery : welfareQuery;
  const rows = query.data?.content ?? [];
  const pageInfo = query.data?.page;

  const tabItems = [
    { value: TAB_LEAVE, label: '연차' },
    { value: TAB_WELFARE, label: '복리후생' },
  ];

  return (
    <div>
      <PageHeader
        title="처리 이력"
        subtitle={
          scope === 'all'
            ? '전사 연차·복리후생 결재 처리 로그'
            : '내 부서 연차·복리후생 결재 처리 로그'
        }
      />

      <Tabs tabs={tabItems} value={tab} onChange={changeTab} className="mb-5" />

      <div className="mb-4 flex flex-wrap items-center justify-end gap-3">
        <FilterGroup label="액션">
          {ACTION_FILTERS.map((value) => (
            <Chip key={value} active={action === value} onClick={() => changeAction(value)}>
              {value === 'ALL' ? '전체' : ACTION_LABEL[value]}
            </Chip>
          ))}
        </FilterGroup>
      </div>

      <TableCard
        loading={query.isLoading}
        error={query.isError}
        errorLabel="처리 이력을 불러오지 못했습니다."
        onRetry={query.refetch}
        empty={!query.isLoading && rows.length === 0}
        emptyLabel="처리 이력이 없습니다."
      >
        <Table className="min-w-[880px]">
          <THead>
            <Th>처리 일시</Th>
            <Th>액션</Th>
            <Th>신청자</Th>
            <Th>부서</Th>
            <Th>{tab === TAB_LEAVE ? '연차 종류' : '구분'}</Th>
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
                  {tab === TAB_LEAVE ? (LEAVE_TYPE_LABEL[h.leaveType] ?? h.leaveType) : h.category}
                </Td>
                <Td right className="text-ink-body tabular-nums">
                  {Number(tab === TAB_LEAVE ? h.days : h.addDays)}일
                </Td>
                <Td className="text-ink-body">{h.actorName}</Td>
                <Td className="max-w-[240px] truncate text-ink-mute" title={h.comment}>
                  {h.comment || '—'}
                </Td>
              </TR>
            ))}
          </tbody>
        </Table>
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

// 로그 타임스탬프 — 'YYYY-MM-DD HH:mm' (서버는 LocalDateTime을 ISO 문자열로 내려준다).
// 초 단위는 로그를 훑는 데 필요 없어 잘라낸다.
function formatDateTime(value) {
  if (!value) return '—';
  return String(value).replace('T', ' ').slice(0, 16);
}
