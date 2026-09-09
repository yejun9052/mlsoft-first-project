import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';
import { fireEvent, render, screen, within } from '@testing-library/react';
import { createMemoryRouter, RouterProvider } from 'react-router-dom';
import AdminPolicyPage from './AdminPolicyPage.jsx';
import {
  useLeavePolicies,
  useLeavePolicyConfigs,
  useResetHistories,
  useUpdateLeavePolicy,
  useUpdateLeavePolicyConfig,
} from '../hooks/usePolicies.js';
import { useHolidays, useSyncHolidays } from '../hooks/useHolidays.js';

vi.mock('react-hot-toast', () => ({
  default: { success: vi.fn(), error: vi.fn() },
}));

vi.mock('../hooks/usePolicies.js', () => ({
  useLeavePolicies: vi.fn(),
  useLeavePolicyConfigs: vi.fn(),
  useResetHistories: vi.fn(),
  useUpdateLeavePolicy: vi.fn(),
  useUpdateLeavePolicyConfig: vi.fn(),
}));

vi.mock('../hooks/useHolidays.js', () => ({
  useHolidays: vi.fn(),
  useSyncHolidays: vi.fn(),
}));

const CURRENT_YEAR = new Date().getFullYear();
const syncMutate = vi.fn();
const updateConfigMutateAsync = vi.fn().mockResolvedValue(undefined);

function query(data) {
  return {
    data,
    isLoading: false,
    isError: false,
    refetch: vi.fn(),
  };
}

function renderPage({ configs = [] } = {}) {
  useLeavePolicies.mockReturnValue(query([]));
  useLeavePolicyConfigs.mockReturnValue(query(configs));
  useResetHistories.mockReturnValue(
    query({ content: [], page: { totalPages: 1, totalElements: 0 } }),
  );
  useUpdateLeavePolicy.mockReturnValue({ mutate: vi.fn(), isPending: false });
  useUpdateLeavePolicyConfig.mockReturnValue({ mutateAsync: updateConfigMutateAsync, isPending: false });
  useHolidays.mockImplementation((year) =>
    query(year === CURRENT_YEAR ? [{ date: `${CURRENT_YEAR}-01-01`, name: '신정' }] : []),
  );
  useSyncHolidays.mockReturnValue({ mutate: syncMutate, isPending: false });

  const router = createMemoryRouter(
    [{ path: '/', element: <AdminPolicyPage /> }],
    { initialEntries: ['/'] },
  );
  return render(<RouterProvider router={router} />);
}

const REAL_MATCH_MEDIA = window.matchMedia;

function setViewport(isMobile) {
  window.matchMedia = vi.fn().mockImplementation((query) => ({
    matches: isMobile,
    media: query,
    addEventListener: vi.fn(),
    removeEventListener: vi.fn(),
  }));
}

beforeEach(() => {
  vi.clearAllMocks();
  syncMutate.mockReset();
});

afterEach(() => {
  window.matchMedia = REAL_MATCH_MEDIA;
});

describe('AdminPolicyPage 공휴일 동기화', () => {
  it('공휴일 섹션과 현재 연도 적재 건수를 보여준다', () => {
    renderPage();

    expect(screen.getByRole('heading', { name: '공휴일' })).toBeInTheDocument();
    expect(screen.getByLabelText('공휴일 조회 연도')).toHaveValue(String(CURRENT_YEAR));
    expect(screen.getByText('현재 적재 건수')).toBeInTheDocument();
    expect(screen.getByText('1건')).toBeInTheDocument();
  });

  it('선택한 연도로 동기화 API를 호출한다', () => {
    renderPage();

    const targetYear = CURRENT_YEAR + 1;
    fireEvent.change(screen.getByLabelText('공휴일 조회 연도'), {
      target: { value: String(targetYear) },
    });
    fireEvent.click(screen.getByRole('button', { name: '동기화' }));

    expect(syncMutate).toHaveBeenCalledWith(
      { year: targetYear },
      expect.objectContaining({ onSuccess: expect.any(Function) }),
    );
  });

  it('동기화 성공 결과의 적재 건수를 표시한다', () => {
    syncMutate.mockImplementation((_variables, options) => {
      options.onSuccess({ year: CURRENT_YEAR, count: 22 });
    });
    renderPage();

    fireEvent.click(screen.getByRole('button', { name: '동기화' }));

    expect(screen.getByText('22건 적재')).toBeInTheDocument();
  });
});

// visibleWhen 조건부 노출 — 퇴직자-데이터-파기-설계-2026-09-07.md §2
const MODE_CONFIG = {
  id: 1,
  name: 'retiree_purge_mode',
  value: 'MANUAL',
  type: 'ENUM',
  defaultValue: 'MANUAL',
  min: null,
  max: null,
  unit: null,
  options: ['MANUAL', 'AUTO'],
  label: '퇴직자 파기 방식',
  description: '퇴직자 개인정보 파기 방식',
  status: 'PENDING_FEATURE',
  visibleWhen: null,
};
const YEARS_CONFIG = {
  id: 2,
  name: 'retiree_purge_years',
  value: '3',
  type: 'INTEGER',
  defaultValue: '3',
  min: 3,
  max: 10,
  unit: '년',
  options: [],
  label: '퇴직자 파기 유예기간',
  description: '자동 파기까지 걸리는 기간',
  status: 'PENDING_FEATURE',
  visibleWhen: { dependsOnKey: 'retiree_purge_mode', requiredValue: 'AUTO' },
};

describe('AdminPolicyPage 시스템 설정 조건부 노출', () => {
  it('visibleWhen이 없는 설정은 그대로 보인다', () => {
    renderPage({ configs: [MODE_CONFIG] });

    expect(screen.getByText(MODE_CONFIG.label)).toBeInTheDocument();
  });

  it('의존 설정 값이 조건과 다르면 해당 설정이 문서에 없다', () => {
    renderPage({ configs: [MODE_CONFIG, YEARS_CONFIG] });

    expect(screen.getByText(MODE_CONFIG.label)).toBeInTheDocument();
    expect(screen.queryByText(YEARS_CONFIG.label)).not.toBeInTheDocument();
  });

  it('의존 설정 값을 조건과 맞게 바꾸면 저장하지 않아도 나타난다', () => {
    renderPage({ configs: [MODE_CONFIG, YEARS_CONFIG] });

    expect(screen.queryByText(YEARS_CONFIG.label)).not.toBeInTheDocument();

    fireEvent.change(screen.getByLabelText(MODE_CONFIG.label), { target: { value: 'AUTO' } });

    expect(screen.getByText(YEARS_CONFIG.label)).toBeInTheDocument();
    // 저장 버튼을 누르지 않았지만 나타난다 — 화면 표시는 서버 값이 아니라 편집 중인 값을 본다
    expect(updateConfigMutateAsync).not.toHaveBeenCalled();
  });

  it('조건에서 벗어나면 사라지고, 값을 바꾼 뒤 되돌리면 그 값이 유지된다', () => {
    renderPage({ configs: [MODE_CONFIG, YEARS_CONFIG] });

    fireEvent.change(screen.getByLabelText(MODE_CONFIG.label), { target: { value: 'AUTO' } });
    fireEvent.change(screen.getByLabelText(YEARS_CONFIG.label), { target: { value: '5' } });
    expect(screen.getByLabelText(YEARS_CONFIG.label)).toHaveValue(5);

    fireEvent.change(screen.getByLabelText(MODE_CONFIG.label), { target: { value: 'MANUAL' } });
    expect(screen.queryByText(YEARS_CONFIG.label)).not.toBeInTheDocument();

    fireEvent.change(screen.getByLabelText(MODE_CONFIG.label), { target: { value: 'AUTO' } });
    expect(screen.getByLabelText(YEARS_CONFIG.label)).toHaveValue(5);
  });
});

// 좁은 화면 저장 막대 (C-2) — 값을 바꾸고도 저장 버튼을 못 보고 나가는 것을 막는다
describe('AdminPolicyPage 좁은 화면 저장 막대', () => {
  it('넓은 화면에서는 화면 아래 고정 막대를 그리지 않는다', () => {
    setViewport(false);
    renderPage({ configs: [YEARS_CONFIG] });

    fireEvent.change(screen.getByLabelText(YEARS_CONFIG.label), { target: { value: '5' } });

    expect(screen.queryByRole('status')).not.toBeInTheDocument();
    // 카드 안 저장 버튼은 그대로 하나만 있다
    expect(screen.getAllByRole('button', { name: '설정 저장' })).toHaveLength(1);
  });

  it('좁은 화면에서 변경이 없으면 조용하다 — 늘 떠 있으면 소음이다', () => {
    setViewport(true);
    renderPage({ configs: [YEARS_CONFIG] });

    expect(screen.queryByRole('status')).not.toBeInTheDocument();
    expect(screen.queryByText(/저장하지 않은 변경/)).not.toBeInTheDocument();
  });

  it('좁은 화면에서 값을 바꾸면 저장 막대가 뜨고, 그 버튼으로 저장할 수 있다', () => {
    setViewport(true);
    renderPage({ configs: [YEARS_CONFIG] });

    fireEvent.change(screen.getByLabelText(YEARS_CONFIG.label), { target: { value: '5' } });

    const bar = screen.getByRole('status');
    expect(within(bar).getByText('저장하지 않은 변경 1건')).toBeInTheDocument();

    fireEvent.click(within(bar).getByRole('button', { name: '설정 저장' }));

    expect(updateConfigMutateAsync).toHaveBeenCalledWith({ name: YEARS_CONFIG.name, value: '5' });
  });
});
