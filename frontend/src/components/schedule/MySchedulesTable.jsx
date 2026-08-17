import { useState } from 'react';
import dayjs from 'dayjs';
import toast from 'react-hot-toast';
import { Trash2 } from 'lucide-react';
import TableCard from '../ui/TableCard.jsx';
import Table, { THead, Th, TR, Td } from '../ui/Table.jsx';
import Button from '../ui/Button.jsx';
import ConfirmDialog from '../ui/ConfirmDialog.jsx';
import Pagination from '../ui/Pagination.jsx';
import { SCHEDULE_TYPE_LABEL } from '../../constants/status.js';
import { useDeleteSchedule, useMySchedules } from '../../hooks/useSchedules.js';
import { usePageClamp } from '../../hooks/usePageClamp.js';

const PAGE_SIZE = 20;

// 기간 표기 — 단일일이면 'M.D', 여러 날이면 'M.D ~ M.D' (사용 내역 연차 표와 동일 규칙)
function formatPeriod(dates) {
  const start = dayjs(dates[0]).format('M.D');
  if (dates.length === 1) return start;
  return `${start} ~ ${dayjs(dates[dates.length - 1]).format('M.D')}`;
}

/**
 * 내 개인 일정 목록 — 외근·출장·재택·교육을 확인하고 지운다.
 *
 * 연차 표와 달리 **상태 열이 없다.** 결재를 거치지 않아 등록되면 곧 확정이고, 취소가 아니라
 * 삭제다(잔액 복구 같은 후처리가 없어 되돌릴 게 없다). 그래서 사유 입력도 받지 않는다 —
 * 연차 취소는 결재 이력에 남지만 이건 그냥 사라진다.
 */
export default function MySchedulesTable() {
  const [page, setPage] = useState(0);
  const query = useMySchedules({ page, size: PAGE_SIZE });
  const deleteMutation = useDeleteSchedule();

  const [deleteTarget, setDeleteTarget] = useState(null);
  const schedules = query.data?.content ?? [];
  const totalPages = query.data?.page?.totalPages ?? 0;
  const totalElements = query.data?.page?.totalElements;

  // 어느 경로로 목록이 줄어들든 걸리는 그물. 아래 onSuccess의 선반영은 깜박임을 없애는
  // 빠른 길이고, 이건 그게 못 잡는 경우(다른 탭에서 지워 캐시가 갱신되는 등)를 받는다.
  usePageClamp(page, setPage, query.data?.page?.totalPages);

  function handleDelete() {
    if (!deleteTarget) return;
    deleteMutation.mutate(deleteTarget.id, {
      onSuccess: () => {
        toast.success('일정을 삭제했습니다.');
        setDeleteTarget(null);
        // 마지막 페이지의 마지막 건을 지우면 빈 페이지가 남는다 — 한 페이지 앞으로.
        // 재조회 응답을 기다리지 않고 먼저 옮겨 빈 표가 잠깐 보이는 것을 막는다
        if (schedules.length === 1 && page > 0) setPage((p) => p - 1);
      },
    });
  }

  return (
    <>
      <TableCard
        loading={query.isLoading}
        empty={schedules.length === 0}
        emptyLabel="등록한 개인 일정이 없습니다. 팀 캘린더에서 날짜를 클릭해 등록하세요."
        error={query.isError}
        errorLabel="일정을 불러오지 못했습니다."
        onRetry={query.refetch}
      >
        <Table className="min-w-[640px]">
          <THead>
            <Th>등록일</Th>
            <Th>종류</Th>
            <Th>기간</Th>
            <Th right>일수</Th>
            <Th>메모</Th>
            <Th right>삭제</Th>
          </THead>
          <tbody>
            {schedules.map((item) => (
              <TR key={item.id}>
                <Td>{dayjs(item.createdAt ?? item.dates[0]).format('YYYY.MM.DD')}</Td>
                <Td className="font-medium text-ink-hi">
                  {item.typeLabel ?? SCHEDULE_TYPE_LABEL[item.scheduleType] ?? item.scheduleType}
                </Td>
                <Td>{formatPeriod(item.dates)}</Td>
                <Td right className="font-semibold text-ink-hi">
                  {item.dates.length}일
                </Td>
                <Td className="max-w-[240px] truncate text-ink-mute">{item.memo ?? '—'}</Td>
                <Td right>
                  <Button
                    variant="ghost"
                    size="sm"
                    Icon={Trash2}
                    onClick={() => setDeleteTarget(item)}
                    disabled={deleteMutation.isPending}
                  >
                    삭제
                  </Button>
                </Td>
              </TR>
            ))}
          </tbody>
        </Table>
      </TableCard>

      <Pagination
        page={page}
        totalPages={totalPages}
        totalElements={totalElements}
        onChange={setPage}
      />

      {/* 연차 취소와 달리 사유를 받지 않는다 — 결재 이력에 남지 않고 그냥 삭제된다 */}
      <ConfirmDialog
        open={Boolean(deleteTarget)}
        title="일정 삭제"
        message={
          deleteTarget &&
          `${formatPeriod(deleteTarget.dates)} ${
            deleteTarget.typeLabel ?? SCHEDULE_TYPE_LABEL[deleteTarget.scheduleType]
          } 일정을 삭제하시겠습니까? 연차 잔액에는 영향이 없습니다.`
        }
        tone="danger"
        confirmLabel="삭제"
        onConfirm={handleDelete}
        onCancel={() => setDeleteTarget(null)}
        loading={deleteMutation.isPending}
      />
    </>
  );
}
