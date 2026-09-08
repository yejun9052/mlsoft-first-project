import { useState } from 'react';
import { useQueryClient } from '@tanstack/react-query';
import { useNavigate } from 'react-router-dom';
import toast from 'react-hot-toast';
import { CalendarCheck2, Loader2, ShieldCheck } from 'lucide-react';
import { logout, reviseOnboarding, submitOnboarding } from '../api/auth.js';
import GlowShell from '../components/ui/GlowShell.jsx';
import { useCurrentUser } from '../hooks/useAuth.js';

// 승인 대기 상태 — 자동 승인 범위를 벗어난 입사일을 신고한 경우 (리뷰 S-1)
const STATUS_PENDING_APPROVAL = 'PENDING_APPROVAL';

// 오늘 날짜(YYYY-MM-DD, 로컬 기준) — 입사일 max 속성용 (미래 입사일 차단)
const TODAY = (() => {
  const now = new Date();
  const month = String(now.getMonth() + 1).padStart(2, '0');
  const day = String(now.getDate()).padStart(2, '0');
  return `${now.getFullYear()}-${month}-${day}`;
})();

// date/text input 공통 스타일 (다크 테마 캘린더 아이콘 반전 포함)
const INPUT_CLASS =
  'w-full rounded-btn border border-white/[0.15] bg-white/[0.04] px-3.5 py-2.5 text-[14px] text-ink-hi outline-none transition-all placeholder:text-ink-faint focus:border-accent-cyan/60 focus:bg-white/[0.06] focus:ring-2 focus:ring-accent-cyan/15 [color-scheme:dark]';

// 온보딩 — 최초 로그인 시 생일·입사일만 입력, 연차는 서버가 자동 계산 (docs/01 §2-1)
export default function OnboardingPage() {
  const navigate = useNavigate();
  const queryClient = useQueryClient();
  const { data: currentUser } = useCurrentUser();
  const [birthDay, setBirthDay] = useState('');
  const [hireDate, setHireDate] = useState('');
  const [jobGrade, setJobGrade] = useState('');
  const [submitting, setSubmitting] = useState(false);
  const [revising, setRevising] = useState(false);

  const userName = currentUser?.name ?? '';
  const pendingApproval =
    currentUser?.onboardingStatus === STATUS_PENDING_APPROVAL;

  // 응답을 캐시와 저장소에 함께 반영해야 현재 렌더와 새로고침 직후 렌더가 같은 상태를 본다.
  function storeCurrentUser(user) {
    localStorage.setItem('userInfo', JSON.stringify(user));
    queryClient.setQueryData(['auth', 'me'], user);
  }

  function startRevision() {
    setBirthDay(currentUser?.birthDay ?? '');
    setHireDate(currentUser?.hireDate ?? '');
    setRevising(true);
  }

  function cancelRevision() {
    setBirthDay('');
    setHireDate('');
    setRevising(false);
  }

  // 최초 제출과 수정은 같은 입력 검증·폼을 쓰되 서버 메서드만 POST와 PATCH로 구분한다.
  async function handleSubmit(event) {
    event.preventDefault();
    if (!birthDay || !hireDate) {
      toast.error('생년월일과 입사일을 모두 입력해 주세요.');
      return;
    }

    setSubmitting(true);
    try {
      const data = revising
        ? await reviseOnboarding({ birthDay, hireDate })
        : await submitOnboarding({ birthDay, hireDate, jobGrade: jobGrade.trim() || null });

      storeCurrentUser(data);

      if (data.onboardingStatus === STATUS_PENDING_APPROVAL) {
        // 수정했지만 여전히 자동 승인 범위 밖 — 대기 카드에서 대기 카드로 돌아오므로 날짜 한 줄만
        // 달라진다. 최초 제출은 폼→카드로 화면이 통째로 바뀌어 그 자체가 피드백이지만
        // 수정에는 그게 없어서, 토스트가 없으면 제출이 된 건지 알 수 없다.
        if (revising) {
          toast.success('입사일을 수정했습니다. 관리자 확인을 기다려 주세요.');
        }
        setRevising(false);
        setSubmitting(false);
        return;
      }

      // 대기가 아니면 확정이다. COMPLETED로 좁혀 비교하지 않는 이유 — 서버가 예상 밖의 상태를 주면
      // 그 분기가 통째로 빠져 버튼이 '등록 중…'에 멈춘 채 남고, 사용자에게는 무응답으로 보인다.
      toast.success(
        revising
          ? '입사일을 수정했습니다. 온보딩이 완료되었습니다!'
          : '온보딩이 완료되었습니다. 환영합니다!',
      );
      navigate('/dashboard', { replace: true });
    } catch {
      // 에러 toast는 api 인터셉터에서 일괄 처리 — 여기선 버튼만 복구
      setSubmitting(false);
    }
  }

  // 승인 대기 중에도 세션을 끝낼 수 있어야 하므로 로그아웃 경로는 항상 남긴다.
  async function handleLogout() {
    try {
      await logout();
    } finally {
      localStorage.removeItem('userInfo');
      queryClient.removeQueries({ queryKey: ['auth', 'me'] });
      navigate('/login', { replace: true });
    }
  }

  if (pendingApproval && !revising) {
    return (
      <GlowShell>
        <div className="glass glass-edge w-full max-w-[440px] rounded-card border border-white/[0.15] p-8 text-center shadow-card">
          <span className="mx-auto mb-5 flex h-12 w-12 items-center justify-center rounded-card bg-accent/15 ring-1 ring-accent/30">
            <ShieldCheck size={24} className="text-accent" />
          </span>

          <h1 className="mb-3 text-[22px] font-bold tracking-[-0.02em] text-ink-hi">
            관리자 확인을 기다리는 중입니다
          </h1>

          <p className="mb-5 text-[13px] leading-relaxed text-ink-mute">
            입력하신 입사일이 최근 기간을 벗어나 관리자 확인이 필요합니다.
            <br />
            승인되면 근속 기간에 맞는 연차가 부여됩니다.
          </p>

          <dl className="mb-5 grid grid-cols-[88px_1fr] gap-x-3 gap-y-2 rounded-btn border border-white/[0.1] bg-white/[0.04] px-4 py-3 text-left text-[13px]">
            <dt className="text-ink-faint">입사일</dt>
            <dd className="font-medium text-ink-body">
              {currentUser?.hireDate ?? '-'}
            </dd>
            <dt className="text-ink-faint">생년월일</dt>
            <dd className="font-medium text-ink-body">
              {currentUser?.birthDay ?? '-'}
            </dd>
          </dl>

          {currentUser?.onboardingRevisable === true ? (
            <p className="mb-5 text-[12px] leading-relaxed text-ink-faint">
              입력한 정보가 잘못되었다면 승인 전에 한 번 수정할 수 있습니다.
            </p>
          ) : currentUser?.onboardingRevised === true ? (
            <p className="mb-5 text-[12px] leading-relaxed text-ink-faint">
              수정은 1회만 가능하며 이미 사용하셨습니다.
              <br />
              더 고치려면 관리자에게 반려를 요청해 주세요.
            </p>
          ) : (
            <p className="mb-5 text-[12px] leading-relaxed text-ink-faint">
              입사일을 잘못 입력하셨다면 관리자에게 반려를 요청해 주세요.
              반려되면 다시 입력할 수 있습니다.
            </p>
          )}

          <div className="flex flex-col gap-3">
            {currentUser?.onboardingRevisable === true && (
              <button
                type="button"
                onClick={startRevision}
                className="w-full rounded-btn bg-accent px-4 py-3 text-[14px] font-semibold text-navy-app shadow-btn transition-colors hover:bg-accent-light"
              >
                입사일 수정
              </button>
            )}

            <button
              type="button"
              onClick={handleLogout}
              className="w-full rounded-btn border border-white/[0.15] px-4 py-3 text-[14px] font-semibold text-ink-body transition-colors hover:bg-white/[0.06]"
            >
              로그아웃
            </button>
          </div>
        </div>
      </GlowShell>
    );
  }

  return (
    <GlowShell>
      <div className="glass glass-edge w-full max-w-[440px] rounded-card border border-white/[0.15] p-8 shadow-card">
        <div className="mb-7 flex flex-col items-center gap-3 text-center">
          <span className="flex h-12 w-12 items-center justify-center rounded-card bg-accent shadow-btn">
            <CalendarCheck2 size={24} className="text-navy-app" />
          </span>

          <h1 className="text-[22px] font-bold tracking-[-0.02em] text-ink-hi">
            {revising
              ? '입력한 정보를 수정해 주세요'
              : userName
                ? `${userName}님, 환영합니다!`
                : '환영합니다!'}
          </h1>

          <p className="text-[13px] leading-relaxed text-ink-mute">
            {revising
              ? '관리자가 확인하기 전에 생년월일과 입사일을 바로잡을 수 있습니다.'
              : '서비스 이용을 위해 아래 정보를 입력해 주세요.'}
          </p>

          {revising && (
            <p className="w-full rounded-btn border border-accent/30 bg-accent/10 px-3 py-2 text-[12px] font-semibold text-accent">
              수정은 1회만 가능합니다.
            </p>
          )}
        </div>

        <form onSubmit={handleSubmit} className="flex flex-col gap-5">
          <div>
            <label
              htmlFor="birthDay"
              className="mb-1.5 block text-[13px] font-medium text-ink-body"
            >
              생년월일
            </label>
            <input
              id="birthDay"
              type="date"
              value={birthDay}
              max={TODAY}
              onChange={(event) => setBirthDay(event.target.value)}
              required
              className={INPUT_CLASS}
            />
          </div>

          <div>
            <label
              htmlFor="hireDate"
              className="mb-1.5 block text-[13px] font-medium text-ink-body"
            >
              입사일
            </label>
            <input
              id="hireDate"
              type="date"
              value={hireDate}
              max={TODAY}
              onChange={(event) => setHireDate(event.target.value)}
              required
              className={INPUT_CLASS}
            />
            <p className="mt-1.5 text-[11px] text-ink-faint">
              연차는 입사일 기준으로 자동 계산됩니다.
            </p>
          </div>

          {!revising && (
            <div>
              <label
                htmlFor="jobGrade"
                className="mb-1.5 block text-[13px] font-medium text-ink-body"
              >
                직급
              </label>
              <input
                id="jobGrade"
                type="text"
                value={jobGrade}
                maxLength={50}
                placeholder="예: 선임 연구원"
                onChange={(event) => setJobGrade(event.target.value)}
                className={INPUT_CLASS}
              />
              <p className="mt-1.5 text-[11px] text-ink-faint">
                선택 입력입니다. 나중에 내 정보에서 바꿀 수 있습니다.
              </p>
            </div>
          )}

          <button
            type="submit"
            disabled={submitting}
            className="mt-1 flex w-full items-center justify-center gap-2 rounded-btn bg-accent px-4 py-3 text-[14px] font-semibold text-navy-app shadow-btn transition-colors hover:bg-accent-light disabled:cursor-not-allowed disabled:opacity-60"
          >
            {submitting && <Loader2 size={16} className="animate-spin" />}
            {submitting
              ? revising
                ? '수정 중…'
                : '등록 중…'
              : revising
                ? '수정 제출'
                : '시작하기'}
          </button>

          {revising && (
            <button
              type="button"
              onClick={cancelRevision}
              disabled={submitting}
              className="w-full rounded-btn border border-white/[0.15] px-4 py-3 text-[14px] font-semibold text-ink-body transition-colors hover:bg-white/[0.06] disabled:cursor-not-allowed disabled:opacity-60"
            >
              취소
            </button>
          )}
        </form>
      </div>
    </GlowShell>
  );
}
