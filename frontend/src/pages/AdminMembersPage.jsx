import { useMemo, useState } from 'react';
import toast from 'react-hot-toast';
import { Search, UserPlus, Shield, Building2, UserMinus } from 'lucide-react';
import { members } from '../mocks/data.js';
import { ROLE, ROLE_LABEL } from '../constants/roles.js';
import PageHeader from '../components/ui/PageHeader.jsx';
import Card from '../components/ui/Card.jsx';
import StatusBadge from '../components/ui/StatusBadge.jsx';

// 탭 정의 (재직 / 퇴직)
const TAB_ACTIVE = 'active';
const TAB_RETIRED = 'retired';

// 역할 필터 칩 (전체 + 3개 역할)
const ROLE_FILTERS = [
  { value: 'ALL', label: '전체' },
  { value: ROLE.EMPLOYEE, label: '사원' },
  { value: ROLE.TEAM_LEADER, label: '팀장' },
  { value: ROLE.SYSTEM_ADMIN, label: '총관리자' },
];

export default function AdminMembersPage() {
  const [tab, setTab] = useState(TAB_ACTIVE);
  const [search, setSearch] = useState('');
  const [roleFilter, setRoleFilter] = useState('ALL');

  // 재직자: 검색(이름/이메일) + 역할 필터 적용
  const activeMembers = useMemo(() => {
    const q = search.trim().toLowerCase();
    return members
      .filter((m) => m.isActive)
      .filter((m) => roleFilter === 'ALL' || m.role === roleFilter)
      .filter(
        (m) => !q || m.name.toLowerCase().includes(q) || m.email.toLowerCase().includes(q),
      );
  }, [search, roleFilter]);

  // 퇴직자 (필터 미적용)
  const retiredMembers = useMemo(() => members.filter((m) => !m.isActive), []);

  const activeCount = useMemo(() => members.filter((m) => m.isActive).length, []);
  const rows = tab === TAB_ACTIVE ? activeMembers : retiredMembers;

  return (
    <div>
      <PageHeader title="구성원 관리" subtitle="전체 구성원 조회 및 권한·부서 관리">
        <button
          type="button"
          onClick={() => toast('구성원 추가 (준비 중)')}
          className="flex items-center gap-1.5 rounded-btn bg-accent px-3.5 py-2.5 text-[13px] font-semibold text-white shadow-btn transition-colors hover:bg-accent-dark"
        >
          <UserPlus size={15} />
          구성원 추가
        </button>
      </PageHeader>

      {/* 탭 (재직 / 퇴직) */}
      <div className="mb-5 flex items-center gap-1 border-b border-white/6">
        <TabButton
          active={tab === TAB_ACTIVE}
          label="재직"
          count={activeCount}
          onClick={() => setTab(TAB_ACTIVE)}
        />
        <TabButton
          active={tab === TAB_RETIRED}
          label="퇴직"
          count={retiredMembers.length}
          onClick={() => setTab(TAB_RETIRED)}
        />
      </div>

      {/* 검색 + 역할 필터 (재직 탭 전용) */}
      {tab === TAB_ACTIVE && (
        <div className="mb-4 flex flex-wrap items-center justify-between gap-3">
          <div className="relative">
            <Search
              size={15}
              className="pointer-events-none absolute left-3 top-1/2 -translate-y-1/2 text-ink-faint"
            />
            <input
              type="text"
              value={search}
              onChange={(e) => setSearch(e.target.value)}
              placeholder="이름 또는 이메일 검색"
              className="w-72 rounded-btn border border-white/8 bg-navy-btn2 py-2.5 pl-9 pr-3 text-[13px] text-ink-hi placeholder:text-ink-faint focus:border-accent/50 focus:outline-none"
            />
          </div>
          <div className="flex items-center gap-1.5">
            {ROLE_FILTERS.map((f) => (
              <FilterChip
                key={f.value}
                active={roleFilter === f.value}
                label={f.label}
                onClick={() => setRoleFilter(f.value)}
              />
            ))}
          </div>
        </div>
      )}

      {/* 테이블 카드 */}
      <Card bodyClassName="p-0">
        <div className="overflow-x-auto">
          <table className="w-full min-w-[880px] text-left text-[13px]">
            <thead>
              <tr className="border-b border-white/6 bg-navy-header">
                <Th>구성원</Th>
                <Th>이메일</Th>
                <Th>부서</Th>
                <Th>직책</Th>
                <Th>{tab === TAB_ACTIVE ? '역할' : '상태'}</Th>
                <Th right>연차 (잔여/기본)</Th>
                <Th>입사일</Th>
                {tab === TAB_ACTIVE ? <Th right>관리</Th> : <Th>퇴직일</Th>}
              </tr>
            </thead>
            <tbody>
              {rows.length === 0 ? (
                <tr>
                  <td colSpan={8} className="px-5 py-12 text-center text-[13px] text-ink-mute">
                    검색 결과가 없습니다.
                  </td>
                </tr>
              ) : (
                rows.map((m) => (
                  <tr
                    key={m.id}
                    className="border-b border-white/5 transition-colors last:border-0 hover:bg-white/[0.02]"
                  >
                    <td className="whitespace-nowrap px-5 py-3">
                      <div className="flex items-center gap-3">
                        <Avatar name={m.name} />
                        <span className="font-medium text-ink-hi">{m.name}</span>
                      </div>
                    </td>
                    <td className="whitespace-nowrap px-5 py-3 text-ink-mute">{m.email}</td>
                    <td className="whitespace-nowrap px-5 py-3 text-ink-body">{m.departmentName}</td>
                    <td className="whitespace-nowrap px-5 py-3 text-ink-body">{m.position}</td>
                    <td className="whitespace-nowrap px-5 py-3">
                      {tab === TAB_ACTIVE ? (
                        <StatusBadge
                          label={ROLE_LABEL[m.role]}
                          tone={m.role === ROLE.EMPLOYEE ? 'muted' : 'accent'}
                        />
                      ) : (
                        <StatusBadge label="퇴직" tone="muted" />
                      )}
                    </td>
                    <td className="whitespace-nowrap px-5 py-3 text-right tabular-nums">
                      <span className="font-semibold text-ink-hi">{m.remainingDays}</span>
                      <span className="text-ink-faint"> / {m.baseDays}일</span>
                    </td>
                    <td className="whitespace-nowrap px-5 py-3 text-ink-mute tabular-nums">
                      {m.hireDate}
                    </td>
                    {tab === TAB_ACTIVE ? (
                      <td className="whitespace-nowrap px-5 py-3">
                        <div className="flex items-center justify-end gap-1">
                          <IconAction
                            Icon={Shield}
                            label="역할 변경"
                            onClick={() => toast(`${m.name}님의 역할 변경 (준비 중)`)}
                          />
                          <IconAction
                            Icon={Building2}
                            label="부서 변경"
                            onClick={() => toast(`${m.name}님의 부서 변경 (준비 중)`)}
                          />
                          <IconAction
                            Icon={UserMinus}
                            label="퇴직 처리"
                            danger
                            onClick={() => toast(`${m.name}님 퇴직 처리 (준비 중)`)}
                          />
                        </div>
                      </td>
                    ) : (
                      <td className="whitespace-nowrap px-5 py-3 text-ink-mute tabular-nums">
                        {m.retiredAt}
                      </td>
                    )}
                  </tr>
                ))
              )}
            </tbody>
          </table>
        </div>
      </Card>
    </div>
  );
}

// 탭 버튼 — 활성 시 하단 2px 파랑 보더 + 카운트 배지
function TabButton({ active, label, count, onClick }) {
  return (
    <button
      type="button"
      onClick={onClick}
      className={`relative px-4 py-2.5 text-[13px] font-semibold transition-colors ${
        active ? 'text-accent-light' : 'text-ink-mute hover:text-ink-body'
      }`}
    >
      {label}
      <span
        className={`ml-1.5 rounded-full px-1.5 py-0.5 text-[11px] ${
          active ? 'bg-accent/16 text-accent-light' : 'bg-white/6 text-ink-faint'
        }`}
      >
        {count}
      </span>
      {active && <span className="absolute inset-x-0 -bottom-px h-0.5 rounded-full bg-accent" />}
    </button>
  );
}

// 역할 필터 칩
function FilterChip({ active, label, onClick }) {
  return (
    <button
      type="button"
      onClick={onClick}
      className={`rounded-badge px-3 py-1.5 text-[12px] font-semibold transition-colors ${
        active
          ? 'bg-accent/16 text-accent-light'
          : 'bg-navy-btn2 text-ink-mute hover:bg-white/8 hover:text-ink-body'
      }`}
    >
      {label}
    </button>
  );
}

// 이니셜 아바타
function Avatar({ name }) {
  return (
    <span className="flex h-9 w-9 shrink-0 items-center justify-center rounded-full bg-navy-avatar text-[13px] font-semibold text-ink-body">
      {name.slice(0, 1)}
    </span>
  );
}

// 액션 아이콘 버튼 (퇴직 처리는 danger 틴트)
function IconAction({ Icon, label, onClick, danger }) {
  return (
    <button
      type="button"
      onClick={onClick}
      title={label}
      aria-label={label}
      className={`rounded-btn p-1.5 transition-colors ${
        danger
          ? 'text-ink-mute hover:bg-danger/12 hover:text-danger'
          : 'text-ink-mute hover:bg-white/6 hover:text-ink-body'
      }`}
    >
      <Icon size={15} />
    </button>
  );
}

// 테이블 헤더 셀
function Th({ children, right }) {
  return (
    <th
      className={`whitespace-nowrap px-5 py-3 text-[11px] font-semibold uppercase tracking-wider text-ink-faint ${
        right ? 'text-right' : 'text-left'
      }`}
    >
      {children}
    </th>
  );
}
