import { useState } from 'react';
import { useNavigate } from 'react-router-dom';
import toast from 'react-hot-toast';
import { CalendarCheck2, Loader2 } from 'lucide-react';
import { me, submitOnboarding } from '../api/auth.js';

// 오늘 날짜(YYYY-MM-DD, 로컬 기준) — 입사일 max 속성용 (미래 입사일 차단)
const TODAY = (() => {
  const now = new Date();
  const month = String(now.getMonth() + 1).padStart(2, '0');
  const day = String(now.getDate()).padStart(2, '0');
  return `${now.getFullYear()}-${month}-${day}`;
})();

// date input 공통 스타일 (다크 테마 캘린더 아이콘 반전 포함)
const DATE_INPUT_CLASS =
  'w-full rounded-btn border border-white/8 bg-navy-btn2 px-3.5 py-2.5 text-[14px] text-ink-hi outline-none transition-colors focus:border-accent [color-scheme:dark]';

// 온보딩 — 최초 로그인 시 생일·입사일만 입력, 연차는 서버가 자동 계산 (docs/01 §2-1)
export default function OnboardingPage() {
  const navigate = useNavigate();
  const [birthDay, setBirthDay] = useState('');
  const [hireDate, setHireDate] = useState('');
  const [submitting, setSubmitting] = useState(false);

  // 로그인 유저 이름 (환영 문구용, RequireAuth 통과 후 렌더되므로 방어적 파싱만)
  let userName = '';
  try {
    userName = JSON.parse(localStorage.getItem('userInfo'))?.name ?? '';
  } catch {
    userName = '';
  }

  // 제출 — 온보딩 등록 후 me() 재조회로 userInfo 갱신(onboarded=true 반영) → 대시보드
  async function handleSubmit(event) {
    event.preventDefault();
    if (!birthDay || !hireDate) {
      toast.error('생년월일과 입사일을 모두 입력해 주세요.');
      return;
    }

    setSubmitting(true);
    try {
      await submitOnboarding({ birthDay, hireDate });
      const data = await me();
      localStorage.setItem('userInfo', JSON.stringify(data));
      toast.success('온보딩이 완료되었습니다. 환영합니다!');
      navigate('/dashboard', { replace: true });
    } catch {
      // 에러 toast는 api 인터셉터에서 일괄 처리 — 여기선 버튼만 복구
      setSubmitting(false);
    }
  }

  return (
    <div className="flex h-screen items-center justify-center bg-navy-app px-6">
      <div className="w-full max-w-[440px] rounded-card bg-navy-card p-8 shadow-card">
        {/* 환영 헤더 */}
        <div className="mb-7 flex flex-col items-center gap-3 text-center">
          <span className="flex h-12 w-12 items-center justify-center rounded-card bg-accent shadow-btn">
            <CalendarCheck2 size={24} className="text-white" />
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
            className="mt-1 flex w-full items-center justify-center gap-2 rounded-btn bg-accent px-4 py-3 text-[14px] font-semibold text-white shadow-btn transition-colors hover:bg-accent-dark disabled:cursor-not-allowed disabled:opacity-60"
          >
            {submitting && <Loader2 size={16} className="animate-spin" />}
            {submitting ? '등록 중…' : '시작하기'}
          </button>
        </form>
      </div>
    </div>
  );
}
