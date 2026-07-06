import toast from 'react-hot-toast';
import { HeartHandshake, Stethoscope, GraduationCap, Gift, Users, Sun } from 'lucide-react';
import PageHeader from '../components/ui/PageHeader.jsx';
import Card from '../components/ui/Card.jsx';
import StatusBadge from '../components/ui/StatusBadge.jsx';
import { welfareCategories, myWelfareRequests } from '../mocks/data.js';

// 카테고리 icon 문자열 → lucide 컴포넌트 매핑 (data.js welfareCategories.icon)
const WELFARE_ICON = {
  HeartHandshake,
  Stethoscope,
  GraduationCap,
  Gift,
  Users,
  Sun,
};

// 카테고리 color 토큰 → 아이콘 박스(틴트 배경 + 아이콘 색) 클래스 매핑
const COLOR_CLASS = {
  danger: 'bg-danger/12 text-danger',
  ok: 'bg-ok/12 text-ok',
  accent: 'bg-accent/16 text-accent-light',
  warn: 'bg-warn/14 text-warn',
  purple: 'bg-violet-400/14 text-violet-400',
  cyan: 'bg-sky-400/14 text-sky-400',
};

// 내 신청 내역 테이블 헤더
const REQUEST_COLUMNS = ['구분', '항목', '일수', '사유', '신청일', '상태'];

// 복리후생 카테고리 카드 — 아이콘 박스 + 항목 리스트 + 신청 버튼
function WelfareCard({ category }) {
  const Icon = WELFARE_ICON[category.icon] ?? Gift;
  const iconClass = COLOR_CLASS[category.color] ?? COLOR_CLASS.accent;

  return (
    <div className="flex h-full flex-col rounded-card bg-navy-card p-5 shadow-card">
      {/* 상단: 44px 아이콘 박스 + 카테고리명 + 항목 수 */}
      <div className="flex items-center gap-3">
        <span className={`flex h-11 w-11 items-center justify-center rounded-btn ${iconClass}`}>
          <Icon size={22} />
        </span>
        <div>
          <h3 className="text-[15px] font-semibold text-ink-hi">{category.label}</h3>
          <p className="text-[12px] text-ink-mute">{category.items.length}개 항목</p>
        </div>
      </div>

      {/* 항목 리스트 */}
      <ul className="mt-4 flex flex-1 flex-col gap-2">
        {category.items.map((item) => (
          <li
            key={item.id}
            className="flex items-center justify-between gap-2 rounded-btn bg-navy-app/40 px-3 py-2"
          >
            <div className="min-w-0">
              <p className="truncate text-[13px] font-medium text-ink-body">{item.name}</p>
              <p className="text-[11px] text-ink-faint">{item.target}</p>
            </div>
            {item.days > 0 ? (
              <span className="shrink-0 rounded-badge bg-accent/12 px-2 py-0.5 text-[11px] font-semibold text-accent-light">
                {item.days}일
              </span>
            ) : (
              <span className="shrink-0 rounded-badge bg-white/6 px-2 py-0.5 text-[11px] font-medium text-ink-mute">
                휴가 미포함
              </span>
            )}
          </li>
        ))}
      </ul>

      {/* 하단: 신청하기 (Secondary — mock) */}
      <button
        type="button"
        onClick={() => toast('복리후생 신청 (준비 중)', { icon: '🎁' })}
        className="mt-4 w-full rounded-btn bg-navy-btn2 py-2.5 text-[13px] font-semibold text-ink-body transition-colors hover:bg-white/8"
      >
        신청하기
      </button>
    </div>
  );
}

// 복리후생 — 카테고리 안내 카드 그리드 + 내 신청 내역 (docs/05 §⑤)
export default function WelfarePage() {
  return (
    <div>
      <PageHeader title="복리후생" subtitle="경조사·건강검진 등 복리후생 안내 및 신청" />

      {/* 카테고리 카드 3열 그리드 (좁으면 2열) */}
      <div className="grid grid-cols-2 gap-4 xl:grid-cols-3">
        {welfareCategories.map((category) => (
          <WelfareCard key={category.key} category={category} />
        ))}
      </div>

      {/* 내 신청 내역 */}
      <div className="mt-5">
        <Card title="내 신청 내역">
          {myWelfareRequests.length === 0 ? (
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
                  {myWelfareRequests.map((request) => (
                    <tr key={request.id} className="border-b border-white/5 last:border-0">
                      <td className="py-3 pr-4 text-[13px] text-ink-body">{request.category}</td>
                      <td className="py-3 pr-4 text-[13px] font-medium text-ink-hi">{request.name}</td>
                      <td className="py-3 pr-4 text-[13px] text-ink-body">{request.days}일</td>
                      <td className="py-3 pr-4 text-[13px] text-ink-mute">{request.reason}</td>
                      <td className="py-3 pr-4 text-[13px] text-ink-mute">{request.appliedAt}</td>
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
    </div>
  );
}
