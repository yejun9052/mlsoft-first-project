import Card from './Card.jsx';
import LoadingState from './LoadingState.jsx';
import EmptyState from './EmptyState.jsx';
import ErrorState from './ErrorState.jsx';

// 테이블 전용 카드 — Card(padding='none') + 로딩/실패/빈 상태를 일괄 처리.
// children에 <Table>(Table.jsx의 Table/THead/TR/Th/Td 조합) 마크업을 그대로 넣는다.
//
// 상태 우선순위는 loading → error → empty다. error가 empty보다 먼저인 것이 중요하다 —
// 조회가 실패하면 목록이 빈 배열이라 empty 조건도 함께 참이 되는데, 그때 "데이터가 없습니다"를
// 보여주면 사용자가 잘못된 결론을 내린다 (리뷰 F-6).
export default function TableCard({
  title,
  right,
  loading = false,
  error = false,
  errorLabel = '목록을 불러오지 못했습니다.',
  onRetry,
  empty = false,
  emptyLabel = '데이터가 없습니다.',
  className = '',
  children,
}) {
  return (
    <Card title={title} right={right} padding="none" className={className}>
      {loading ? (
        <LoadingState />
      ) : error ? (
        <ErrorState label={errorLabel} onRetry={onRetry} />
      ) : empty ? (
        <EmptyState label={emptyLabel} />
      ) : (
        children
      )}
    </Card>
  );
}
