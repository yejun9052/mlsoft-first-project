// 통계 스트립 — 박스 없이 세로 구분선으로 Stat 여러 개를 나열 (Dashboard/History 상단 통계 행).
// 구분선은 첫 칸을 제외한 모든 자식에 자동 적용(임의 변형 선택자) — 호출부가 pl-8 래퍼를 따로 두지 않아도 됨.
export default function StatStrip({ children, className = '' }) {
  return (
    <div className={`flex flex-wrap items-end gap-y-4 [&>*+*]:border-l [&>*+*]:border-white/8 [&>*+*]:pl-8 ${className}`}>
      {children}
    </div>
  );
}
