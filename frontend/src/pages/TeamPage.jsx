import { useMemo, useState } from 'react';
import { Building2, Users, Crown, CalendarDays, Clock3 } from 'lucide-react';
import { ROLE, ROLE_LABEL } from '../constants/roles.js';
import PageHeader from '../components/ui/PageHeader.jsx';
import Card from '../components/ui/Card.jsx';
import Stat from '../components/ui/Stat.jsx';
import TableCard from '../components/ui/TableCard.jsx';
import Table, { THead, Th, TR, Td } from '../components/ui/Table.jsx';
import Avatar from '../components/ui/Avatar.jsx';
import StatusBadge from '../components/ui/StatusBadge.jsx';
import EmptyState from '../components/ui/EmptyState.jsx';
import LeaveHeatmap from '../components/LeaveHeatmap.jsx';
import { useCurrentUser } from '../hooks/useAuth.js';
import { useHolidays } from '../hooks/useHolidays.js';
import { useTeamMembers } from '../hooks/useUsers.js';
import { useTeamAnnualUsage, useTeamLeaves } from '../hooks/useLeaves.js';
import { useDepartments } from '../hooks/useDepartments.js';

// 역할별 배지 톤 (총관리자·팀장은 강조 accent, 사원은 muted)
const ROLE_TONE = {
  [ROLE.SYSTEM_ADMIN]: 'accent',
  [ROLE.TEAM_LEADER]: 'accent',
  [ROLE.EMPLOYEE]: 'muted',
};

// 팀 정보 — 내 부서 팀원 목록 + 이번 달 팀 연차 현황 (docs/05 §③)
// UserSummaryResponse(팀원 목록)는 개인정보 보호를 위해 잔여/기본 연차를 내려주지 않는다 — 의도된
// 백엔드 설계라 다른 API로 우회 보강하지 않고, 명단은 이름·직책·역할 중심으로 구성한다.
export default function TeamPage() {
  const { data: me } = useCurrentUser();
  const [heatmapYear, setHeatmapYear] = useState(new Date().getFullYear());
  const teamMembersQuery = useTeamMembers();
  const teamLeavesQuery = useTeamLeaves();
  const teamAnnualUsageQuery = useTeamAnnualUsage(heatmapYear);
  const holidaysQuery = useHolidays(heatmapYear);
  const departmentsQuery = useDepartments();

  const teamMembers = teamMembersQuery.data ?? [];
  // useMemo로 감싸 참조를 안정화 — 아래 소진 현황 useMemo의 불필요한 재계산 경고를 피한다
  // (DashboardPage의 calendarLeaves와 동일한 패턴).
  const teamLeaves = useMemo(() => teamLeavesQuery.data ?? [], [teamLeavesQuery.data]);

  // 내 부서 상세(설명·팀장) — 요약 카드용. useCurrentUser에는 부서 설명이 없어 전체 목록에서 찾는다.
  const dept = useMemo(
    () => departmentsQuery.data?.find((d) => d.id === me?.departmentId) ?? null,
    [departmentsQuery.data, me?.departmentId],
  );

  // 이번 달 팀 연차 사용 — 승인 확정 / 대기(신규+소급취소) 선차감을 구분해 합산 (선차감 정책, docs/01)
  const { confirmedDays, pendingDays } = useMemo(() => {
    let confirmed = 0;
    let pending = 0;
    for (const leave of teamLeaves) {
      const amount = Number(leave.days);
      if (leave.status === 'APPROVED') confirmed += amount;
      else pending += amount;
    }
    return { confirmedDays: confirmed, pendingDays: pending };
  }, [teamLeaves]);

  // 부서 미배정 — 팀원·팀 현황 모두 조회 대상이 없어(서버가 빈 배열로 응답) 전용 안내로 대체
  if (me && !me.departmentId) {
    return (
      <div>
        <PageHeader title="팀 정보" subtitle="부서 정보 없음" />
        <Card>
          <EmptyState Icon={Users} label="소속된 부서가 없습니다. 관리자에게 문의해 주세요." />
        </Card>
      </div>
    );
  }

  return (
    <div>
      <PageHeader title="팀 정보" subtitle={dept?.name ?? me?.departmentName} />

      {/* 부서 요약 카드 */}
      <Card className="mb-5">
        <div className="flex flex-wrap items-center justify-between gap-6">
          <div className="flex items-center gap-4">
            <span className="flex h-12 w-12 items-center justify-center rounded-btn bg-accent/12 text-accent-light">
              <Building2 size={22} />
            </span>
            <div>
              <h2 className="text-[18px] font-bold tracking-[-0.02em] text-ink-hi">
                {dept?.name ?? me?.departmentName}
              </h2>
              {dept?.description && <p className="mt-0.5 text-[13px] text-ink-mute">{dept.description}</p>}
            </div>
          </div>
          <div className="flex flex-wrap items-center gap-6">
            <Stat Icon={Crown} label="팀장" value={dept?.leaderName ?? '미지정'} />
            <Stat Icon={Users} label="인원" value={`${teamMembers.length}명`} />
            <Stat Icon={CalendarDays} label="이번 달 사용(확정)" value={`${confirmedDays}일`} />
            <Stat Icon={Clock3} label="이번 달 대기 중" value={`${pendingDays}일`} />
          </div>
        </div>
      </Card>

      <Card
        className="mb-5"
        title="팀 연차 사용 기록"
        right={
          <select
            aria-label="히트맵 연도"
            value={heatmapYear}
            onChange={(event) => setHeatmapYear(Number(event.target.value))}
            className="rounded-btn border border-white/[0.14] bg-navy-btn2 px-3 py-2 text-[12px] text-ink-body outline-none focus:border-accent"
          >
            {Array.from({ length: 6 }, (_, index) => new Date().getFullYear() - index).map((year) => (
              <option key={year} value={year}>{year}년</option>
            ))}
          </select>
        }
      >
        <LeaveHeatmap
          year={heatmapYear}
          mode="team"
          entries={teamAnnualUsageQuery.data ?? []}
          holidays={holidaysQuery.data ?? []}
          loading={teamAnnualUsageQuery.isLoading}
          error={teamAnnualUsageQuery.isError}
          onRetry={teamAnnualUsageQuery.refetch}
        />
      </Card>

      {/* 팀원 목록 */}
      <TableCard
        title="팀원"
        right={<span className="text-[12px] font-medium text-ink-mute">{teamMembers.length}명</span>}
        loading={teamMembersQuery.isLoading}
        error={teamMembersQuery.isError}
        errorLabel="팀원 목록을 불러오지 못했습니다."
        onRetry={teamMembersQuery.refetch}
        empty={!teamMembersQuery.isLoading && teamMembers.length === 0}
        emptyLabel="팀원이 없습니다."
      >
        <Table className="min-w-[640px]">
          <THead>
            <Th>팀원</Th>
            <Th>직책</Th>
            <Th>이메일</Th>
            <Th right>역할</Th>
          </THead>
          <tbody>
            {teamMembers.map((member) => {
              const isLeader = dept != null && member.id === dept.leaderId;
              const isMe = member.id === me?.id;
              return (
                <TR key={member.id}>
                  <Td>
                    <div className="flex items-center gap-3">
                      <Avatar name={member.name} />
                      <div className="flex items-center gap-2">
                        <span className="text-[14px] font-semibold text-ink-hi">{member.name}</span>
                        {isMe && <span className="text-[12px] text-accent-light">(나)</span>}
                        {isLeader && (
                          <span className="inline-flex items-center gap-1 rounded-badge bg-accent/16 px-2 py-0.5 text-[11px] font-semibold text-accent-light">
                            <Crown size={11} />
                            팀장
                          </span>
                        )}
                      </div>
                    </div>
                  </Td>
                  <Td className="text-ink-body">{member.position ?? '-'}</Td>
                  <Td className="text-ink-mute">{member.email}</Td>
                  <Td right>
                    <StatusBadge label={ROLE_LABEL[member.role]} tone={ROLE_TONE[member.role]} />
                  </Td>
                </TR>
              );
            })}
          </tbody>
        </Table>
      </TableCard>
    </div>
  );
}
