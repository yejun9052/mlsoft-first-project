import { useState } from 'react';
import toast from 'react-hot-toast';
import { AlertTriangle } from 'lucide-react';
import PageHeader from '../components/ui/PageHeader.jsx';
import Card from '../components/ui/Card.jsx';
import Button from '../components/ui/Button.jsx';
import Field from '../components/ui/Field.jsx';
import TextInput from '../components/ui/TextInput.jsx';
import StatusBadge from '../components/ui/StatusBadge.jsx';
import LoadingState from '../components/ui/LoadingState.jsx';
import ErrorState from '../components/ui/ErrorState.jsx';
import {
  useIntegrations,
  useSendTestMail,
  useUpdateIntegration,
  useVerifyHolidayKey,
} from '../hooks/useIntegrations.js';

// 외부 연동 설정 — 메일 발신 계정과 공휴일 API 키를 한 화면에서 관리한다.
//
// **이 화면은 비밀값을 절대 되돌려 받지 않는다.** 서버 응답에는 마스킹된 값만 오고, 입력한 값은
// 제출 즉시 지운다. 관리자 화면을 여는 것만으로 자격 증명이 새는 경로를 만들지 않기 위해서다
// (docs/12 B-4 별항). 저장은 항상 "덮어쓰기"이고 현재 값을 보여 주는 수정 폼이 아니다.
export default function AdminIntegrationsPage() {
  const integrationsQuery = useIntegrations();

  if (integrationsQuery.isLoading) {
    return (
      <div>
        <IntegrationsHeader />
        <Card padding="none">
          <LoadingState />
        </Card>
      </div>
    );
  }

  if (integrationsQuery.isError) {
    return (
      <div>
        <IntegrationsHeader />
        <Card padding="none">
          <ErrorState
            label="연동 설정을 불러오지 못했습니다."
            onRetry={integrationsQuery.refetch}
          />
        </Card>
      </div>
    );
  }

  const { mail, holiday, encryptionConfigured } = integrationsQuery.data ?? {};

  return (
    <div>
      <IntegrationsHeader />

      <div className="grid gap-5 lg:grid-cols-2">
        <MailCard credential={mail} encryptionConfigured={Boolean(encryptionConfigured)} />
        <HolidayCard credential={holiday} encryptionConfigured={Boolean(encryptionConfigured)} />
      </div>
    </div>
  );
}

function IntegrationsHeader() {
  return (
    <PageHeader
      title="외부 연동 설정"
      subtitle="메일 발신 계정과 공휴일 API 키를 저장·회전한다. 저장된 값은 마스킹해서만 보여준다"
    />
  );
}

// 암호화 키(APP_CREDENTIAL_ENCRYPTION_KEY)가 없으면 서버가 자격 증명을 저장할 수 없다.
// 저장을 눌러 실패를 보게 두는 대신 이유를 먼저 알리고 버튼을 막는다.
function EncryptionWarning() {
  return (
    <div className="mb-4 flex items-start gap-2 rounded-btn border border-warn/35 bg-warn/[0.08] px-3 py-2.5">
      <AlertTriangle size={15} className="mt-0.5 shrink-0 text-warn" />
      <p className="text-[12px] text-warn">
        암호화 키가 설정되지 않아 자격 증명을 저장할 수 없습니다. 서버에
        <code className="mx-1">APP_CREDENTIAL_ENCRYPTION_KEY</code>
        환경변수를 먼저 주입해 주세요.
      </p>
    </div>
  );
}

// 저장된 자격 증명 요약 — 값이 없으면 "미설정"
function CredentialSummary({ rows, active }) {
  return (
    <dl className="grid grid-cols-[100px_1fr] items-center gap-x-3 gap-y-2 text-[13px]">
      {rows.map((row) => (
        <div key={row.label} className="contents">
          <dt className="text-ink-faint">{row.label}</dt>
          <dd className="min-w-0 truncate text-ink-body tabular-nums">{row.value || '미설정'}</dd>
        </div>
      ))}
      <dt className="text-ink-faint">상태</dt>
      <dd>
        {active ? (
          <StatusBadge label="사용 중" tone="ok" />
        ) : (
          <StatusBadge label="미설정" tone="muted" />
        )}
      </dd>
    </dl>
  );
}

// ── 메일 발신 계정 ──────────────────────────────────────────────────────
function MailCard({ credential, encryptionConfigured }) {
  const updateMutation = useUpdateIntegration();
  const testMutation = useSendTestMail();

  const [username, setUsername] = useState('');
  const [secret, setSecret] = useState('');

  const canSave = encryptionConfigured && username.trim() !== '' && secret.trim() !== '';

  function save() {
    updateMutation.mutate(
      { provider: 'mail', username: username.trim(), secret: secret.trim() },
      {
        onSuccess: () => {
          // 제출 즉시 비운다 — 화면에 앱 비밀번호가 남아 있을 이유가 없다
          setUsername('');
          setSecret('');
          toast.success('메일 발신 계정을 저장했습니다.');
        },
      },
    );
  }

  function sendTest() {
    testMutation.mutate(undefined, {
      onSuccess: () => toast.success('테스트 메일을 요청했습니다. 본인 메일함을 확인해 주세요.'),
    });
  }

  return (
    <Card title="메일 발신 계정">
      {!encryptionConfigured && <EncryptionWarning />}

      <CredentialSummary
        rows={[
          { label: '계정', value: credential?.username },
          { label: '앱 비밀번호', value: credential?.maskedSecret },
        ]}
        active={Boolean(credential?.active)}
      />

      <div className="mt-5 border-t border-white/[0.12] pt-5">
        <Field label="계정 (Gmail 주소)" required>
          <TextInput
            aria-label="메일 계정"
            autoComplete="off"
            value={username}
            onChange={(event) => setUsername(event.target.value)}
            placeholder="예: mlsoft.noreply@gmail.com"
          />
        </Field>

        <Field
          label="앱 비밀번호"
          required
          className="mt-4"
          hint="저장하면 화면에서 다시 볼 수 없습니다. 분실하면 새로 발급해 교체하세요."
        >
          <TextInput
            type="password"
            aria-label="앱 비밀번호"
            autoComplete="new-password"
            value={secret}
            onChange={(event) => setSecret(event.target.value)}
            placeholder="16자리 앱 비밀번호"
          />
        </Field>

        <div className="mt-5 flex flex-wrap items-center gap-2">
          <Button disabled={!canSave} loading={updateMutation.isPending} onClick={save}>
            저장
          </Button>
          <Button variant="secondary" loading={testMutation.isPending} onClick={sendTest}>
            테스트 메일 보내기
          </Button>
        </div>
        <p className="mt-2 text-[11px] text-ink-faint">테스트 메일은 로그인한 본인에게 갑니다.</p>
      </div>

      {/* 사용자가 "키값 같은 거 안내해서"라고 했던 부분 — 계정 비밀번호로는 인증되지 않는다 */}
      <div className="mt-5 rounded-btn border border-white/[0.12] bg-white/[0.03] px-3 py-3">
        <p className="text-[12px] font-semibold text-ink-body">Google 앱 비밀번호 발급 절차</p>
        <ol className="mt-2 list-decimal space-y-1 pl-4 text-[11px] text-ink-mute">
          <li>Google 계정 &gt; 보안에서 2단계 인증을 먼저 켭니다. 켜야만 앱 비밀번호가 보입니다.</li>
          <li>보안 &gt; 앱 비밀번호에서 새 이름을 만들고 16자리 값을 발급받습니다.</li>
          <li>발급된 16자리를 위 입력란에 붙여 넣습니다. 계정 로그인 비밀번호로는 인증되지 않습니다.</li>
        </ol>
      </div>
    </Card>
  );
}

// ── 공휴일 API 키 ───────────────────────────────────────────────────────
function HolidayCard({ credential, encryptionConfigured }) {
  const updateMutation = useUpdateIntegration();
  const verifyMutation = useVerifyHolidayKey();

  const [apiKey, setApiKey] = useState('');
  const [verifyResult, setVerifyResult] = useState(null);

  const canSave = encryptionConfigured && apiKey.trim() !== '';

  function save() {
    updateMutation.mutate(
      { provider: 'holiday', apiKey: apiKey.trim() },
      {
        onSuccess: () => {
          setApiKey('');
          setVerifyResult(null);
          toast.success('공휴일 API 키를 저장했습니다.');
        },
      },
    );
  }

  // 입력값이 있으면 그 키로, 없으면 저장된 키로 검증한다 — 저장 전에 확인할 수 있어야 한다
  function verify() {
    const typed = apiKey.trim();
    verifyMutation.mutate(typed === '' ? {} : { apiKey: typed }, { onSuccess: setVerifyResult });
  }

  return (
    <Card title="공휴일 API 키">
      {!encryptionConfigured && <EncryptionWarning />}

      <CredentialSummary
        rows={[
          { label: '제공자', value: credential?.provider },
          { label: 'API 키', value: credential?.maskedKey },
        ]}
        active={Boolean(credential?.active)}
      />

      <div className="mt-5 border-t border-white/[0.12] pt-5">
        <Field
          label="API 키"
          required
          hint="공공데이터포털(data.go.kr) 특일 정보 서비스 키입니다. 비워 두고 검증하면 저장된 키로 확인합니다."
        >
          <TextInput
            type="password"
            aria-label="공휴일 API 키"
            autoComplete="off"
            value={apiKey}
            onChange={(event) => setApiKey(event.target.value)}
            placeholder="발급받은 서비스 키"
          />
        </Field>

        <div className="mt-5 flex flex-wrap items-center gap-2">
          <Button disabled={!canSave} loading={updateMutation.isPending} onClick={save}>
            저장
          </Button>
          <Button variant="secondary" loading={verifyMutation.isPending} onClick={verify}>
            키 검증
          </Button>
        </div>

        {verifyResult && (
          <p className="mt-3 text-[12px]">
            {verifyResult.valid ? (
              <span className="text-ok">
                검증 성공 — 올해 공휴일 {verifyResult.count}건을 받았습니다.
              </span>
            ) : (
              <span className="text-danger">
                검증 실패 — 키가 올바른지, 서비스 신청이 승인됐는지 확인해 주세요.
              </span>
            )}
          </p>
        )}
      </div>
    </Card>
  );
}
