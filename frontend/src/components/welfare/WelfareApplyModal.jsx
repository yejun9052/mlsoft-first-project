import { useMemo, useState } from 'react';
import toast from 'react-hot-toast';
import { getWelfareTargetLabel } from '../../constants/welfare.js';
import { TEST_APPROVER_CANDIDATES } from '../../constants/approvers.js';
import { useApplyWelfare, useWelfarePoliciesAll } from '../../hooks/useWelfare.js';
import Modal from '../ui/Modal.jsx';
import Field from '../ui/Field.jsx';
import Select from '../ui/Select.jsx';
import Textarea from '../ui/Textarea.jsx';
import Button from '../ui/Button.jsx';

const FORM_ID = 'welfare-apply-form';

/**
 * 복리후생 신청 모달 — 구분(category) 선택 → 그 구분에 속한 대상(target) 선택 → 선택된 정책의
 * 사유/추가일수/증빙서류 자동 미리보기 → 상세 사유 입력 → 제출 (v1 WelfareApplyModal UX 참고, 스타일은
 * V2 다크 네이비 카드 테마로 재구현).
 *
 * 킷의 Modal(backdrop·Esc·클릭아웃)을 그대로 사용 — 폼(children)과 액션 버튼(footer)이 Modal 안에서
 * DOM상 분리돼 있어, 제출 버튼은 form 속성으로 아래 <form id={FORM_ID}>를 가리켜 연결한다.
 * (캘린더의 드래그 가능 플로팅 패널과는 의도적으로 다른 패턴이라 그대로 유지, 통일하지 않음)
 *
 * 상세 사유는 백엔드 WelfareCreateRequest.reason이 @NotBlank라 필수 입력이다(v1은 선택값이었지만
 * 이번 백엔드 계약은 다르므로 필수로 취급 — 빈 값으로 제출하면 400).
 *
 * 서브 승인자 후보를 내려주는 API가 아직 없어(연차 신청과 동일한 사정 — CalendarPage 참고),
 * 로컬 DB에 존재하는 테스트 TEAM_LEADER 2명을 하드코딩(constants/approvers.js)해 재사용한다.
 *
 * initialPolicyId — 정책 테이블(WelfarePage)에서 특정 행을 클릭해 들어온 경우, 구분뿐 아니라
 * 대상까지 미리 선택된 채로 연다(정책 목록은 WelfarePage가 이미 불러온 react-query 캐시를
 * 그대로 재사용하므로 이 시점엔 이미 로드돼 있다).
 */
export default function WelfareApplyModal({ initialCategory, initialPolicyId, onClose }) {
  const policiesQuery = useWelfarePoliciesAll();
  const policies = policiesQuery.data ?? [];

  const [category, setCategory] = useState(initialCategory ?? '');
  const [policyId, setPolicyId] = useState(initialPolicyId != null ? String(initialPolicyId) : '');
  const [reason, setReason] = useState('');
  const [subApproverId, setSubApproverId] = useState('');
  const applyWelfareMutation = useApplyWelfare();

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
    <Modal
      title="복리후생 신청"
      onClose={onClose}
      footer={
        <>
          <Button variant="secondary" onClick={onClose} lift={false}>
            취소
          </Button>
          <Button
            type="submit"
            form={FORM_ID}
            loading={applyWelfareMutation.isPending}
            disabled={!selectedPolicy}
            lift={false}
          >
            {applyWelfareMutation.isPending ? '신청 중…' : '신청하기'}
          </Button>
        </>
      }
    >
      <form id={FORM_ID} onSubmit={handleSubmit} className="flex flex-col gap-4">
        {/* 구분 */}
        <Field label="구분" required>
          <Select
            value={category}
            onChange={(e) => handleCategoryChange(e.target.value)}
            disabled={policiesQuery.isLoading}
          >
            <option value="">선택하세요</option>
            {categories.map((c) => (
              <option key={c} value={c}>
                {c}
              </option>
            ))}
          </Select>
        </Field>

        {/* 대상 */}
        <Field label="대상" required>
          <Select value={policyId} onChange={(e) => setPolicyId(e.target.value)} disabled={!category}>
            <option value="">{category ? '선택하세요' : '먼저 구분을 선택해주세요'}</option>
            {targetsForCategory.map((p) => (
              <option key={p.id} value={p.id}>
                {getWelfareTargetLabel(p.target)}
              </option>
            ))}
          </Select>
        </Field>

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
        <Field label="상세 사유" required>
          <Textarea
            value={reason}
            onChange={(e) => setReason(e.target.value)}
            rows={3}
            maxLength={500}
            placeholder={selectedPolicy ? `예: ${selectedPolicy.description}` : '먼저 대상을 선택해주세요'}
          />
        </Field>

        {/* 서브 승인자 (선택) — 연차 신청과 동일한 테스트 계정 재사용 */}
        <Field label="서브 승인자 (선택)">
          <Select value={subApproverId} onChange={(e) => setSubApproverId(e.target.value)}>
            <option value="">선택 안 함</option>
            {TEST_APPROVER_CANDIDATES.map((a) => (
              <option key={a.id} value={a.id}>
                {a.name} · {a.departmentName}
              </option>
            ))}
          </Select>
        </Field>
      </form>
    </Modal>
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
