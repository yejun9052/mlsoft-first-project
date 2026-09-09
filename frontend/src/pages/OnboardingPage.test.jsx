import { beforeEach, describe, expect, it, vi } from 'vitest';
import {
  fireEvent,
  render,
  screen,
  waitFor,
} from '@testing-library/react';
import {
  QueryClient,
  QueryClientProvider,
} from '@tanstack/react-query';
import {
  MemoryRouter,
  Route,
  Routes,
} from 'react-router-dom';
import OnboardingPage from './OnboardingPage.jsx';
import {
  logout,
  me,
  reviseOnboarding,
  submitOnboarding,
} from '../api/auth.js';

vi.mock('react-hot-toast', () => ({
  default: {
    success: vi.fn(),
    error: vi.fn(),
  },
}));

vi.mock('../api/auth.js', () => ({
  logout: vi.fn(),
  me: vi.fn(),
  reviseOnboarding: vi.fn(),
  submitOnboarding: vi.fn(),
}));

const 대기중사용자 = {
  id: 1,
  name: '김온보딩',
  role: 'EMPLOYEE',
  birthDay: '1995-04-12',
  hireDate: '2020-03-02',
  onboardingStatus: 'PENDING_APPROVAL',
  onboardingRevisable: true,
  onboardingRevised: false,
};

function pendingUser(overrides = {}) {
  return {
    ...대기중사용자,
    ...overrides,
  };
}

const 신입사용자 = {
  id: 2,
  name: '박신입',
  role: 'EMPLOYEE',
  birthDay: null,
  hireDate: null,
  onboardingStatus: 'NOT_STARTED',
  onboardingRevisable: false,
  onboardingRevised: false,
};

function notStartedUser(overrides = {}) {
  return {
    ...신입사용자,
    ...overrides,
  };
}

/**
 * 실제 useCurrentUser가 사용하는 캐시 키를 채운다.
 * 훅을 고정값으로 모킹하면 수정 응답의 setQueryData가 화면을 바꾸는 계약을 검증할 수 없다.
 */
function renderPage(user = pendingUser()) {
  const queryClient = new QueryClient({
    defaultOptions: {
      queries: {
        retry: false,
      },
      mutations: {
        retry: false,
      },
    },
  });

  queryClient.setQueryData(['auth', 'me'], user);

  return render(
    <QueryClientProvider client={queryClient}>
      <MemoryRouter initialEntries={['/onboarding']}>
        <Routes>
          <Route path="/onboarding" element={<OnboardingPage />} />
          <Route path="/dashboard" element={<div>대시보드 화면</div>} />
        </Routes>
      </MemoryRouter>
    </QueryClientProvider>,
  );
}

function openRevisionForm() {
  fireEvent.click(
    screen.getByRole('button', { name: '입사일 수정' }),
  );
}

function submitRevision() {
  fireEvent.click(
    screen.getByRole('button', { name: '수정 제출' }),
  );
}

beforeEach(() => {
  vi.clearAllMocks();
  localStorage.clear();

  me.mockResolvedValue(pendingUser());
  logout.mockResolvedValue(null);
  submitOnboarding.mockResolvedValue(pendingUser());
});

describe('OnboardingPage 승인 대기 중 입사일 수정', () => {
  it('대기 화면에 입력한 입사일과 생년월일을 표시한다', () => {
    renderPage();

    expect(screen.getByText('2020-03-02')).toBeInTheDocument();
    expect(screen.getByText('1995-04-12')).toBeInTheDocument();
  });

  it('수정 가능한 대기 사용자에게 입사일 수정 버튼을 표시한다', () => {
    renderPage(
      pendingUser({
        onboardingRevisable: true,
        onboardingRevised: false,
      }),
    );

    expect(
      screen.getByRole('button', { name: '입사일 수정' }),
    ).toBeInTheDocument();
  });

  it('수정권을 이미 사용한 사용자에게 버튼 대신 사용 완료 안내를 표시한다', () => {
    renderPage(
      pendingUser({
        onboardingRevisable: false,
        onboardingRevised: true,
      }),
    );

    expect(
      screen.queryByRole('button', { name: '입사일 수정' }),
    ).not.toBeInTheDocument();

    // <br />가 포함된 문장 전체가 아니라 직속 텍스트 노드의 짧은 고유 조각을 찾는다.
    expect(screen.getByText(/이미 사용/)).toBeInTheDocument();
  });

  it('관리자가 수정 기능을 막은 사용자에게 반려 요청 안내를 표시한다', () => {
    renderPage(
      pendingUser({
        onboardingRevisable: false,
        onboardingRevised: false,
      }),
    );

    expect(
      screen.queryByRole('button', { name: '입사일 수정' }),
    ).not.toBeInTheDocument();

    expect(
      screen.getByText(/관리자에게 반려를 요청/),
    ).toBeInTheDocument();
  });

  it('수정 폼을 열면 서버에 저장된 생년월일과 입사일을 초기값으로 채운다', () => {
    renderPage();

    openRevisionForm();

    expect(screen.getByLabelText('생년월일')).toHaveValue('1995-04-12');
    expect(screen.getByLabelText('입사일')).toHaveValue('2020-03-02');
  });

  it('수정 결과가 완료 상태이면 PATCH 함수를 호출하고 대시보드로 이동한다', async () => {
    reviseOnboarding.mockResolvedValue({
      ...pendingUser(),
      birthDay: '1995-05-13',
      hireDate: '2026-08-01',
      onboardingStatus: 'COMPLETED',
      onboardingRevisable: false,
      onboardingRevised: true,
    });

    renderPage();
    openRevisionForm();

    fireEvent.change(screen.getByLabelText('생년월일'), {
      target: { value: '1995-05-13' },
    });
    fireEvent.change(screen.getByLabelText('입사일'), {
      target: { value: '2026-08-01' },
    });
    submitRevision();

    await waitFor(() => {
      expect(reviseOnboarding).toHaveBeenCalledWith({
        birthDay: '1995-05-13',
        hireDate: '2026-08-01',
      });
    });

    expect(
      await screen.findByText('대시보드 화면'),
    ).toBeInTheDocument();
  });

  it('수정 후에도 대기 상태이면 대기 화면으로 돌아오고 수정 버튼이 사라진다', async () => {
    reviseOnboarding.mockResolvedValue({
      ...pendingUser(),
      birthDay: '1995-05-13',
      hireDate: '2019-02-01',
      onboardingStatus: 'PENDING_APPROVAL',
      onboardingRevisable: false,
      onboardingRevised: true,
    });

    renderPage();
    openRevisionForm();

    fireEvent.change(screen.getByLabelText('생년월일'), {
      target: { value: '1995-05-13' },
    });
    fireEvent.change(screen.getByLabelText('입사일'), {
      target: { value: '2019-02-01' },
    });
    submitRevision();

    await waitFor(() => {
      expect(reviseOnboarding).toHaveBeenCalledWith({
        birthDay: '1995-05-13',
        hireDate: '2019-02-01',
      });
    });

    await waitFor(() => {
      expect(
        screen.getByText('관리자 확인을 기다리는 중입니다'),
      ).toBeInTheDocument();
      expect(
        screen.queryByRole('button', { name: '입사일 수정' }),
      ).not.toBeInTheDocument();
    });

    expect(screen.getByText('2019-02-01')).toBeInTheDocument();
    expect(screen.getByText('1995-05-13')).toBeInTheDocument();
    expect(screen.getByText(/이미 사용/)).toBeInTheDocument();
  });
});

describe('OnboardingPage 최초 온보딩 제출 — 직급 선택 입력', () => {
  function fillRequiredFields() {
    fireEvent.change(screen.getByLabelText('생년월일'), {
      target: { value: '1995-04-12' },
    });
    fireEvent.change(screen.getByLabelText('입사일'), {
      target: { value: '2026-08-01' },
    });
  }

  it('직급을 비워도 제출된다', async () => {
    submitOnboarding.mockResolvedValue(
      notStartedUser({
        birthDay: '1995-04-12',
        hireDate: '2026-08-01',
        onboardingStatus: 'COMPLETED',
      }),
    );

    renderPage(notStartedUser());
    fillRequiredFields();
    fireEvent.click(screen.getByRole('button', { name: '시작하기' }));

    await waitFor(() => {
      expect(submitOnboarding).toHaveBeenCalledWith({
        birthDay: '1995-04-12',
        hireDate: '2026-08-01',
        jobGrade: null,
      });
    });
  });

  it('직급을 입력하면 제출 body에 실린다', async () => {
    submitOnboarding.mockResolvedValue(
      notStartedUser({
        birthDay: '1995-04-12',
        hireDate: '2026-08-01',
        onboardingStatus: 'COMPLETED',
      }),
    );

    renderPage(notStartedUser());
    fillRequiredFields();
    fireEvent.change(screen.getByLabelText('직급'), {
      target: { value: ' 선임연구원 ' },
    });
    fireEvent.click(screen.getByRole('button', { name: '시작하기' }));

    await waitFor(() => {
      expect(submitOnboarding).toHaveBeenCalledWith({
        birthDay: '1995-04-12',
        hireDate: '2026-08-01',
        jobGrade: '선임연구원',
      });
    });
  });

  it('직급 입력은 50자로 제한된다', () => {
    renderPage(notStartedUser());
    expect(screen.getByLabelText('직급')).toHaveAttribute('maxlength', '50');
  });

  it('입사일 수정 화면에는 직급 입력을 표시하지 않는다', () => {
    renderPage();
    openRevisionForm();

    expect(screen.queryByLabelText('직급')).not.toBeInTheDocument();
  });

  // 내 정보 화면의 직급 placeholder와 같은 예시를 써야 한다 (B-3)
  it('직급 placeholder가 내 정보 화면과 같은 예시를 쓴다', () => {
    renderPage(notStartedUser());
    expect(screen.getByPlaceholderText('예: 선임 연구원')).toBeInTheDocument();
  });
});
