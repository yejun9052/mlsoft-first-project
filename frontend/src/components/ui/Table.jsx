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

// 헤더 셀. sticky=true면 오른쪽에 고정한다 — 컬럼이 많아 가로 스크롤이 생기는 표에서
// 마지막 "관리" 열이 스크롤 밖으로 밀려 조작 버튼 자체를 못 보는 문제 대응 (B-2).
// navy-card는 글래스 카드의 불투명 폴백 토큰이라(index.css) 스크롤되는 셀이 밑으로
// 비쳐 보이지 않게 가리는 배경으로 그대로 재사용한다 — 새 색을 만들지 않는다.
export function Th({ children, right = false, sticky = false, className = '' }) {
  return (
    <th
      className={`whitespace-nowrap px-5 py-3 text-[11px] font-semibold uppercase tracking-[0.1em] text-ink-faint ${
        right ? 'text-right' : 'text-left'
      } ${sticky ? 'sticky right-0 z-10 border-l border-white/[0.12] bg-navy-card' : ''} ${className}`}
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

// 데이터 셀. sticky는 Th와 짝을 맞춰 같은 열을 고정한다 (B-2 — Th 주석 참고)
export function Td({ children, right = false, sticky = false, className = '' }) {
  return (
    <td
      className={`whitespace-nowrap px-5 py-3 ${right ? 'text-right' : ''} ${
        sticky ? 'sticky right-0 z-10 border-l border-white/[0.12] bg-navy-card' : ''
      } ${className}`}
    >
      {children}
    </td>
  );
}
