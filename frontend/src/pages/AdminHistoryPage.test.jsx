import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';
import { fireEvent, render, screen } from '@testing-library/react';
import AdminHistoryPage from './AdminHistoryPage.jsx';
import { useCurrentUser } from '../hooks/useAuth.js';
import { useLeaveHistories, useWelfareHistories } from '../hooks/useHistories.js';
import { useAuditActions, useAuditLogs } from '../hooks/useAudit.js';

vi.mock('../hooks/useAuth.js', () => ({
  useCurrentUser: vi.fn(),
}));

vi.mock('../hooks/useHistories.js', () => ({
  useLeaveHistories: vi.fn(),
  useWelfareHistories: vi.fn(),
}));

vi.mock('../hooks/useAudit.js', () => ({
  useAuditActions: vi.fn(),
  useAuditLogs: vi.fn(),
}));

function installMatchMedia(matches) {
  window.matchMedia = vi.fn().mockImplementation((media) => ({
    matches,
    media,
    onchange: null,
    addEventListener: vi.fn(),
    removeEventListener: vi.fn(),
    dispatchEvent: vi.fn(),
  }));
}

function query(data = { content: [], page: { totalPages: 1, totalElements: 0 } }) {
  return {
    data,
    isLoading: false,
    isError: false,
    refetch: vi.fn(),
  };
}

beforeEach(() => {
  vi.clearAllMocks();
  installMatchMedia(false);

  useCurrentUser.mockReturnValue({
    data: { role: 'SYSTEM_ADMIN' },
  });
  useLeaveHistories.mockReturnValue(query());
  useWelfareHistories.mockReturnValue(query());
  useAuditLogs.mockReturnValue(query());
  useAuditActions.mockReturnValue(query([]));
});

afterEach(() => {
  vi.unstubAllGlobals();
});

describe('AdminHistoryPage 권한 및 조회 스코프', () => {
  it('SYSTEM_ADMIN에게 관리자 조작 탭을 표시한다', () => {
    render(<AdminHistoryPage />);

    expect(
      screen.getByRole('button', { name: '관리자 조작' }),
    ).toBeInTheDocument();
    expect(useLeaveHistories).toHaveBeenCalledWith(
      expect.objectContaining({
        scope: 'all',
        page: 0,
        size: 10,
        enabled: true,
      }),
    );
  });

  it('TEAM_LEADER에게 관리자 조작 탭을 숨기고 my-approvals를 사용한다', () => {
    useCurrentUser.mockReturnValue({
      data: { role: 'TEAM_LEADER' },
    });

    render(<AdminHistoryPage />);

    expect(
      screen.queryByRole('button', { name: '관리자 조작' }),
    ).not.toBeInTheDocument();
    expect(useLeaveHistories).toHaveBeenCalledWith(
      expect.objectContaining({
        scope: 'my-approvals',
        enabled: true,
      }),
    );
    expect(screen.getByText(/내가 결재자로 지정된/)).toBeInTheDocument();
  });

  it('관리자 조작 탭을 선택해야 감사 로그 조회를 활성화한다', () => {
    render(<AdminHistoryPage />);

    expect(useAuditLogs).toHaveBeenLastCalledWith(
      expect.objectContaining({ enabled: false }),
    );

    fireEvent.click(screen.getByRole('button', { name: '관리자 조작' }));

    expect(useAuditLogs).toHaveBeenLastCalledWith({
      action: undefined,
      page: 0,
      size: 10,
      enabled: true,
    });
    expect(useAuditActions).toHaveBeenLastCalledWith({ enabled: true });
  });

  it('감사 액션 필터는 서버가 내려준 라벨로 렌더링한다', () => {
    useAuditActions.mockReturnValue(
      query([
        { name: 'ROLE_CHANGED', label: '권한 변경' },
        { name: 'CONFIG_CHANGED', label: '정책 설정 변경' },
      ]),
    );

    render(<AdminHistoryPage />);
    fireEvent.click(screen.getByRole('button', { name: '관리자 조작' }));

    expect(
      screen.getByRole('button', { name: '권한 변경' }),
    ).toBeInTheDocument();
    expect(
      screen.getByRole('button', { name: '정책 설정 변경' }),
    ).toBeInTheDocument();

    fireEvent.click(screen.getByRole('button', { name: '권한 변경' }));

    expect(useAuditLogs).toHaveBeenLastCalledWith({
      action: 'ROLE_CHANGED',
      page: 0,
      size: 10,
      enabled: true,
    });
  });

  it('탭을 바꾸면 이전 탭의 액션 필터를 ALL로 초기화한다', () => {
    render(<AdminHistoryPage />);

    fireEvent.click(screen.getByRole('button', { name: '반려' }));
    expect(useLeaveHistories).toHaveBeenLastCalledWith(
      expect.objectContaining({ action: 'REJECTED' }),
    );

    fireEvent.click(screen.getByRole('button', { name: '복리후생' }));

    expect(useWelfareHistories).toHaveBeenLastCalledWith(
      expect.objectContaining({
        action: undefined,
        page: 0,
        enabled: true,
      }),
    );
  });

  it('좁은 화면에서는 결재 이력을 표 대신 카드로 표시한다', () => {
    installMatchMedia(true);
    useLeaveHistories.mockReturnValue(
      query({
        content: [
          {
            id: 10,
            createdAt: '2026-08-20T09:30:00',
            action: 'APPROVED',
            userName: '홍길동',
            departmentName: '개발팀',
            leaveType: 'ANNUAL',
            days: '1.0',
            actorName: '김팀장',
            comment: '승인합니다',
          },
        ],
        page: { totalPages: 1, totalElements: 1 },
      }),
    );

    render(<AdminHistoryPage />);

    expect(screen.getByTestId('responsive-table-cards')).toBeInTheDocument();
    expect(screen.queryByTestId('responsive-table-table')).not.toBeInTheDocument();
    expect(screen.getByText('홍길동')).toBeInTheDocument();
    expect(screen.getByText('개발팀')).toBeInTheDocument();
    expect(screen.getByText('승인합니다')).toBeInTheDocument();
  });

  it('감사 로그의 변경 전과 변경 후 값을 함께 표시한다', () => {
    useAuditLogs.mockReturnValue(
      query({
        content: [
          {
            id: 1,
            createdAt: '2026-08-10T13:45:33',
            action: 'ROLE_CHANGED',
            actionLabel: '권한 변경',
            actorName: '관리자',
            targetLabel: '홍길동',
            beforeValue: 'EMPLOYEE',
            afterValue: 'TEAM_LEADER',
          },
        ],
        page: { totalPages: 1, totalElements: 1 },
      }),
    );

    render(<AdminHistoryPage />);
    fireEvent.click(screen.getByRole('button', { name: '관리자 조작' }));

    expect(screen.getByText('권한 변경')).toBeInTheDocument();
    expect(screen.getByText('EMPLOYEE')).toBeInTheDocument();
    expect(screen.getByText('TEAM_LEADER')).toBeInTheDocument();
    expect(screen.getByText('2026-08-10 13:45')).toBeInTheDocument();
  });
});
