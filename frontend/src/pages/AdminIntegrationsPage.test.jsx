import { beforeEach, describe, expect, it, vi } from 'vitest';
import { fireEvent, render, screen, within } from '@testing-library/react';
import AdminIntegrationsPage from './AdminIntegrationsPage.jsx';
import {
  useIntegrations,
  useSendTestMail,
  useUpdateIntegration,
  useVerifyHolidayKey,
} from '../hooks/useIntegrations.js';

vi.mock('react-hot-toast', () => ({
  default: { success: vi.fn(), error: vi.fn() },
}));

vi.mock('../hooks/useIntegrations.js', () => ({
  useIntegrations: vi.fn(),
  useSendTestMail: vi.fn(),
  useUpdateIntegration: vi.fn(),
  useVerifyHolidayKey: vi.fn(),
}));

const updateMutate = vi.fn();
const testMailMutate = vi.fn();
const verifyMutate = vi.fn();

const INTEGRATIONS = {
  mail: {
    provider: 'SMTP',
    username: 'mlsoft.noreply@gmail.com',
    maskedSecret: '••••••••••••mnop',
    active: true,
  },
  holiday: {
    provider: 'DATA_GO_KR',
    maskedKey: '••••••••abcd',
    active: true,
  },
  encryptionConfigured: true,
};

// 성공 콜백까지 태우는 뮤테이션 목 — 제출 후 입력을 비우는 부분이 onSuccess에 있다
function mutation(mutate, result) {
  return {
    mutate: mutate.mockImplementation((variables, options) => options?.onSuccess?.(result)),
    isPending: false,
  };
}

// Card는 <section> + <h2>라 제목으로 카드 범위를 잡는다 (두 카드에 같은 이름의 버튼이 있다)
function card(title) {
  return screen.getByRole('heading', { name: title }).closest('section');
}

function renderPage(data = INTEGRATIONS) {
  useIntegrations.mockReturnValue({
    data,
    isLoading: false,
    isError: false,
    refetch: vi.fn(),
  });
  return render(<AdminIntegrationsPage />);
}

beforeEach(() => {
  vi.clearAllMocks();

  useUpdateIntegration.mockReturnValue(mutation(updateMutate, null));
  useSendTestMail.mockReturnValue(mutation(testMailMutate, null));
  useVerifyHolidayKey.mockReturnValue(mutation(verifyMutate, { valid: true, count: 22 }));
});

describe('AdminIntegrationsPage 자격 증명 표시', () => {
  it('저장된 값은 마스킹된 상태로만 보여준다', () => {
    renderPage();

    expect(screen.getByText('mlsoft.noreply@gmail.com')).toBeInTheDocument();
    expect(screen.getByText('••••••••••••mnop')).toBeInTheDocument();
    expect(screen.getByText('••••••••abcd')).toBeInTheDocument();
    expect(screen.getAllByText('사용 중')).toHaveLength(2);
  });

  it('저장된 값이 없으면 미설정으로 표시한다', () => {
    renderPage({ mail: null, holiday: null, encryptionConfigured: true });

    // 계정·비밀값·키·제공자 4칸 + 상태 배지 2개
    expect(screen.getAllByText('미설정').length).toBeGreaterThanOrEqual(4);
  });

  it('입력값을 비밀번호 입력으로 받아 화면에 드러내지 않는다', () => {
    renderPage();

    expect(screen.getByLabelText('앱 비밀번호')).toHaveAttribute('type', 'password');
    expect(screen.getByLabelText('공휴일 API 키')).toHaveAttribute('type', 'password');
  });
});

describe('AdminIntegrationsPage 메일 계정', () => {
  it('계정과 앱 비밀번호를 저장하고 제출 후 입력을 비운다', () => {
    renderPage();

    const username = screen.getByLabelText('메일 계정');
    const secret = screen.getByLabelText('앱 비밀번호');
    fireEvent.change(username, { target: { value: 'new.sender@gmail.com' } });
    fireEvent.change(secret, { target: { value: 'abcd efgh ijkl mnop' } });

    fireEvent.click(within(card('메일 발신 계정')).getByRole('button', { name: '저장' }));

    expect(updateMutate).toHaveBeenCalledWith(
      { provider: 'mail', username: 'new.sender@gmail.com', secret: 'abcd efgh ijkl mnop' },
      expect.anything(),
    );
    // 제출 즉시 비운다 — 앱 비밀번호가 화면에 남아 있을 이유가 없다
    expect(username.value).toBe('');
    expect(secret.value).toBe('');
  });

  it('계정이나 비밀번호가 비면 저장을 막는다', () => {
    renderPage();

    fireEvent.change(screen.getByLabelText('메일 계정'), {
      target: { value: 'only.username@gmail.com' },
    });

    expect(within(card('메일 발신 계정')).getByRole('button', { name: '저장' })).toBeDisabled();
  });

  it('테스트 메일은 수신자를 받지 않고 호출한다', () => {
    renderPage();

    fireEvent.click(screen.getByRole('button', { name: '테스트 메일 보내기' }));

    expect(testMailMutate).toHaveBeenCalledWith(undefined, expect.anything());
  });

  it('앱 비밀번호 발급 절차를 안내한다', () => {
    renderPage();

    expect(screen.getByText('Google 앱 비밀번호 발급 절차')).toBeInTheDocument();
    expect(screen.getByText(/2단계 인증을 먼저 켭니다/)).toBeInTheDocument();
  });
});

describe('AdminIntegrationsPage 공휴일 키', () => {
  it('입력값이 없으면 저장된 키로 검증하고 결과를 보여준다', () => {
    renderPage();

    fireEvent.click(screen.getByRole('button', { name: '키 검증' }));

    expect(verifyMutate).toHaveBeenCalledWith({}, expect.anything());
    expect(screen.getByText(/검증 성공 — 올해 공휴일 22건/)).toBeInTheDocument();
  });

  it('입력값이 있으면 그 키로 검증한다', () => {
    renderPage();

    fireEvent.change(screen.getByLabelText('공휴일 API 키'), {
      target: { value: 'test-service-key' },
    });
    fireEvent.click(screen.getByRole('button', { name: '키 검증' }));

    expect(verifyMutate).toHaveBeenCalledWith({ apiKey: 'test-service-key' }, expect.anything());
  });

  it('검증에 실패하면 실패 안내를 보여준다', () => {
    useVerifyHolidayKey.mockReturnValue(mutation(verifyMutate, { valid: false, count: 0 }));
    renderPage();

    fireEvent.click(screen.getByRole('button', { name: '키 검증' }));

    expect(screen.getByText(/검증 실패/)).toBeInTheDocument();
  });

  it('키를 저장하면 입력을 비운다', () => {
    renderPage();

    const input = screen.getByLabelText('공휴일 API 키');
    fireEvent.change(input, { target: { value: 'new-service-key' } });
    fireEvent.click(within(card('공휴일 API 키')).getByRole('button', { name: '저장' }));

    expect(updateMutate).toHaveBeenCalledWith(
      { provider: 'holiday', apiKey: 'new-service-key' },
      expect.anything(),
    );
    expect(input.value).toBe('');
  });
});

describe('AdminIntegrationsPage 암호화 키 미설정', () => {
  it('두 카드에 경고를 띄우고 저장을 막는다', () => {
    renderPage({ ...INTEGRATIONS, encryptionConfigured: false });

    expect(screen.getAllByText(/암호화 키가 설정되지 않아/)).toHaveLength(2);

    fireEvent.change(screen.getByLabelText('메일 계정'), { target: { value: 'a@b.com' } });
    fireEvent.change(screen.getByLabelText('앱 비밀번호'), { target: { value: 'secret' } });
    fireEvent.change(screen.getByLabelText('공휴일 API 키'), { target: { value: 'key' } });

    expect(within(card('메일 발신 계정')).getByRole('button', { name: '저장' })).toBeDisabled();
    expect(within(card('공휴일 API 키')).getByRole('button', { name: '저장' })).toBeDisabled();
  });

  it('저장은 막아도 테스트 발송·키 검증은 막지 않는다', () => {
    renderPage({ ...INTEGRATIONS, encryptionConfigured: false });

    expect(screen.getByRole('button', { name: '테스트 메일 보내기' })).toBeEnabled();
    expect(screen.getByRole('button', { name: '키 검증' })).toBeEnabled();
  });
});
