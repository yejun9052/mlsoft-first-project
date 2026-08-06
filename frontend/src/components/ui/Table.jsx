// 테이블 프리미티브 — AdminMembers/AdminPolicy/Welfare/Team의 네이티브 table 마크업을 한 구현으로 수렴.
// Table이 가로 스크롤 래퍼(overflow-x-auto)까지 함께 담당한다. min-w 등 표별 폭 지정은 className으로 전달.
export default function Table({ className = '', children }) {
  return (
    <div className="overflow-x-auto">
      <table className={`w-full text-left text-[13px] ${className}`}>{children}</table>
    </div>
  );
}

// 헤더 행 — 글래스 카드 위에 얹히므로 불투명 배경 대신 살짝 어두운 반투명 띠로 처리한다.
export function THead({ children }) {
  return (
    <thead>
      <tr className="border-b border-white/[0.12] bg-navy-app/40">{children}</tr>
    </thead>
  );
}

// 헤더 셀
export function Th({ children, right = false, className = '' }) {
  return (
    <th
      className={`whitespace-nowrap px-5 py-3 text-[11px] font-semibold uppercase tracking-[0.1em] text-ink-faint ${
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
    <tr
      className={`border-b border-white/[0.10] transition-colors last:border-0 hover:bg-accent-cyan/[0.04] ${className}`}
      {...rest}
    >
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
