import { useMemo, useState } from 'react';
import dayjs from 'dayjs';
import { ChevronRight } from 'lucide-react';
import PageHeader from '../components/ui/PageHeader.jsx';
import TableCard from '../components/ui/TableCard.jsx';
import Table, { THead, Th, TR, Td } from '../components/ui/Table.jsx';
import StatusBadge from '../components/ui/StatusBadge.jsx';
import WelfareApplyModal from '../components/welfare/WelfareApplyModal.jsx';
import { getWelfareCategoryMeta, getWelfareTargetLabel } from '../constants/welfare.js';
import { useMyWelfareRequests, useWelfarePoliciesAll } from '../hooks/useWelfare.js';

// 정책 목록(플랫, 백엔드 seed 순서 그대로) → 같은 구분이 연달아 나오면 구분 셀을 첫 행에만 표시하고
// 그룹 경계에 굵은 상단 보더를 준다 — 카테고리 카드 그리드 대신 정보 밀도 높은 단일 테이블로 통일.
function withGroupInfo(policies) {
  let lastCategory = null;
  return policies.map((policy, index) => {
    const isNewGroup = policy.category !== lastCategory;
    const groupBoundary = isNewGroup && index !== 0;
    lastCategory = policy.category;
    return { policy, showCategory: isNewGroup, groupBoundary };
  });
}

// 정책 한 행 — 행 전체가 클릭 타깃(onApply)이라 신청 진입에 별도 버튼이 필요 없다.
function PolicyRow({ policy, showCategory, groupBoundary, onApply }) {
  const { icon: Icon } = getWelfareCategoryMeta(policy.category);

  return (
    <TR
      className={`cursor-pointer ${groupBoundary ? 'border-t-2 border-t-white/10' : ''}`}
      onClick={() => onApply(policy)}
    >
      <Td>
        {showCategory && (
          <span className="flex items-center gap-2 font-medium text-ink-hi">
            <Icon size={15} className="shrink-0 text-accent-light" />
            {policy.category}
          </span>
        )}
      </Td>
      <Td className="text-ink-body">{getWelfareTargetLabel(policy.target)}</Td>
      <Td right className="font-semibold text-ink-hi">
        {Number(policy.defaultDays) > 0 ? `${Number(policy.defaultDays)}일` : '휴가 미포함'}
      </Td>
      <Td className="text-ink-mute">
        <span className="block max-w-[220px] truncate" title={policy.defaultEvidence}>
          {policy.defaultEvidence}
        </span>
      </Td>
      <Td right className="text-ink-dim">
        <ChevronRight size={15} />
      </Td>
    </TR>
  );
}

// 복리후생 — 정책 안내 테이블(구분·대상·지급일수·증빙서류, 행 클릭으로 신청) + 내 신청 내역 (docs/05 §⑤)
export default function WelfarePage() {
  const policiesQuery = useWelfarePoliciesAll();
  const myRequestsQuery = useMyWelfareRequests();

  const [applyPolicy, setApplyPolicy] = useState(null); // null=닫힘, 정책 객체=해당 구분+대상으로 모달 오픈

  const policies = policiesQuery.data ?? [];
  const rows = useMemo(() => withGroupInfo(policiesQuery.data ?? []), [policiesQuery.data]);
  const myRequests = myRequestsQuery.data?.content ?? [];

  return (
    <div>
      <PageHeader title="복리후생" subtitle="경조사·건강검진 등 복리후생 안내 및 신청" />

      {/* 정책 안내 테이블 — 행을 클릭하면 해당 구분·대상이 미리 선택된 채 신청 모달이 열린다 */}
      <TableCard
        title="복리후생 안내"
        loading={policiesQuery.isLoading}
        empty={policies.length === 0}
        emptyLabel="등록된 복리후생 정책이 없습니다."
      >
        <Table>
          <THead>
            <Th>구분</Th>
            <Th>대상</Th>
            <Th right>지급일수</Th>
            <Th>증빙서류</Th>
            <Th right />
          </THead>
          <tbody>
            {rows.map(({ policy, showCategory, groupBoundary }) => (
              <PolicyRow
                key={policy.id}
                policy={policy}
                showCategory={showCategory}
                groupBoundary={groupBoundary}
                onApply={setApplyPolicy}
              />
            ))}
          </tbody>
        </Table>
      </TableCard>

      {/* 내 신청 내역 */}
      <div className="mt-5">
        <TableCard
          title="내 신청 내역"
          loading={myRequestsQuery.isLoading}
          empty={myRequests.length === 0}
          emptyLabel="신청 내역이 없습니다."
        >
          <Table className="min-w-[680px]">
            <THead>
              <Th>구분</Th>
              <Th>항목</Th>
              <Th right>일수</Th>
              <Th>사유</Th>
              <Th>신청일</Th>
              <Th>상태</Th>
            </THead>
            <tbody>
              {myRequests.map((request) => (
                <TR key={request.id}>
                  <Td>{request.category}</Td>
                  <Td className="font-medium text-ink-hi">{getWelfareTargetLabel(request.target)}</Td>
                  <Td right>{request.addDays}일</Td>
                  <Td className="text-ink-mute">
                    <span className="block max-w-[240px] truncate" title={request.reason}>
                      {request.reason}
                    </span>
                  </Td>
                  <Td className="text-ink-mute">{dayjs(request.createdAt).format('YYYY-MM-DD')}</Td>
                  <Td>
                    <StatusBadge status={request.status} />
                  </Td>
                </TR>
              ))}
            </tbody>
          </Table>
        </TableCard>
      </div>

      {/* 신청 모달 — 정책 테이블의 행 클릭으로 진입, 해당 구분+대상이 미리 선택된 채로 열린다
          (캘린더=드래그 패널과 달리 복리후생은 중앙 오버레이 모달 — 의도적으로 다른 패턴, 통일하지 않음) */}
      {applyPolicy !== null && (
        <WelfareApplyModal
          initialCategory={applyPolicy.category}
          initialPolicyId={applyPolicy.id}
          onClose={() => setApplyPolicy(null)}
        />
      )}
    </div>
  );
}
