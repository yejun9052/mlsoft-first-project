import { useMemo, useState } from 'react';
import dayjs from 'dayjs';
import { Loader2 } from 'lucide-react';
import PageHeader from '../components/ui/PageHeader.jsx';
import Card from '../components/ui/Card.jsx';
import StatusBadge from '../components/ui/StatusBadge.jsx';
import WelfareApplyModal from '../components/welfare/WelfareApplyModal.jsx';
import { getWelfareCategoryMeta, getWelfareTargetLabel } from '../constants/welfare.js';
import { useMyWelfareRequests, useWelfarePoliciesAll } from '../hooks/useWelfare.js';

// 카테고리 color 토큰 → 아이콘 박스(틴트 배경 + 아이콘 색) 클래스 매핑 (constants/welfare.js의 color와 1:1)
const COLOR_CLASS = {
  danger: 'bg-danger/12 text-danger',
  ok: 'bg-ok/12 text-ok',
  accent: 'bg-accent/16 text-accent-light',
  warn: 'bg-warn/14 text-warn',
  purple: 'bg-violet-400/14 text-violet-400',
  cyan: 'bg-sky-400/14 text-sky-400',
  muted: 'bg-white/8 text-ink-mute',
};

// 내 신청 내역 테이블 헤더
const REQUEST_COLUMNS = ['구분', '항목', '일수', '사유', '신청일', '상태'];

// 활성 정책 목록(플랫) → 카테고리별 그룹 (등장 순서 유지 — 백엔드 seed 순서를 그대로 따른다)
function groupByCategory(policies) {
  const groups = [];
  const indexByCategory = new Map();
  for (const policy of policies) {
    if (!indexByCategory.has(policy.category)) {
      indexByCategory.set(policy.category, groups.length);
      groups.push({ category: policy.category, items: [] });
    }
    groups[indexByCategory.get(policy.category)].items.push(policy);
  }
  return groups;
}

// 복리후생 카테고리 카드 — 아이콘 박스 + 항목 리스트 + 신청 버튼
function WelfareCard({ category, items, onApply }) {
  const { icon: Icon, color } = getWelfareCategoryMeta(category);
  const iconClass = COLOR_CLASS[color] ?? COLOR_CLASS.accent;

  return (
    <div className="flex h-full flex-col rounded-card bg-navy-card p-5 shadow-card">
      {/* 상단: 44px 아이콘 박스 + 카테고리명 + 항목 수 */}
      <div className="flex items-center gap-3">
        <span className={`flex h-11 w-11 items-center justify-center rounded-btn ${iconClass}`}>
          <Icon size={22} />
        </span>
        <div>
          <h3 className="text-[15px] font-semibold text-ink-hi">{category}</h3>
          <p className="text-[12px] text-ink-mute">{items.length}개 항목</p>
        </div>
      </div>

      {/* 항목 리스트 */}
      <ul className="mt-4 flex flex-1 flex-col gap-2">
        {items.map((policy) => (
          <li
            key={policy.id}
            className="flex items-center justify-between gap-2 rounded-btn bg-navy-app/40 px-3 py-2"
          >
            <div className="min-w-0">
              <p className="truncate text-[13px] font-medium text-ink-body">{policy.description}</p>
              <p className="text-[11px] text-ink-faint">{getWelfareTargetLabel(policy.target)}</p>
            </div>
            {Number(policy.defaultDays) > 0 ? (
              <span className="shrink-0 rounded-badge bg-accent/12 px-2 py-0.5 text-[11px] font-semibold text-accent-light">
                {Number(policy.defaultDays)}일
              </span>
            ) : (
              <span className="shrink-0 rounded-badge bg-white/6 px-2 py-0.5 text-[11px] font-medium text-ink-mute">
                휴가 미포함
              </span>
            )}
          </li>
        ))}
      </ul>

      {/* 하단: 신청하기 — 이 카테고리를 미리 선택한 채 신청 모달을 연다 */}
      <button
        type="button"
        onClick={() => onApply(category)}
        className="mt-4 w-full rounded-btn bg-navy-btn2 py-2.5 text-[13px] font-semibold text-ink-body transition-colors hover:bg-white/8"
      >
        신청하기
      </button>
    </div>
  );
}

// 복리후생 — 카테고리 안내 카드 그리드 + 내 신청 내역 (docs/05 §⑤)
export default function WelfarePage() {
  const policiesQuery = useWelfarePoliciesAll();
  const myRequestsQuery = useMyWelfareRequests();

  const [applyCategory, setApplyCategory] = useState(null); // null=닫힘, 문자열=해당 구분으로 모달 오픈

  const categoryGroups = useMemo(() => groupByCategory(policiesQuery.data ?? []), [policiesQuery.data]);
  const myRequests = myRequestsQuery.data?.content ?? [];

  return (
    <div>
      <PageHeader title="복리후생" subtitle="경조사·건강검진 등 복리후생 안내 및 신청" />

      {/* 카테고리 카드 3열 그리드 (좁으면 2열) */}
      {policiesQuery.isLoading ? (
        <div className="flex items-center justify-center gap-2 rounded-card bg-navy-card py-16 text-ink-mute shadow-card">
          <Loader2 size={18} className="animate-spin" />
          <span className="text-[13px]">불러오는 중…</span>
        </div>
      ) : categoryGroups.length === 0 ? (
        <div className="rounded-card bg-navy-card py-16 text-center text-[13px] text-ink-mute shadow-card">
          등록된 복리후생 정책이 없습니다.
        </div>
      ) : (
        <div className="grid grid-cols-2 gap-4 xl:grid-cols-3">
          {categoryGroups.map((group) => (
            <WelfareCard
              key={group.category}
              category={group.category}
              items={group.items}
              onApply={setApplyCategory}
            />
          ))}
        </div>
      )}

      {/* 내 신청 내역 */}
      <div className="mt-5">
        <Card title="내 신청 내역">
          {myRequestsQuery.isLoading ? (
            <div className="flex items-center justify-center gap-2 py-6 text-ink-mute">
              <Loader2 size={16} className="animate-spin" />
              <span className="text-[13px]">불러오는 중…</span>
            </div>
          ) : myRequests.length === 0 ? (
            <p className="py-6 text-center text-[13px] text-ink-mute">신청 내역이 없습니다.</p>
          ) : (
            <div className="overflow-x-auto">
              <table className="w-full min-w-[680px] text-left">
                <thead>
                  <tr className="border-b border-white/6">
                    {REQUEST_COLUMNS.map((col) => (
                      <th
                        key={col}
                        className="pb-3 pr-4 text-[11px] font-semibold uppercase tracking-wide text-ink-faint last:pr-0"
                      >
                        {col}
                      </th>
                    ))}
                  </tr>
                </thead>
                <tbody>
                  {myRequests.map((request) => (
                    <tr key={request.id} className="border-b border-white/5 last:border-0">
                      <td className="py-3 pr-4 text-[13px] text-ink-body">{request.category}</td>
                      <td className="py-3 pr-4 text-[13px] font-medium text-ink-hi">
                        {getWelfareTargetLabel(request.target)}
                      </td>
                      <td className="py-3 pr-4 text-[13px] text-ink-body">{request.addDays}일</td>
                      <td className="py-3 pr-4 text-[13px] text-ink-mute">{request.reason}</td>
                      <td className="py-3 pr-4 text-[13px] text-ink-mute">
                        {dayjs(request.createdAt).format('YYYY-MM-DD')}
                      </td>
                      <td className="py-3">
                        <StatusBadge status={request.status} />
                      </td>
                    </tr>
                  ))}
                </tbody>
              </table>
            </div>
          )}
        </Card>
      </div>

      {/* 신청 모달 — 카테고리 카드의 "신청하기"로 진입, 해당 구분이 미리 선택된 채로 열린다 */}
      {applyCategory !== null && (
        <WelfareApplyModal initialCategory={applyCategory} onClose={() => setApplyCategory(null)} />
      )}
    </div>
  );
}
