import { beforeEach, describe, expect, it, vi } from 'vitest';
import { fireEvent, render, screen } from '@testing-library/react';
import AdminEmailsPage from './AdminEmailsPage.jsx';
import {
  useEmailHistories,
  useReminderTargets,
  useResendEmail,
  useSendBulkEmail,
} from '../hooks/useEmails.js';
import {
  useEmailTemplates,
  usePreviewEmailTemplate,
  useUpdateEmailTemplate,
} from '../hooks/useEmailTemplates.js';

vi.mock('react-hot-toast', () => ({
  default: { success: vi.fn(), error: vi.fn() },
}));

vi.mock('../hooks/useEmails.js', () => ({
  useEmailHistories: vi.fn(),
  useReminderTargets: vi.fn(),
  useResendEmail: vi.fn(),
  useSendBulkEmail: vi.fn(),
}));

vi.mock('../hooks/useEmailTemplates.js', () => ({
  useEmailTemplates: vi.fn(),
  usePreviewEmailTemplate: vi.fn(),
  useUpdateEmailTemplate: vi.fn(),
}));

const sendMutate = vi.fn();
const resendMutate = vi.fn();
const updateTemplateMutate = vi.fn();
const previewMutate = vi.fn();

const TARGET = {
  userId: 7,
  name: '김연차',
  departmentName: '개발팀',
  remainingDays: 12.5,
  nextResetDate: '2027-03-02',
  daysUntilReset: 28,
  emailAvailable: true,
};

const NO_EMAIL_TARGET = {
  userId: 8,
  name: '이메일없음',
  departmentName: '지원팀',
  remainingDays: 3,
  nextResetDate: '2027-04-01',
  daysUntilReset: 58,
  emailAvailable: false,
};

const TEMPLATE = {
  templateKey: 'LEAVE_BALANCE_REMINDER',
  subjectTemplate: '[연차 소진 안내] {name}님',
  bodyTemplate: '잔여 {remainingDays}일',
  version: 3,
  updatedAt: '2026-09-05T10:00:00',
  updatedByName: '관리자',
  variables: ['{name}', '{remainingDays}', '{nextResetDate}'],
};

const FAILED_HISTORY = {
  id: 91,
  recipientName: '김연차',
  recipientEmailMasked: 'ki****@mlsoft.com',
  type: 'REMINDER',
  status: 'FAILED',
  title: '연차 소진 안내',
  retryCount: 2,
  errorMessage: 'Invalid Addresses',
  sentAt: null,
  createdAt: '2026-09-05T09:10:00',
};

// 성공 콜백까지 태우는 뮤테이션 목 — 화면이 onSuccess에서 상태를 바꾸는 부분을 함께 검증한다
function mutation(mutate, result) {
  return {
    mutate: mutate.mockImplementation((variables, options) => options?.onSuccess?.(result)),
    isPending: false,
  };
}

function query(data) {
  return { data, isLoading: false, isError: false, refetch: vi.fn() };
}

function page(content) {
  return { content, page: { number: 0, size: 10, totalElements: content.length, totalPages: 1 } };
}

function openTab(label) {
  fireEvent.click(screen.getByRole('button', { name: label }));
}

beforeEach(() => {
  vi.clearAllMocks();

  useReminderTargets.mockReturnValue(query([TARGET, NO_EMAIL_TARGET]));
  useEmailHistories.mockReturnValue(query(page([FAILED_HISTORY])));
  useEmailTemplates.mockReturnValue(query([TEMPLATE]));

  useSendBulkEmail.mockReturnValue(
    mutation(sendMutate, { requested: 1, queued: 1, skipped: 0 }),
  );
  useResendEmail.mockReturnValue(mutation(resendMutate, FAILED_HISTORY));
  useUpdateEmailTemplate.mockReturnValue(mutation(updateTemplateMutate, TEMPLATE));
  usePreviewEmailTemplate.mockReturnValue(
    mutation(previewMutate, { subject: '[연차 소진 안내] 김연차님', html: '<p>잔여 12.5일</p>' }),
  );
});

describe('AdminEmailsPage 대상·일괄 발송', () => {
  it('대상 표에 잔여·기산일·발송 가능 여부를 렌더한다', () => {
    render(<AdminEmailsPage />);

    expect(screen.getByText('김연차')).toBeInTheDocument();
    expect(screen.getByText('개발팀')).toBeInTheDocument();
    expect(screen.getByText('12.5일')).toBeInTheDocument();
    expect(screen.getByText('2027-03-02')).toBeInTheDocument();
    expect(screen.getByText('D-28')).toBeInTheDocument();
    // 메일 주소가 없는 사원은 선택 자체가 막혀 있어야 한다
    expect(screen.getByLabelText('이메일없음 선택')).toBeDisabled();
    expect(screen.getByText('메일 없음')).toBeInTheDocument();
  });

  it('선택한 사원과 제목·본문을 그대로 발송에 넘긴다', () => {
    render(<AdminEmailsPage />);

    fireEvent.click(screen.getByLabelText('김연차 선택'));
    fireEvent.change(screen.getByPlaceholderText('예: 연차 소진 안내'), {
      target: { value: '연차를 소진해 주세요' },
    });
    fireEvent.change(screen.getByPlaceholderText('수신자에게 보낼 안내 문구를 적어주세요.'), {
      target: { value: '기산일이 다가옵니다.' },
    });

    fireEvent.click(screen.getByRole('button', { name: '선택 발송' }));

    expect(sendMutate).toHaveBeenCalledWith(
      {
        userIds: [7],
        title: '연차를 소진해 주세요',
        content: '기산일이 다가옵니다.',
      },
      expect.anything(),
    );
    // 결과(요청/큐 적재/건너뜀)를 화면에 남긴다
    expect(screen.getByText('큐 적재')).toBeInTheDocument();
  });

  it('선택이 100명을 넘으면 발송 버튼을 막고 이유를 알린다', () => {
    const many = Array.from({ length: 101 }, (_, index) => ({
      ...TARGET,
      userId: index + 1,
      name: `사원${index + 1}`,
    }));
    useReminderTargets.mockReturnValue(query(many));

    render(<AdminEmailsPage />);

    fireEvent.click(screen.getByLabelText('전체 선택'));
    fireEvent.change(screen.getByPlaceholderText('예: 연차 소진 안내'), {
      target: { value: '안내' },
    });
    fireEvent.change(screen.getByPlaceholderText('수신자에게 보낼 안내 문구를 적어주세요.'), {
      target: { value: '본문' },
    });

    expect(screen.getByRole('button', { name: '선택 발송' })).toBeDisabled();
    expect(screen.getByText(/한 번에 최대 100명까지/)).toBeInTheDocument();
    expect(sendMutate).not.toHaveBeenCalled();
  });
});

describe('AdminEmailsPage 양식', () => {
  it('편집한 제목·본문으로 저장을 호출한다', () => {
    render(<AdminEmailsPage />);
    openTab('양식');

    fireEvent.change(screen.getByLabelText('양식 본문'), {
      target: { value: '잔여 {remainingDays}일이 남았습니다.' },
    });
    fireEvent.click(screen.getByRole('button', { name: '저장' }));

    expect(updateTemplateMutate).toHaveBeenCalledWith(
      {
        templateKey: 'LEAVE_BALANCE_REMINDER',
        subjectTemplate: '[연차 소진 안내] {name}님',
        bodyTemplate: '잔여 {remainingDays}일이 남았습니다.',
      },
      expect.anything(),
    );
  });

  it('미리보기 결과를 sandbox iframe으로 렌더한다', () => {
    render(<AdminEmailsPage />);
    openTab('양식');

    fireEvent.click(screen.getByRole('button', { name: '미리보기' }));

    expect(previewMutate).toHaveBeenCalled();
    expect(screen.getByText('[연차 소진 안내] 김연차님')).toBeInTheDocument();

    const frame = screen.getByTitle('메일 미리보기');
    expect(frame).toHaveAttribute('srcdoc', '<p>잔여 12.5일</p>');
    // 권한을 모두 뺀 sandbox여야 한다 — 본문 HTML이 이 화면에서 실행되면 안 된다
    expect(frame).toHaveAttribute('sandbox', '');
  });

  it('변수 칩을 누르면 본문 커서 위치에 삽입한다', () => {
    render(<AdminEmailsPage />);
    openTab('양식');

    const body = screen.getByLabelText('양식 본문');
    fireEvent.focus(body);
    fireEvent.click(screen.getByRole('button', { name: '{nextResetDate}' }));

    expect(body.value).toContain('{nextResetDate}');
  });
});

describe('AdminEmailsPage 발송 이력', () => {
  it('상태 필터를 서버 조회 파라미터로 넘긴다', () => {
    render(<AdminEmailsPage />);
    openTab('발송 이력');

    fireEvent.click(screen.getByRole('button', { name: '실패' }));

    expect(useEmailHistories).toHaveBeenLastCalledWith(
      expect.objectContaining({ page: 0, size: 10, status: 'FAILED' }),
    );
  });

  it('유형 필터를 서버 조회 파라미터로 넘긴다', () => {
    render(<AdminEmailsPage />);
    openTab('발송 이력');

    fireEvent.click(screen.getByRole('button', { name: '소진 안내' }));

    expect(useEmailHistories).toHaveBeenLastCalledWith(
      expect.objectContaining({ type: 'REMINDER' }),
    );
  });

  it('FAILED 행에서만 재발송을 호출한다', () => {
    render(<AdminEmailsPage />);
    openTab('발송 이력');

    expect(screen.getByText('ki****@mlsoft.com')).toBeInTheDocument();
    fireEvent.click(screen.getByRole('button', { name: '재발송' }));

    expect(resendMutate).toHaveBeenCalledWith(91, expect.anything());
  });

  it('SENT 행에는 재발송 버튼을 두지 않는다', () => {
    useEmailHistories.mockReturnValue(
      query(page([{ ...FAILED_HISTORY, id: 92, status: 'SENT', errorMessage: null }])),
    );

    render(<AdminEmailsPage />);
    openTab('발송 이력');

    expect(screen.queryByRole('button', { name: '재발송' })).not.toBeInTheDocument();
  });
});
