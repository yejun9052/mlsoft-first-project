import { useEffect, useState } from 'react';
import dayjs from 'dayjs';
import toast from 'react-hot-toast';
import { Mail, Building2, CalendarDays, Cake } from 'lucide-react';
import PageHeader from '../components/ui/PageHeader.jsx';
import Card from '../components/ui/Card.jsx';
import Avatar from '../components/ui/Avatar.jsx';
import StatusBadge from '../components/ui/StatusBadge.jsx';
import Field from '../components/ui/Field.jsx';
import TextInput from '../components/ui/TextInput.jsx';
import Button from '../components/ui/Button.jsx';
import LoadingState from '../components/ui/LoadingState.jsx';
import ErrorState from '../components/ui/ErrorState.jsx';
import ConfirmDialog from '../components/ui/ConfirmDialog.jsx';
import { useUnsavedGuard } from '../hooks/useUnsavedGuard.js';
import { ROLE_LABEL } from '../constants/roles.js';
import { useCurrentUser } from '../hooks/useAuth.js';
import { useLeaveSummary } from '../hooks/useLeaves.js';
import { useUpdateMyProfile } from '../hooks/useUsers.js';

// 프로필 정보 행 — 아이콘 + 라벨(ink-mute) + 값(ink-body)
function InfoRow({ Icon, label, value }) {
  return (
    <div className="flex items-center gap-3 py-2.5">
      <span className="flex h-8 w-8 shrink-0 items-center justify-center rounded-btn border border-white/[0.12] bg-white/[0.04] text-accent-light">
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

// 내 정보 — 프로필 / 연차 요약 / 정보 수정 (2컬럼, docs/05 §④)
// UserMeResponse(GET /api/auth/me)에는 직책·전화번호 필드가 없어(관리자용 UserResponse와 달리 경량 응답)
// 해당 항목은 화면에서 노출하지 않는다 — 없는 데이터를 다른 API로 우회 보강하지 않는다.
export default function MyInfoPage() {
  const meQuery = useCurrentUser();
  const summaryQuery = useLeaveSummary();
  const { data: me } = meQuery;
  const { data: summary } = summaryQuery;
  const updateProfileMutation = useUpdateMyProfile();

  // 수정 폼 — useCurrentUser는 localStorage initialData 덕에 첫 렌더부터 값이 채워져 있다.
  const [name, setName] = useState(me?.name ?? '');
  const [birthDay, setBirthDay] = useState(me?.birthDay ?? '');

  // 서버가 준 현재 값. 폼의 기준선이자 "바뀌었는가"의 비교 대상이다
  const savedName = me?.name ?? '';
  const savedBirthDay = me?.birthDay ?? '';

  // 서버 값이 바뀌면 폼을 다시 맞춘다. useState 초기값은 **첫 렌더에 한 번**만 쓰이는데,
  // 위 initialData는 localStorage라 낡아 있을 수 있고(initialDataUpdatedAt: 0이라 매번 재검증한다)
  // 아예 없으면 undefined다. 그때 폼은 빈 칸이나 옛 이름을 그대로 들고 있게 되고,
  // 저장하려 하면 "이름과 생년월일을 모두 입력해 주세요"가 뜬다. 저장 성공 뒤 재조회에서도
  // 이 효과가 기준선을 새 값으로 옮겨 아래 dirty가 풀린다.
  useEffect(() => {
    setName(savedName);
    setBirthDay(savedBirthDay);
  }, [savedName, savedBirthDay]);

  // 저장하지 않고 나가려 할 때 붙잡는다 (연차 정책 화면과 같은 장치).
  // 저장 버튼이 입력칸 바로 아래라 그쪽 같은 "미저장 배지"는 두지 않았다 —
  // 배지는 멀리 있는 버튼을 찾게 하려는 것이고, 여기서는 눈에 이미 들어와 있다.
  const unsavedGuard = useUnsavedGuard(name !== savedName || birthDay !== savedBirthDay);

  // 실패를 로딩과 구분한다 — !summary 가드만 있으면 실패 시 스피너가 영원히 돈다 (리뷰 F-6)
  if (meQuery.isError || summaryQuery.isError) {
    return (
      <ErrorState
        label="내 정보를 불러오지 못했습니다."
        onRetry={() => {
          meQuery.refetch();
          summaryQuery.refetch();
        }}
      />
    );
  }

  if (!me || !summary) {
    return <LoadingState className="h-full" />;
  }

  // 선차감 정책: useDays는 대기 중(선차감) 포함 → 확정 사용 = 사용 − 대기
  const confirmedUsed = Number(summary.useDays) - Number(summary.pendingDays);

  function handleSave(event) {
    event.preventDefault();
    if (!name.trim() || !birthDay) {
      toast.error('이름과 생년월일을 모두 입력해 주세요.');
      return;
    }
    updateProfileMutation.mutate(
      { name: name.trim(), birthDay },
      { onSuccess: () => toast.success('저장되었습니다.') },
    );
  }

  return (
    <div>
      <PageHeader title="내 정보" />

      <div className="grid grid-cols-1 gap-5 lg:grid-cols-[1fr_1.2fr]">
        {/* 좌: 프로필 카드 */}
        <Card>
          <div className="flex flex-col items-center gap-3 border-b border-white/[0.12] pb-6 text-center">
            <Avatar name={me.name} size="lg" />
            <div>
              <h2 className="text-[18px] font-bold text-ink-hi">{me.name}</h2>
              <p className="mt-0.5 text-[12px] text-ink-mute">{me.departmentName ?? '부서 미배정'}</p>
            </div>
            <StatusBadge label={ROLE_LABEL[me.role]} tone="accent" />
          </div>

          <div className="mt-2 divide-y divide-white/[0.10]">
            <InfoRow Icon={Mail} label="이메일" value={me.email} />
            <InfoRow Icon={Building2} label="부서" value={me.departmentName ?? '미배정'} />
            <InfoRow Icon={CalendarDays} label="입사일" value={me.hireDate} />
            <InfoRow Icon={Cake} label="생년월일" value={me.birthDay} />
          </div>
        </Card>

        {/* 우: 연차 요약 + 정보 수정 */}
        <div className="flex flex-col gap-5">
          {/* 연차 요약 카드 */}
          <Card title="연차 요약">
            <div className="divide-y divide-white/[0.10]">
              <SummaryRow label="기본 부여" value={summary.baseDays} />
              <SummaryRow label="복리 가산" value={summary.bonusDays} />
              <SummaryRow label="사용 (확정)" value={confirmedUsed} />
              <SummaryRow label="대기 중" value={summary.pendingDays} tone="text-ink-mute" />
              <SummaryRow label="잔여 연차" value={summary.remainingDays} hero />
              {/* 미사용 이월 없이 소멸되는 정책이라 소멸 예정 = 잔여와 동일 (백엔드에 별도 필드 없음) */}
              <SummaryRow label="소멸 예정" value={summary.remainingDays} tone="text-warn" />
              <SummaryRow
                label="다음 기산일"
                value={summary.nextResetDate ? dayjs(summary.nextResetDate).format('YYYY.MM.DD') : '-'}
                unit=""
              />
            </div>
          </Card>

          {/* 정보 수정 카드 */}
          <Card title="정보 수정">
            <form onSubmit={handleSave} className="flex flex-col gap-4">
              <Field label="이름">
                <TextInput
                  type="text"
                  value={name}
                  onChange={(e) => setName(e.target.value)}
                />
              </Field>
              <Field label="생년월일">
                <TextInput
                  type="date"
                  value={birthDay}
                  onChange={(e) => setBirthDay(e.target.value)}
                />
              </Field>
              <Button type="submit" className="mt-1 self-start" loading={updateProfileMutation.isPending}>
                저장
              </Button>
            </form>
          </Card>
        </div>
      </div>

      <ConfirmDialog
        open={unsavedGuard.blocked}
        title="저장하지 않고 나가시겠습니까?"
        message="이름·생년월일을 바꿨지만 아직 저장하지 않았습니다. '정보 수정' 카드의 '저장' 버튼을 눌러야 반영됩니다. 지금 나가면 바꾼 값은 사라집니다."
        tone="danger"
        confirmLabel="그냥 나가기"
        cancelLabel="취소"
        onConfirm={unsavedGuard.leave}
        onCancel={unsavedGuard.stay}
      />
    </div>
  );
}
