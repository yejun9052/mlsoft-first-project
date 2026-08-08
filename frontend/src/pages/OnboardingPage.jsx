import { useState } from 'react';
import { useNavigate } from 'react-router-dom';
import toast from 'react-hot-toast';
import { CalendarCheck2, Loader2, ShieldCheck } from 'lucide-react';
import { logout, submitOnboarding } from '../api/auth.js';
import GlowShell from '../components/ui/GlowShell.jsx';

// 승인 대기 상태 — 자동 승인 범위를 벗어난 입사일을 신고한 경우 (리뷰 S-1)
const STATUS_PENDING_APPROVAL = 'PENDING_APPROVAL';

// 오늘 날짜(YYYY-MM-DD, 로컬 기준) — 입사일 max 속성용 (미래 입사일 차단)
const TODAY = (() => {
  const now = new Date();
  const month = String(now.getMonth() + 1).padStart(2, '0');
  const day = String(now.getDate()).padStart(2, '0');
  return `${now.getFullYear()}-${month}-${day}`;
})();

// date input 공통 스타일 (다크 테마 캘린더 아이콘 반전 포함)
const DATE_INPUT_CLASS =
  'w-full rounded-btn border border-white/[0.15] bg-white/[0.04] px-3.5 py-2.5 text-[14px] text-ink-hi outline-none transition-all placeholder:text-ink-faint focus:border-accent-cyan/60 focus:bg-white/[0.06] focus:ring-2 focus:ring-accent-cyan/15 [color-scheme:dark]';

// 온보딩 — 최초 로그인 시 생일·입사일만 입력, 연차는 서버가 자동 계산 (docs/01 §2-1)
export default function OnboardingPage() {
  const navigate = useNavigate();
  const [birthDay, setBirthDay] = useState('');
  const [hireDate, setHireDate] = useState('');
  const [submitting, setSubmitting] = useState(false);

  // 로그인 유저 정보 (환영 문구·승인 대기 판별, RequireAuth 통과 후 렌더되므로 방어적 파싱만)
  let storedUser = null;
  try {
    storedUser = JSON.parse(localStorage.getItem('userInfo'));
  } catch {
    storedUser = null;
  }
  const userName = storedUser?.name ?? '';

  // 제출 결과가 승인 대기면 폼 대신 안내를 띄운다. 새로고침해도 유지되도록 localStorage 값도 함께 본다 —
  // 폼을 다시 보여주면 제출할 때마다 ALREADY_ONBOARDED만 맞고 무엇을 해야 할지 알 수 없다 (리뷰 S-1)
  const [pendingApproval, setPendingApproval] = useState(
    storedUser?.onboardingStatus === STATUS_PENDING_APPROVAL,
  );

  // 제출 — 온보딩 응답(UserMeResponse)을 바로 userInfo에 저장 (onboarded=true 반영).
  // me() 재조회를 끼우면 그 호출이 실패했을 때 onboarded=false가 남아 탈출 불가 루프가 됨 (검증 F1)
  async function handleSubmit(event) {
    event.preventDefault();
    if (!birthDay || !hireDate) {
      toast.error('생년월일과 입사일을 모두 입력해 주세요.');
      return;
    }

    setSubmitting(true);
    try {
      const data = await submitOnboarding({ birthDay, hireDate });
      localStorage.setItem('userInfo', JSON.stringify(data));
      if (data.onboardingStatus === STATUS_PENDING_APPROVAL) {
        setSubmitting(false);
        setPendingApproval(true);
        return;
      }
      toast.success('온보딩이 완료되었습니다. 환영합니다!');
      navigate('/dashboard', { replace: true });
    } catch {
      // 에러 toast는 api 인터셉터에서 일괄 처리 — 여기선 버튼만 복구
      setSubmitting(false);
    }
  }

  // 승인 대기 중에는 할 수 있는 일이 로그아웃뿐이라 그 경로만 남긴다
  async function handleLogout() {
    try {
      await logout();
    } finally {
      localStorage.removeItem('userInfo');
      navigate('/login', { replace: true });
    }
  }

  if (pendingApproval) {
    return (
      <GlowShell>
        <div className="glass glass-edge w-full max-w-[440px] rounded-card border border-white/[0.15] p-8 text-center shadow-card">
          <span className="mx-auto mb-5 flex h-12 w-12 items-center justify-center rounded-card bg-accent/15 ring-1 ring-accent/30">
            <ShieldCheck size={24} className="text-accent" />
          </span>
          <h1 className="mb-3 text-[22px] font-bold tracking-[-0.02em] text-ink-hi">
            관리자 확인을 기다리는 중입니다
          </h1>
          <p className="mb-2 text-[13px] leading-relaxed text-ink-mute">
            입력하신 입사일이 최근 기간을 벗어나 관리자 확인이 필요합니다.
            <br />
            승인되면 근속 기간에 맞는 연차가 부여됩니다.
          </p>
          <p className="mb-7 text-[12px] leading-relaxed text-ink-faint">
            입사일을 잘못 입력하셨다면 관리자에게 반려를 요청해 주세요. 반려되면 다시 입력할 수 있습니다.
          </p>
          <button
            type="button"
            onClick={handleLogout}
            className="w-full rounded-btn border border-white/[0.15] px-4 py-3 text-[14px] font-semibold text-ink-body transition-colors hover:bg-white/[0.06]"
          >
            로그아웃
          </button>
        </div>
      </GlowShell>
    );
  }

  return (
    <GlowShell>
      <div className="glass glass-edge w-full max-w-[440px] rounded-card border border-white/[0.15] p-8 shadow-card">
        {/* 환영 헤더 */}
        <div className="mb-7 flex flex-col items-center gap-3 text-center">
          <span className="flex h-12 w-12 items-center justify-center rounded-card bg-accent shadow-btn">
            <CalendarCheck2 size={24} className="text-navy-app" />
          </span>
          <h1 className="text-[22px] font-bold tracking-[-0.02em] text-ink-hi">
            {userName ? `${userName}님, 환영합니다!` : '환영합니다!'}
          </h1>
          <p className="text-[13px] leading-relaxed text-ink-mute">
            서비스 이용을 위해 아래 정보를 입력해 주세요.
          </p>
        </div>

        <form onSubmit={handleSubmit} className="flex flex-col gap-5">
          {/* 생년월일 */}
          <div>
            <label htmlFor="birthDay" className="mb-1.5 block text-[13px] font-medium text-ink-body">
              생년월일
            </label>
            <input
              id="birthDay"
              type="date"
              value={birthDay}
              max={TODAY}
              onChange={(event) => setBirthDay(event.target.value)}
              required
              className={DATE_INPUT_CLASS}
            />
          </div>

          {/* 입사일 — 오늘 이후 선택 불가 */}
          <div>
            <label htmlFor="hireDate" className="mb-1.5 block text-[13px] font-medium text-ink-body">
              입사일
            </label>
            <input
              id="hireDate"
              type="date"
              value={hireDate}
              max={TODAY}
              onChange={(event) => setHireDate(event.target.value)}
              required
              className={DATE_INPUT_CLASS}
            />
            <p className="mt-1.5 text-[11px] text-ink-faint">
              연차는 입사일 기준으로 자동 계산됩니다.
            </p>
          </div>

          {/* 제출 (Primary) */}
          <button
            type="submit"
            disabled={submitting}
            className="mt-1 flex w-full items-center justify-center gap-2 rounded-btn bg-accent px-4 py-3 text-[14px] font-semibold text-navy-app shadow-btn transition-colors hover:bg-accent-light disabled:cursor-not-allowed disabled:opacity-60"
          >
            {submitting && <Loader2 size={16} className="animate-spin" />}
            {submitting ? '등록 중…' : '시작하기'}
          </button>
        </form>
      </div>
    </GlowShell>
  );
}
