import Card from './Card.jsx';
import LoadingState from './LoadingState.jsx';
import EmptyState from './EmptyState.jsx';

// 테이블 전용 카드 — Card(padding='none') + 로딩/빈 상태를 일괄 처리.
// children에 <Table>(Table.jsx의 Table/THead/TR/Th/Td 조합) 마크업을 그대로 넣는다.
export default function TableCard({
  title,
  right,
  loading = false,
  empty = false,
  emptyLabel = '데이터가 없습니다.',
  className = '',
  children,
}) {
  return (
    <Card title={title} right={right} padding="none" className={className}>
      {loading ? <LoadingState /> : empty ? <EmptyState label={emptyLabel} /> : children}
    </Card>
  );
}
