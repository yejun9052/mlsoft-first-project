import { beforeEach, describe, expect, it, vi } from 'vitest';
import { fireEvent, render, screen, within } from '@testing-library/react';
import ApprovalsPage from './ApprovalsPage.jsx';
import { useCurrentUser } from '../hooks/useAuth.js';
import {
  useAllLeavesCount,
  usePendingApprovals,
  useProcessApproval,
  useProcessCancelApproval,
} from '../hooks/useLeaves.js';
import { useLeaveHistories, useWelfareHistories } from '../hooks/useHistories.js';
import {
  usePendingWelfareApprovals,
  useProcessWelfareApproval,
} from '../hooks/useWelfare.js';

vi.mock('react-hot-toast', () => ({
  default: { success: vi.fn() },
}));

vi.mock('../hooks/useAuth.js', () => ({
  useCurrentUser: vi.fn(),
}));

vi.mock('../hooks/useLeaves.js', () => ({
  useAllLeavesCount: vi.fn(),
  usePendingApprovals: vi.fn(),
  useProcessApproval: vi.fn(),
  useProcessCancelApproval: vi.fn(),
}));

vi.mock('../hooks/useHistories.js', () => ({
  useLeaveHistories: vi.fn(),
  useWelfareHistories: vi.fn(),
}));

vi.mock('../hooks/useWelfare.js', () => ({
  usePendingWelfareApprovals: vi.fn(),
  useProcessWelfareApproval: vi.fn(),
}));

const approvalMutate = vi.fn();
const cancelApprovalMutate = vi.fn();
const welfareApprovalMutate = vi.fn();

function query(data = { content: [] }, overrides = {}) {
  return {
    data,
    isLoading: false,
    isError: false,
    refetch: vi.fn(),
    ...overrides,
  };
}

function mutation(mutate) {
  return {
    mutate,
    isPending: false,
  };
}

function leaveItem(overrides = {}) {
  return {
    id: 11,
    status: 'PENDING',
    userName: '김연차',
    departmentName: '개발팀',
    leaveType: 'ANNUAL',
    dates: ['2026-08-17'],
    days: '1.0',
    requestReason: '개인 일정',
    cancelReason: null,
    createdAt: '2026-08-11T09:00:00',
    ...overrides,
  };
}

function welfareItem(overrides = {}) {
  return {
    id: 21,
    userName: '박복리',
    departmentName: '기획팀',
    addDays: '3.0',
    reason: '결혼',
    createdAt: '2026-08-10T10:00:00',
    ...overrides,
  };
}

beforeEach(() => {
  vi.clearAllMocks();

  useCurrentUser.mockReturnValue({
    data: { role: 'TEAM_LEADER' },
  });

  usePendingApprovals.mockReturnValue(query());
  usePendingWelfareApprovals.mockReturnValue(query());

  useProcessApproval.mockReturnValue(mutation(approvalMutate));
  useProcessCancelApproval.mockReturnValue(mutation(cancelApprovalMutate));
  useProcessWelfareApproval.mockReturnValue(mutation(welfareApprovalMutate));

  useAllLeavesCount.mockReturnValue(query(0));
  useLeaveHistories.mockReturnValue(query());
  useWelfareHistories.mockReturnValue(query());
});

describe('ApprovalsPage 결재 종류별 처리', () => {
  it('연차와 복리후생 대기 신청을 하나의 목록에 함께 표시한다', () => {
    usePendingApprovals.mockReturnValue(query({ content: [leaveItem()] }));
    usePendingWelfareApprovals.mockReturnValue(query({ content: [welfareItem()] }));

    render(<ApprovalsPage />);

    expect(screen.getByText('김연차')).toBeInTheDocument();
    expect(screen.getByText('박복리')).toBeInTheDocument();
    expect(screen.getByText('연차')).toBeInTheDocument();
    expect(screen.getByText('경조/복리')).toBeInTheDocument();
  });

  it('일반 연차 승인에는 연차 approval 뮤테이션을 사용한다', () => {
    usePendingApprovals.mockReturnValue(query({ content: [leaveItem()] }));

    render(<ApprovalsPage />);

    const leaveRow = screen.getByText('김연차').closest('tr');
    expect(leaveRow).not.toBeNull();

    fireEvent.click(
      within(leaveRow).getByRole('button', { name: '승인' }),
    );
    fireEvent.change(screen.getByPlaceholderText('결재 의견을 입력하세요 (선택)'), {
      target: { value: '승인합니다' },
    });
    fireEvent.click(
      within(screen.getByRole('dialog', { name: '신청 승인' }))
        .getByRole('button', { name: '승인' }),
    );

    expect(approvalMutate).toHaveBeenCalledWith(
      {
        id: 11,
        approved: true,
        comment: '승인합니다',
      },
      expect.objectContaining({ onSuccess: expect.any(Function) }),
    );
    expect(cancelApprovalMutate).not.toHaveBeenCalled();
    expect(welfareApprovalMutate).not.toHaveBeenCalled();
  });

  it('CANCEL_PENDING 반려에는 cancel-approval 뮤테이션을 사용한다', () => {
    usePendingApprovals.mockReturnValue(
      query({
        content: [
          leaveItem({
            id: 12,
            status: 'CANCEL_PENDING',
            cancelReason: '소급 취소 요청',
          }),
        ],
      }),
    );

    render(<ApprovalsPage />);

    const cancelRow = screen.getByText('김연차').closest('tr');
    expect(cancelRow).not.toBeNull();

    fireEvent.click(
      within(cancelRow).getByRole('button', { name: '반려' }),
    );
    fireEvent.click(
      within(screen.getByRole('dialog', { name: '신청 반려' }))
        .getByRole('button', { name: '반려' }),
    );

    expect(cancelApprovalMutate).toHaveBeenCalledWith(
      {
        id: 12,
        approved: false,
        comment: '',
      },
      expect.objectContaining({ onSuccess: expect.any(Function) }),
    );
    expect(approvalMutate).not.toHaveBeenCalled();
    expect(welfareApprovalMutate).not.toHaveBeenCalled();
  });

  it('복리후생 승인에는 welfare approval 뮤테이션을 사용한다', () => {
    usePendingWelfareApprovals.mockReturnValue(
      query({ content: [welfareItem({ id: 22 })] }),
    );

    render(<ApprovalsPage />);

    const welfareRow = screen.getByText('박복리').closest('tr');
    expect(welfareRow).not.toBeNull();

    fireEvent.click(
      within(welfareRow).getByRole('button', { name: '승인' }),
    );
    fireEvent.click(
      within(screen.getByRole('dialog', { name: '신청 승인' }))
        .getByRole('button', { name: '승인' }),
    );

    expect(welfareApprovalMutate).toHaveBeenCalledWith(
      {
        id: 22,
        approved: true,
        comment: '',
      },
      expect.objectContaining({ onSuccess: expect.any(Function) }),
    );
    expect(approvalMutate).not.toHaveBeenCalled();
    expect(cancelApprovalMutate).not.toHaveBeenCalled();
  });

  it('처리 완료 탭은 my-actions 스코프로 조회한다', () => {
    render(<ApprovalsPage />);

    fireEvent.click(screen.getByRole('button', { name: '승인' }));

    expect(useLeaveHistories).toHaveBeenLastCalledWith({
      scope: 'my-actions',
      action: 'APPROVED',
      size: 50,
      enabled: true,
    });
    expect(useWelfareHistories).toHaveBeenLastCalledWith({
      scope: 'my-actions',
      action: 'APPROVED',
      size: 50,
      enabled: true,
    });
  });

  it('회사 전체 대기 통계는 SYSTEM_ADMIN에게만 표시한다', () => {
    useCurrentUser.mockReturnValue({
      data: { role: 'SYSTEM_ADMIN' },
    });
    useAllLeavesCount
      .mockReturnValueOnce(query(7))
      .mockReturnValueOnce(query(2));

    render(<ApprovalsPage />);

    expect(screen.getByText('전체 신규 신청 대기')).toBeInTheDocument();
    expect(screen.getByText('전체 취소 요청 대기')).toBeInTheDocument();
    expect(useAllLeavesCount).toHaveBeenNthCalledWith(1, 'PENDING', true);
    expect(useAllLeavesCount).toHaveBeenNthCalledWith(2, 'CANCEL_PENDING', true);
  });
});
