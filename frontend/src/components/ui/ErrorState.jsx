import { AlertTriangle, RotateCw } from 'lucide-react';

// 조회 실패 상태 — 빈 상태(EmptyState)와 반드시 구분해서 쓴다.
//
// 실패를 빈 상태로 보여주면 사용자가 "데이터가 없다"고 잘못 판단한다 (리뷰 F-6).
// 결재 대기 건이 있는데도 "대기 결재 건이 없습니다"가 뜨면 결재를 놓치고, 팀원이 있는데
// "팀원이 없습니다"가 뜨면 부서 배정이 잘못됐다고 오해한다.
//
// 에러 메시지 자체는 여기 쓰지 않는다 — api/index.js 인터셉터가 서버 메시지를 toast로 이미 띄운다.
// 이 컴포넌트는 "이 영역은 비어 있는 게 아니라 못 불러온 것"이라는 사실과 재시도 수단만 제공한다.
export default function ErrorState({
  label = '불러오지 못했습니다.',
  onRetry,
  className = '',
}) {
  return (
    <div className={`flex flex-col items-center justify-center gap-3 py-12 text-center ${className}`}>
      <span className="flex h-12 w-12 items-center justify-center rounded-full border border-danger/25 bg-danger/10 text-danger">
        <AlertTriangle size={22} />
      </span>
      <p className="text-[13px] text-ink-body">{label}</p>
      {onRetry && (
        <button
          type="button"
          onClick={onRetry}
          className="inline-flex items-center gap-1.5 rounded-btn border border-white/[0.15] bg-white/[0.04] px-3 py-1.5 text-[12px] font-semibold text-ink-body transition-colors duration-150 hover:border-white/[0.22] hover:bg-white/[0.08] hover:text-ink-hi"
        >
          <RotateCw size={13} />
          다시 시도
        </button>
      )}
    </div>
  );
}
