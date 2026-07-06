import { useMemo } from 'react';
import dayjs from 'dayjs';
import { Building2, Users, Crown, CalendarDays } from 'lucide-react';
import {
  TODAY,
  getCurrentUser,
  departments,
  members,
  calendarLeaves,
} from '../mocks/data.js';
import { ROLE, ROLE_LABEL } from '../constants/roles.js';
import PageHeader from '../components/ui/PageHeader.jsx';
import Card from '../components/ui/Card.jsx';
import StatusBadge from '../components/ui/StatusBadge.jsx';

// 역할별 배지 톤 (총관리자·팀장은 강조 accent, 사원은 muted)
const ROLE_TONE = {
  [ROLE.SYSTEM_ADMIN]: 'accent',
  [ROLE.TEAM_LEADER]: 'accent',
  [ROLE.EMPLOYEE]: 'muted',
};

// 아바타 — 이름 첫 글자를 원형 배경 위에 표시
function Avatar({ name }) {
  return (
    <span className="flex h-9 w-9 shrink-0 items-center justify-center rounded-full bg-navy-avatar text-[13px] font-semibold text-accent-light">
      {name.charAt(0)}
    </span>
  );
}

// 부서 요약 카드의 미니 통계 한 칸 (아이콘 + 라벨 + 값)
function SummaryStat({ Icon, label, value }) {
  return (
    <div className="flex items-center gap-2.5">
      <span className="flex h-9 w-9 items-center justify-center rounded-btn bg-white/5 text-ink-mute">
        <Icon size={16} />
      </span>
      <div className="flex flex-col">
        <span className="text-[11px] font-medium text-ink-mute">{label}</span>
        <span className="text-[14px] font-semibold text-ink-hi">{value}</span>
      </div>
    </div>
  );
}

export default function TeamPage() {
  const me = getCurrentUser();

  // 현재 사용자 부서 찾기 (이름 매칭 실패 시 폴백 없이 null → 빈 상태 렌더)
  const dept = useMemo(
    () => departments.find((d) => d.name === me.departmentName) ?? null,
    [me.departmentName],
  );

  // 해당 부서의 재직 중 팀원 목록
  const teamMembers = useMemo(
    () => (dept ? members.filter((m) => m.departmentId === dept.id && m.isActive) : []),
    [dept],
  );

  // 이번 달 팀 연차 사용 일수 — calendarLeaves 는 날짜별 엔트리(1건 = 1일)라 개수 합이 곧 사용 일수
  const teamLeaveDays = useMemo(() => {
    const thisMonth = dayjs(TODAY).format('YYYY-MM');
    const teamNames = new Set(teamMembers.map((m) => m.name));
    return calendarLeaves.filter(
      (lv) => teamNames.has(lv.personName) && lv.date.startsWith(thisMonth),
    ).length;
  }, [teamMembers]);

  // 부서 매칭 실패 — 잘못된 팀을 보여주는 대신 빈 상태 카드 안내
  if (!dept) {
    return (
      <div>
        <PageHeader title="팀 정보" subtitle="부서 정보 없음" />
        <section className="flex flex-col items-center justify-center gap-3 rounded-card bg-navy-card px-6 py-16 text-center shadow-card">
          <span className="flex h-12 w-12 items-center justify-center rounded-full bg-white/5 text-ink-dim">
            <Users size={22} />
          </span>
          <p className="text-[14px] font-semibold text-ink-hi">부서 정보를 찾을 수 없습니다</p>
          <p className="text-[13px] text-ink-mute">
            소속 부서 &lsquo;{me.departmentName}&rsquo;이(가) 부서 목록에 없습니다. 관리자에게
            문의해 주세요.
          </p>
        </section>
      </div>
    );
  }

  return (
    <div>
      <PageHeader title="팀 정보" subtitle={dept.name} />

      {/* 부서 요약 카드 */}
      <section className="mb-5 rounded-card bg-navy-card p-5 shadow-card">
        <div className="flex flex-wrap items-center justify-between gap-6">
          <div className="flex items-center gap-4">
            <span className="flex h-12 w-12 items-center justify-center rounded-btn bg-accent/12 text-accent-light">
              <Building2 size={22} />
            </span>
            <div>
              <h2 className="text-[18px] font-bold tracking-[-0.02em] text-ink-hi">{dept.name}</h2>
              <p className="mt-0.5 text-[13px] text-ink-mute">{dept.description}</p>
            </div>
          </div>
          <div className="flex flex-wrap items-center gap-6">
            <SummaryStat Icon={Crown} label="팀장" value={dept.leaderName ?? '미지정'} />
            <SummaryStat Icon={Users} label="인원" value={`${dept.memberCount}명`} />
            {/* 엔트리 = 일 단위 데이터이므로 '건'이 아닌 '일'로 표기 */}
            <SummaryStat Icon={CalendarDays} label="이번 달 팀 연차 사용" value={`${teamLeaveDays}일`} />
          </div>
        </div>
      </section>

      {/* 팀원 목록 */}
      <Card
        title="팀원"
        right={<span className="text-[12px] font-medium text-ink-mute">{teamMembers.length}명</span>}
      >
        <div className="overflow-x-auto">
          <table className="w-full min-w-[720px] border-collapse">
            <thead>
              <tr className="border-b border-white/6 text-[12px] font-medium text-ink-mute">
                <th className="px-2 py-2.5 text-left">팀원</th>
                <th className="px-2 py-2.5 text-left">직책</th>
                <th className="px-2 py-2.5 text-left">역할</th>
                <th className="px-2 py-2.5 text-right">잔여 / 기본</th>
                <th className="px-2 py-2.5 text-right">입사일</th>
              </tr>
            </thead>
            <tbody>
              {teamMembers.map((m) => {
                const isLeader = m.id === dept.leaderId;
                return (
                  <tr key={m.id} className="border-b border-white/5 last:border-0">
                    <td className="px-2 py-3">
                      <div className="flex items-center gap-3">
                        <Avatar name={m.name} />
                        <div className="flex items-center gap-2">
                          <span className="text-[14px] font-semibold text-ink-hi">{m.name}</span>
                          {isLeader && (
                            <span className="inline-flex items-center gap-1 rounded-badge bg-accent/16 px-2 py-0.5 text-[11px] font-semibold text-accent-light">
                              <Crown size={11} />팀장
                            </span>
                          )}
                        </div>
                      </div>
                    </td>
                    <td className="px-2 py-3 text-[13px] text-ink-body">{m.position}</td>
                    <td className="px-2 py-3">
                      <StatusBadge label={ROLE_LABEL[m.role]} tone={ROLE_TONE[m.role]} />
                    </td>
                    <td className="px-2 py-3 text-right">
                      <span className="text-[14px] font-semibold text-ink-hi">{m.remainingDays}</span>
                      <span className="text-[13px] text-ink-mute"> / {m.baseDays}일</span>
                    </td>
                    <td className="px-2 py-3 text-right text-[13px] text-ink-body">{m.hireDate}</td>
                  </tr>
                );
              })}
            </tbody>
          </table>
        </div>
      </Card>
    </div>
  );
}
