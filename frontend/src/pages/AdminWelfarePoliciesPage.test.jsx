import { beforeEach, describe, expect, it, vi } from 'vitest';
import { fireEvent, render, screen, within } from '@testing-library/react';
import AdminWelfarePoliciesPage from './AdminWelfarePoliciesPage.jsx';
import {
  useCreateWelfarePolicy,
  useDeactivateWelfarePolicy,
  useUpdateWelfarePolicy,
  useWelfarePolicies,
} from '../hooks/useWelfare.js';

vi.mock('react-hot-toast', () => ({
  default: { success: vi.fn() },
}));

vi.mock('../hooks/useWelfare.js', () => ({
  useCreateWelfarePolicy: vi.fn(),
  useDeactivateWelfarePolicy: vi.fn(),
  useUpdateWelfarePolicy: vi.fn(),
  useWelfarePolicies: vi.fn(),
}));

const createMutate = vi.fn();
const updateMutate = vi.fn();
const deactivateMutate = vi.fn();

const POLICY = {
  id: 31,
  category: '결혼',
  target: 'SELF',
  defaultDays: '7.0',
  defaultEvidence: '청첩장',
  description: '본인 결혼',
};

function mutation(mutate) {
  return {
    mutate,
    isPending: false,
  };
}

function renderPage(rows = [POLICY]) {
  useWelfarePolicies.mockReturnValue({
    data: {
      content: rows,
      page: { totalPages: 1, totalElements: rows.length },
    },
    isLoading: false,
    isError: false,
    refetch: vi.fn(),
  });

  return render(<AdminWelfarePoliciesPage />);
}

function openCreateForm() {
  fireEvent.click(screen.getByRole('button', { name: '정책 추가' }));
  return screen.getByRole('dialog', { name: '정책 추가' });
}

function fillRequiredForm(dialog, { days = '3.5' } = {}) {
  fireEvent.change(within(dialog).getByPlaceholderText('예: 결혼'), {
    target: { value: '출산' },
  });
  fireEvent.change(within(dialog).getByPlaceholderText('예: 7'), {
    target: { value: days },
  });
  fireEvent.change(
    within(dialog).getByPlaceholderText('예: 청첩장 또는 혼인관계증명서'),
    { target: { value: '출생증명서' } },
  );
  fireEvent.change(
    within(dialog).getByPlaceholderText('사원에게 보일 안내 문구를 적어주세요.'),
    { target: { value: '출산 복리후생' } },
  );
}

beforeEach(() => {
  vi.clearAllMocks();

  useCreateWelfarePolicy.mockReturnValue(mutation(createMutate));
  useUpdateWelfarePolicy.mockReturnValue(mutation(updateMutate));
  useDeactivateWelfarePolicy.mockReturnValue(mutation(deactivateMutate));
});

describe('AdminWelfarePoliciesPage 정책 폼', () => {
  it('정책 추가 버튼은 빈 생성 폼을 연다', () => {
    renderPage();

    const dialog = openCreateForm();

    expect(
      within(dialog).getByPlaceholderText('예: 결혼'),
    ).toHaveValue('');
    expect(within(dialog).getByRole('combobox')).toHaveValue('SELF');
    expect(within(dialog).getByPlaceholderText('예: 7')).toHaveValue(null);
  });

  it('정책 수정 버튼은 기존 값을 채운 수정 폼을 연다', () => {
    renderPage();

    fireEvent.click(screen.getByRole('button', { name: '정책 수정' }));

    const dialog = screen.getByRole('dialog', { name: '정책 수정' });
    expect(within(dialog).getByPlaceholderText('예: 결혼')).toHaveValue('결혼');
    expect(within(dialog).getByPlaceholderText('예: 7')).toHaveValue(7);
    expect(
      within(dialog).getByPlaceholderText('예: 청첩장 또는 혼인관계증명서'),
    ).toHaveValue('청첩장');
  });

  it('필수 입력값이 비어 있으면 생성 요청을 보내지 않는다', () => {
    renderPage();

    const dialog = openCreateForm();
    fireEvent.click(within(dialog).getByRole('button', { name: '저장' }));

    expect(screen.getByText('구분을 입력해주세요.')).toBeInTheDocument();
    expect(screen.getByText('부여 일수를 입력해주세요.')).toBeInTheDocument();
    expect(screen.getByText('제출자료 안내를 입력해주세요.')).toBeInTheDocument();
    expect(screen.getByText('설명을 입력해주세요.')).toBeInTheDocument();
    expect(createMutate).not.toHaveBeenCalled();
  });

  it('부여 일수가 0보다 작으면 저장하지 않는다', () => {
    renderPage();

    const dialog = openCreateForm();
    fillRequiredForm(dialog, { days: '-0.5' });
    fireEvent.click(within(dialog).getByRole('button', { name: '저장' }));

    expect(
      screen.getByText('부여 일수는 0.5일 단위로 입력해주세요.'),
    ).toBeInTheDocument();
    expect(createMutate).not.toHaveBeenCalled();
  });

  it('부여 일수가 365보다 크면 저장하지 않는다', () => {
    renderPage();

    const dialog = openCreateForm();
    fillRequiredForm(dialog, { days: '365.5' });
    fireEvent.click(within(dialog).getByRole('button', { name: '저장' }));

    expect(
      screen.getByText('부여 일수는 0~365일 사이여야 합니다.'),
    ).toBeInTheDocument();
    expect(createMutate).not.toHaveBeenCalled();
  });

  it('생성 요청의 defaultDays를 소수 한 자리 문자열로 보낸다', () => {
    renderPage();

    const dialog = openCreateForm();
    fillRequiredForm(dialog, { days: '3.5' });
    fireEvent.click(within(dialog).getByRole('button', { name: '저장' }));

    expect(createMutate).toHaveBeenCalledWith(
      {
        category: '출산',
        target: 'SELF',
        defaultDays: '3.5',
        defaultEvidence: '출생증명서',
        description: '출산 복리후생',
      },
      expect.objectContaining({ onSuccess: expect.any(Function) }),
    );
  });

  it('수정과 비활성화 요청에 선택한 정책 ID를 사용한다', () => {
    renderPage();

    fireEvent.click(screen.getByRole('button', { name: '정책 수정' }));
    const editDialog = screen.getByRole('dialog', { name: '정책 수정' });
    fireEvent.click(within(editDialog).getByRole('button', { name: '저장' }));

    expect(updateMutate).toHaveBeenCalledWith(
      {
        id: 31,
        category: '결혼',
        target: 'SELF',
        defaultDays: '7.0',
        defaultEvidence: '청첩장',
        description: '본인 결혼',
      },
      expect.objectContaining({ onSuccess: expect.any(Function) }),
    );

    fireEvent.click(screen.getByRole('button', { name: '정책 비활성화' }));
    const deactivateDialog = screen.getByRole('dialog', {
      name: '정책 비활성화',
    });
    fireEvent.click(
      within(deactivateDialog).getByRole('button', { name: '비활성화' }),
    );

    expect(deactivateMutate).toHaveBeenCalledWith(
      31,
      expect.objectContaining({ onSuccess: expect.any(Function) }),
    );
  });
});
