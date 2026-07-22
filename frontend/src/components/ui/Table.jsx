// 테이블 프리미티브 — AdminMembers/AdminPolicy/Welfare/Team의 네이티브 table 마크업을 한 구현으로 수렴.
// Table이 가로 스크롤 래퍼(overflow-x-auto)까지 함께 담당한다. min-w 등 표별 폭 지정은 className으로 전달.
// (HistoryPage의 div-grid 커스텀 테이블도 2단계에서 이 구성으로 옮긴다.)
export default function Table({ className = '', children }) {
  return (
    <div className="overflow-x-auto">
      <table className={`w-full text-left text-[13px] ${className}`}>{children}</table>
    </div>
  );
}

// 헤더 행 — 보더 + navy-header 배경. children으로 <Th>들을 그대로 나열.
export function THead({ children }) {
  return (
    <thead>
      <tr className="border-b border-white/6 bg-navy-header">{children}</tr>
    </thead>
  );
}

// 헤더 셀
export function Th({ children, right = false, className = '' }) {
  return (
    <th
      className={`whitespace-nowrap px-5 py-3 text-[11px] font-semibold uppercase tracking-wider text-ink-faint ${
        right ? 'text-right' : 'text-left'
      } ${className}`}
    >
      {children}
    </th>
  );
}

// 데이터 행 — hover 하이라이트 + 마지막 행 보더 제거. onClick 등 나머지 prop은 <tr>로 그대로 전달
// (행 전체를 클릭 타깃으로 쓰는 테이블 — 예: WelfarePage 정책 목록 — 를 위함).
export function TR({ children, className = '', ...rest }) {
  return (
    <tr className={`border-b border-white/5 transition-colors last:border-0 hover:bg-white/[0.02] ${className}`} {...rest}>
      {children}
    </tr>
  );
}

// 데이터 셀
export function Td({ children, right = false, className = '' }) {
  return (
    <td className={`whitespace-nowrap px-5 py-3 ${right ? 'text-right' : ''} ${className}`}>{children}</td>
  );
}
