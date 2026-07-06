import { useState } from 'react';
import toast from 'react-hot-toast';
import { Mail, Building2, CalendarDays, Cake, Phone } from 'lucide-react';
import PageHeader from '../components/ui/PageHeader.jsx';
import Card from '../components/ui/Card.jsx';
import StatusBadge from '../components/ui/StatusBadge.jsx';
import { getCurrentUser, leaveSummary } from '../mocks/data.js';
import { ROLE_LABEL } from '../constants/roles.js';

// 텍스트 입력 공통 스타일 (OnboardingPage 폼 스타일 기준)
const TEXT_INPUT_CLASS =
  'w-full rounded-btn border border-white/8 bg-navy-btn2 px-3.5 py-2.5 text-[14px] text-ink-hi outline-none transition-colors focus:border-accent';
// 날짜 입력 — 다크 테마 캘린더 아이콘 반전 포함
const DATE_INPUT_CLASS = `${TEXT_INPUT_CLASS} [color-scheme:dark]`;

// 소수 첫째자리 반올림 (연차 0.5일 단위) — 확정 사용일 계산용
function round1(n) {
  return Math.round(n * 10) / 10;
}

// 프로필 정보 행 — 아이콘 + 라벨(ink-mute) + 값(ink-body)
function InfoRow({ Icon, label, value }) {
  return (
    <div className="flex items-center gap-3 py-2.5">
      <span className="flex h-8 w-8 shrink-0 items-center justify-center rounded-btn bg-navy-btn2 text-ink-mute">
        <Icon size={15} />
      </span>
      <span className="text-[12px] text-ink-mute">{label}</span>
      <span className="ml-auto truncate text-[13px] font-medium text-ink-body">{value}</span>
    </div>
  );
}

// 연차 요약 행 — 라벨(ink-mute) + 값(우측정렬, 강조/톤 옵션)
function SummaryRow({ label, value, unit = '일', tone, hero }) {
  return (
    <div className="flex items-center justify-between gap-4 py-2.5">
      <span className="text-[12px] text-ink-mute">{label}</span>
      <span
        className={
          hero
            ? 'text-[22px] font-extrabold leading-none tracking-[-0.02em] text-accent-light'
            : `text-[15px] font-semibold ${tone ?? 'text-ink-body'}`
        }
      >
        {value}
        {unit && <span className="ml-0.5 text-[12px] font-medium text-ink-mute">{unit}</span>}
      </span>
    </div>
  );
}

// 내 정보 — 프로필 / 연차 요약 / 정보 수정 (2컬럼)
export default function MyInfoPage() {
  const me = getCurrentUser();
  // 정보 수정 폼 (mock — 저장 시 toast 안내만)
  const [name, setName] = useState(me.name);
  const [birthDay, setBirthDay] = useState(me.birthDay);

  // 선차감 정책: usedDays 는 대기 중(선차감)을 포함 → 확정 사용 = 사용 − 대기
  const confirmedUsed = round1(leaveSummary.usedDays - leaveSummary.pendingDays);

  // 저장 (데모)
  function handleSave(event) {
    event.preventDefault();
    toast.success('저장되었습니다. (데모)');
  }

  return (
    <div>
      <PageHeader title="내 정보" />

      <div className="grid grid-cols-1 gap-5 lg:grid-cols-[1fr_1.2fr]">
        {/* 좌: 프로필 카드 */}
        <div className="rounded-card bg-navy-card p-6 shadow-card">
          <div className="flex flex-col items-center gap-3 border-b border-white/6 pb-6 text-center">
            <span className="flex h-16 w-16 items-center justify-center rounded-full bg-navy-avatar text-[26px] font-bold text-accent-label">
              {me.name.charAt(0)}
            </span>
            <div>
              <h2 className="text-[18px] font-bold text-ink-hi">{me.name}</h2>
              <p className="mt-0.5 text-[12px] text-ink-mute">{me.position}</p>
            </div>
            <StatusBadge label={ROLE_LABEL[me.role]} tone="accent" />
          </div>

          <div className="mt-2 divide-y divide-white/5">
            <InfoRow Icon={Mail} label="이메일" value={me.email} />
            <InfoRow Icon={Building2} label="부서·직책" value={`${me.departmentName} · ${me.position}`} />
            <InfoRow Icon={CalendarDays} label="입사일" value={me.hireDate} />
            <InfoRow Icon={Cake} label="생년월일" value={me.birthDay} />
            <InfoRow Icon={Phone} label="전화" value={me.phone} />
          </div>
        </div>

        {/* 우: 연차 요약 + 정보 수정 */}
        <div className="flex flex-col gap-5">
          {/* 연차 요약 카드 */}
          <Card title="연차 요약">
            <div className="divide-y divide-white/5">
              <SummaryRow label="기본 부여" value={leaveSummary.baseDays} />
              <SummaryRow label="복리 가산" value={leaveSummary.bonusDays} />
              <SummaryRow label="사용 (확정)" value={confirmedUsed} />
              <SummaryRow label="대기 중" value={leaveSummary.pendingDays} tone="text-ink-mute" />
              <SummaryRow label="잔여 연차" value={leaveSummary.remainingDays} hero />
              <SummaryRow label="소멸 예정" value={leaveSummary.expiringDays} tone="text-warn" />
              <SummaryRow label="다음 기산일" value={leaveSummary.nextResetDate} unit="" />
            </div>
          </Card>

          {/* 정보 수정 카드 */}
          <Card title="정보 수정">
            <form onSubmit={handleSave} className="flex flex-col gap-4">
              <div>
                <label htmlFor="name" className="mb-1.5 block text-[13px] font-medium text-ink-body">
                  이름
                </label>
                <input
                  id="name"
                  type="text"
                  value={name}
                  onChange={(event) => setName(event.target.value)}
                  className={TEXT_INPUT_CLASS}
                />
              </div>
              <div>
                <label htmlFor="birthDay" className="mb-1.5 block text-[13px] font-medium text-ink-body">
                  생년월일
                </label>
                <input
                  id="birthDay"
                  type="date"
                  value={birthDay}
                  onChange={(event) => setBirthDay(event.target.value)}
                  className={DATE_INPUT_CLASS}
                />
              </div>
              <button
                type="submit"
                className="mt-1 self-start rounded-btn bg-accent px-5 py-2.5 text-[14px] font-semibold text-white shadow-btn transition-colors hover:bg-accent-dark"
              >
                저장
              </button>
            </form>
          </Card>
        </div>
      </div>
    </div>
  );
}
