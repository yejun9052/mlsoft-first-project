import { useEffect, useMemo, useState } from 'react';
import toast from 'react-hot-toast';
import { Loader2, X } from 'lucide-react';
import { getWelfareTargetLabel } from '../../constants/welfare.js';
import { TEST_APPROVER_CANDIDATES } from '../../constants/approvers.js';
import { useApplyWelfare, useWelfarePoliciesAll } from '../../hooks/useWelfare.js';

/**
 * 복리후생 신청 모달 — 구분(category) 선택 → 그 구분에 속한 대상(target) 선택 → 선택된 정책의
 * 사유/추가일수/증빙서류 자동 미리보기 → 상세 사유 입력 → 제출 (v1 WelfareApplyModal UX 참고, 스타일은
 * V2 다크 네이비 카드 테마로 재구현).
 *
 * 상세 사유는 백엔드 WelfareCreateRequest.reason이 @NotBlank라 필수 입력이다(v1은 선택값이었지만
 * 이번 백엔드 계약은 다르므로 필수로 취급 — 빈 값으로 제출하면 400).
 *
 * 서브 승인자 후보를 내려주는 API가 아직 없어(연차 신청과 동일한 사정 — CalendarPage 참고),
 * 로컬 DB에 존재하는 테스트 TEAM_LEADER 2명을 하드코딩(constants/approvers.js)해 재사용한다.
 */
export default function WelfareApplyModal({ initialCategory, onClose }) {
  const policiesQuery = useWelfarePoliciesAll();
  const policies = policiesQuery.data ?? [];

  const [category, setCategory] = useState(initialCategory ?? '');
  const [policyId, setPolicyId] = useState('');
  const [reason, setReason] = useState('');
  const [subApproverId, setSubApproverId] = useState('');
  const applyWelfareMutation = useApplyWelfare();

  // Esc로 닫기
  useEffect(() => {
    function onKeyDown(e) {
      if (e.key === 'Escape') onClose();
    }
    window.addEventListener('keydown', onKeyDown);
    return () => window.removeEventListener('keydown', onKeyDown);
  }, [onClose]);

  // 구분 목록 (활성 정책 기준 distinct, 등장 순서 유지)
  // 의존성은 policiesQuery.data(쿼리가 실제로 들고 있는 안정적 참조)로 둔다 — policies는 로딩 중
  // `?? []`로 매 렌더 새 배열을 만들어 참조가 바뀌므로 그대로 의존성에 넣으면 매 렌더 재계산된다.
  const categories = useMemo(
    () => [...new Set((policiesQuery.data ?? []).map((p) => p.category))],
    [policiesQuery.data],
  );

  // 선택한 구분에 속한 정책들 (대상 선택 옵션)
  const targetsForCategory = useMemo(
    () => (policiesQuery.data ?? []).filter((p) => p.category === category),
    [policiesQuery.data, category],
  );

  const selectedPolicy = policies.find((p) => String(p.id) === String(policyId));

  function handleCategoryChange(next) {
    setCategory(next);
    setPolicyId(''); // 구분이 바뀌면 이전 구분의 대상 선택은 무효화
  }

  async function handleSubmit(e) {
    e.preventDefault();
    if (!category) {
      toast.error('구분을 선택해주세요.');
      return;
    }
    if (!selectedPolicy) {
      toast.error('대상을 선택해주세요.');
      return;
    }
    if (!reason.trim()) {
      toast.error('상세 사유를 입력해주세요.');
      return;
    }
    try {
      await applyWelfareMutation.mutateAsync({
        policyId: selectedPolicy.id,
        reason: reason.trim(),
        subApproverId: subApproverId || null,
      });
      toast.success('복리후생 신청이 접수되었습니다.', { icon: '🎁' });
      onClose();
    } catch {
      // 실패 toast는 api 인터셉터가 일괄 처리 — 모달은 열어둔 채 재시도할 수 있게 둔다
    }
  }

  return (
    <div
      role="dialog"
      aria-label="복리후생 신청"
      className="fixed inset-0 z-50 flex items-center justify-center bg-black/60 p-4"
      onClick={onClose}
    >
      <div
        className="flex max-h-[90vh] w-full max-w-[440px] flex-col overflow-hidden rounded-card bg-navy-card shadow-card"
        onClick={(e) => e.stopPropagation()}
      >
        {/* 헤더 */}
        <div className="flex items-center justify-between border-b border-white/6 px-5 py-4">
          <h2 className="text-[16px] font-semibold text-ink-hi">복리후생 신청</h2>
          <button
            type="button"
            onClick={onClose}
            className="rounded-btn p-1 text-ink-mute transition-colors hover:bg-white/6 hover:text-ink-body"
            aria-label="닫기"
          >
            <X size={16} />
          </button>
        </div>

        <form onSubmit={handleSubmit} className="flex flex-1 flex-col gap-4 overflow-y-auto px-5 py-4">
          {/* 구분 */}
          <div>
            <FieldLabel required>구분</FieldLabel>
            <select
              value={category}
              onChange={(e) => handleCategoryChange(e.target.value)}
              disabled={policiesQuery.isLoading}
              className="w-full rounded-btn border border-white/8 bg-navy-btn2 px-2.5 py-2.5 text-[14px] text-ink-hi focus:border-accent/50 focus:outline-none"
            >
              <option value="">선택하세요</option>
              {categories.map((c) => (
                <option key={c} value={c}>
                  {c}
                </option>
              ))}
            </select>
          </div>

          {/* 대상 */}
          <div>
            <FieldLabel required>대상</FieldLabel>
            <select
              value={policyId}
              onChange={(e) => setPolicyId(e.target.value)}
              disabled={!category}
              className="w-full rounded-btn border border-white/8 bg-navy-btn2 px-2.5 py-2.5 text-[14px] text-ink-hi focus:border-accent/50 focus:outline-none disabled:cursor-not-allowed disabled:opacity-50"
            >
              <option value="">{category ? '선택하세요' : '먼저 구분을 선택해주세요'}</option>
              {targetsForCategory.map((p) => (
                <option key={p.id} value={p.id}>
                  {getWelfareTargetLabel(p.target)}
                </option>
              ))}
            </select>
          </div>

          {/* 선택된 정책 자동 미리보기 */}
          {selectedPolicy && (
            <div className="flex flex-col gap-2 rounded-btn bg-navy-app/50 px-3.5 py-3 text-[13px]">
              <PreviewRow label="사유">
                {selectedPolicy.category} · {getWelfareTargetLabel(selectedPolicy.target)}
              </PreviewRow>
              <PreviewRow label="추가 연차">
                <span className="font-semibold text-accent-light">{Number(selectedPolicy.defaultDays)}일</span>
              </PreviewRow>
              <PreviewRow label="증빙서류 안내" column>
                {selectedPolicy.defaultEvidence}
              </PreviewRow>
              <p className="text-[11px] text-ink-dim">※ 위 서류는 인사 담당자에게 직접 제출해주세요.</p>
            </div>
          )}

          {/* 상세 사유 (필수) */}
          <div>
            <FieldLabel required>상세 사유</FieldLabel>
            <textarea
              value={reason}
              onChange={(e) => setReason(e.target.value)}
              rows={3}
              maxLength={500}
              placeholder={
                selectedPolicy ? `예: ${selectedPolicy.description}` : '먼저 대상을 선택해주세요'
              }
              className="w-full resize-none rounded-btn border border-white/8 bg-navy-btn2 px-3 py-2.5 text-[14px] text-ink-hi placeholder:text-ink-dim focus:border-accent/50 focus:outline-none"
            />
          </div>

          {/* 서브 승인자 (선택) — 연차 신청과 동일한 테스트 계정 재사용 */}
          <div>
            <FieldLabel>서브 승인자 (선택)</FieldLabel>
            <select
              value={subApproverId}
              onChange={(e) => setSubApproverId(e.target.value)}
              className="w-full rounded-btn border border-white/8 bg-navy-btn2 px-2.5 py-2.5 text-[14px] text-ink-hi focus:border-accent/50 focus:outline-none"
            >
              <option value="">선택 안 함</option>
              {TEST_APPROVER_CANDIDATES.map((a) => (
                <option key={a.id} value={a.id}>
                  {a.name} · {a.departmentName}
                </option>
              ))}
            </select>
          </div>

          {/* 액션 */}
          <div className="mt-1 flex items-center justify-end gap-2">
            <button
              type="button"
              onClick={onClose}
              className="rounded-btn bg-navy-btn2 px-4 py-2.5 text-[13px] font-semibold text-ink-body transition-colors hover:bg-white/8"
            >
              취소
            </button>
            <button
              type="submit"
              disabled={applyWelfareMutation.isPending || !selectedPolicy}
              className="flex items-center gap-1.5 rounded-btn bg-accent px-4 py-2.5 text-[13px] font-semibold text-white shadow-btn transition-colors hover:bg-accent-dark disabled:cursor-not-allowed disabled:opacity-60"
            >
              {applyWelfareMutation.isPending && <Loader2 size={14} className="animate-spin" />}
              {applyWelfareMutation.isPending ? '신청 중…' : '신청하기'}
            </button>
          </div>
        </form>
      </div>
    </div>
  );
}

function FieldLabel({ children, required }) {
  return (
    <p className="mb-1.5 text-[13px] font-medium text-ink-mute">
      {children}
      {required && <span className="ml-0.5 text-danger">*</span>}
    </p>
  );
}

function PreviewRow({ label, column, children }) {
  if (column) {
    return (
      <div className="flex flex-col gap-0.5">
        <span className="text-[12px] text-ink-faint">{label}</span>
        <span className="text-ink-body">{children}</span>
      </div>
    );
  }
  return (
    <div className="flex items-center justify-between gap-2">
      <span className="text-[12px] text-ink-faint">{label}</span>
      <span className="text-ink-body">{children}</span>
    </div>
  );
}
