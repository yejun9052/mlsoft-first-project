import useMediaQuery from '../../hooks/useMediaQuery.js';

const MOBILE_TABLE_QUERY = '(max-width: 767px)';

// 표와 카드를 CSS로 동시에 숨겨 두지 않는다.
// 보이지 않는 복제본이 스크린리더와 테스트에 남아 같은 데이터를 두 번 읽는 것을 막기 위해 렌더 트리 자체를 나눈다.
export default function ResponsiveTable({ table, cards }) {
  const isMobile = useMediaQuery(MOBILE_TABLE_QUERY);

  return isMobile ? (
    <div className="space-y-3 p-3" data-testid="responsive-table-cards">
      {cards}
    </div>
  ) : (
    <div data-testid="responsive-table-table">{table}</div>
  );
}
