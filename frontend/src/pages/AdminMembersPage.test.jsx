import { beforeEach, describe, expect, it, vi } from 'vitest';
import { fireEvent, render, screen } from '@testing-library/react';
import toast from 'react-hot-toast';
import AdminMembersPage from './AdminMembersPage.jsx';
import {
  useRestoreUser,
  useRetiredUsers,
  useRetireUser,
  useUpdateUserDepartment,
  useUpdateUserRole,
  useUsers,
} from '../hooks/useUsers.js';
import { useDepartments } from '../hooks/useDepartments.js';
import {
  useApproveOnboarding,
  useCurrentUser,
  usePendingOnboardings,
  useRejectOnboarding,
} from '../hooks/useAuth.js';

// error까지 목킹해야 한다 — success만 두면 toast.error를 부르는 경로가 크래시하는데,
// 그 크래시가 단정보다 뒤에 일어나면 테스트는 통과한 것처럼 보인다 (2026-08-16에 실제로 그랬다)
vi.mock('react-hot-toast', () => ({ default: { success: vi.fn(), error: vi.fn() } }));

vi.mock('../hooks/useUsers.js', () => ({
  useRestoreUser: vi.fn(),
  useRetiredUsers: vi.fn(),
  useRetireUser: vi.fn(),
  useUpdateUserDepartment: vi.fn(),
  useUpdateUserRole: vi.fn(),
  useUsers: vi.fn(),
}));

vi.mock('../hooks/useDepartments.js', () => ({ useDepartments: vi.fn() }));

vi.mock('../hooks/useAuth.js', () => ({
  useApproveOnboarding: vi.fn(),
  useCurrentUser: vi.fn(),
  usePendingOnboardings: vi.fn(),
  useRejectOnboarding: vi.fn(),
}));

const roleMutate = vi.fn();
const deptMutate = vi.fn();
const restoreMutate = vi.fn();

const 개발팀 = { id: 1, name: '개발팀', active: true };
const 디자인팀 = { id: 2, name: '디자인팀', active: true };
const 폐지된팀 = { id: 9, name: '폐지된팀', active: false };

const 나 = {
  id: 100,
  name: '이예준',
  email: 'me@mlsoft.com',
  departmentId: 1,
  departmentName: '개발팀',
  position: '팀장',
  role: 'SYSTEM_ADMIN',
  remainingDays: 10,
  baseDays: 15,
  hireDate: '2020-03-02',
};
const 남 = { ...나, id: 200, name: '박준호', email: 'park@mlsoft.com', role: 'EMPLOYEE' };

function renderPage(rows = [나, 남], departments = [개발팀, 디자인팀], retired = []) {
  useUsers.mockReturnValue({ data: { content: rows, page: { totalElements: rows.length } }, isLoading: false, isError: false, refetch: vi.fn() });
  useRetiredUsers.mockReturnValue({ data: { content: retired, page: { totalElements: retired.length } }, isLoading: false, isError: false, refetch: vi.fn() });
  useRestoreUser.mockReturnValue({ mutate: restoreMutate, isPending: false });
  useDepartments.mockReturnValue({ data: departments, isLoading: false });
  usePendingOnboardings.mockReturnValue({ data: { content: [], page: { totalElements: 0 } }, isLoading: false, isError: false, refetch: vi.fn() });
  useApproveOnboarding.mockReturnValue({ mutate: vi.fn(), isPending: false });
  useRejectOnboarding.mockReturnValue({ mutate: vi.fn(), isPending: false });
  useUpdateUserRole.mockReturnValue({ mutate: roleMutate, isPending: false });
  useUpdateUserDepartment.mockReturnValue({ mutate: deptMutate, isPending: false });
  useRetireUser.mockReturnValue({ mutate: vi.fn(), isPending: false });
  useCurrentUser.mockReturnValue({ data: { id: 나.id } });
  return render(<AdminMembersPage />);
}

/** 라벨은 InlineSelect가 aria-label로 내려 준다 — 행마다 이름이 붙어 유일하다 */
const 역할셀렉트 = (name) => screen.getByLabelText(`${name}님의 역할`);
const 부서셀렉트 = (name) => screen.getByLabelText(`${name}님의 부서`);

describe('AdminMembersPage 표에서 바로 고치기', () => {
  beforeEach(() => {
    vi.clearAllMocks();
  });

  it('부서와 역할을 셀렉트로 보여주고 현재 값이 선택돼 있다', () => {
    renderPage();

    expect(역할셀렉트('박준호')).toHaveValue('EMPLOYEE');
    expect(부서셀렉트('박준호')).toHaveValue('1');
  });

  // 역할 변경은 방향과 무관하게 전부 되묻는다 — 권한이 한 번의 클릭으로 바뀌면 안 된다.
  // 팀장은 "어느 부서의" 팀장이므로 부서 이름이 문장에 있어야 한다 — 없으면 어느 팀 얘기인지 모른다
  it('팀장으로 올릴 때 어느 부서의 팀장이 되는지 밝힌 확인을 받는다', () => {
    renderPage();

    fireEvent.change(역할셀렉트('박준호'), { target: { value: 'TEAM_LEADER' } });

    expect(roleMutate).not.toHaveBeenCalled();
    expect(screen.getByText(/박준호님을 개발팀 팀장으로 정말 지정하시겠습니까/)).toBeInTheDocument();
    expect(screen.getByText(/개발팀의 연차·복리후생 신청이 기본으로 박준호님에게 갑니다/)).toBeInTheDocument();
  });

  // 팀장 지정은 그 부서 결재선을 바꾸는 조작이다. 앉힐 자리(부서)가 없으면 서버도 거부하므로
  // 확인 창까지 띄웠다가 오류를 보여 주는 것은 헛걸음이다
  it('부서가 없는 사원은 팀장으로 올릴 수 없다 — 확인 창도 뜨지 않는다', () => {
    const 미배정 = { ...남, departmentId: null, departmentName: null };
    renderPage([나, 미배정]);

    fireEvent.change(역할셀렉트('박준호'), { target: { value: 'TEAM_LEADER' } });

    expect(roleMutate).not.toHaveBeenCalled();
    expect(screen.queryByText(/정말 지정하시겠습니까/)).not.toBeInTheDocument();
    // 막기만 하고 아무 말도 안 하면 관리자는 클릭이 먹히지 않는 것으로 본다
    expect(toast.error).toHaveBeenCalledWith(expect.stringContaining('부서를 먼저 배정'));
  });

  // 부서당 팀장은 1명이라 기존 팀장이 내려간다. 그 사실을 안 적으면 관리자는 남을 강등시킨 줄 모른다
  it('이미 팀장이 있는 부서면 누가 내려가는지 이름까지 알린다', () => {
    const 팀장있는개발팀 = { ...개발팀, leaderId: 500, leaderName: '김도현' };
    renderPage([나, 남], [팀장있는개발팀, 디자인팀]);

    fireEvent.change(역할셀렉트('박준호'), { target: { value: 'TEAM_LEADER' } });

    expect(screen.getByText(/현재 팀장인 김도현님은 사원으로 내려가고/)).toBeInTheDocument();
  });

  it('확인을 누르면 그때 남의 역할이 저장된다', () => {
    renderPage();
    fireEvent.change(역할셀렉트('박준호'), { target: { value: 'TEAM_LEADER' } });

    fireEvent.click(screen.getByRole('button', { name: '팀장(으)로 변경' }));

    expect(roleMutate).toHaveBeenCalledWith(
      expect.objectContaining({ id: 200, role: 'TEAM_LEADER' }),
      expect.anything(),
    );
  });

  it('총관리자로 올릴 때는 그 권한의 범위를 밝힌다', () => {
    renderPage();

    fireEvent.change(역할셀렉트('박준호'), { target: { value: 'SYSTEM_ADMIN' } });

    expect(screen.getByText(/전 사원의 연차·권한·부서와 시스템 설정을 바꿀 수 있습니다/)).toBeInTheDocument();
  });

  // 강등은 서버에서 팀장직 해제 + 대기 결재 재배정이 함께 일어난다 (리뷰 I-5a).
  // 화면에 적지 않으면 관리자가 알 방법이 없다.
  it('사원으로 강등할 때는 팀장직 해제와 결재 이관을 알린다', () => {
    const 팀장 = { ...남, role: 'TEAM_LEADER' };
    renderPage([나, 팀장]);

    fireEvent.change(역할셀렉트('박준호'), { target: { value: 'EMPLOYEE' } });

    expect(screen.getByText(/팀장직이 해제되고.*대기 결재는 다른 승인자에게 넘어갑니다/)).toBeInTheDocument();
  });

  it('부서를 바꾸면 곧바로 저장한다', () => {
    renderPage();

    fireEvent.change(부서셀렉트('박준호'), { target: { value: '2' } });

    expect(deptMutate).toHaveBeenCalledWith(
      expect.objectContaining({ id: 200, departmentId: 2 }),
      expect.anything(),
    );
  });

  // 여기가 이 화면에서 유일하게 스스로를 잠글 수 있는 조작이다. 서버의 마지막 관리자 보호는
  // 관리자가 2명 이상이면 걸리지 않으므로 화면이 되물어야 한다.
  it('내 역할을 낮출 때는 화면이 잠긴다는 것까지 알린다', () => {
    renderPage();

    fireEvent.change(역할셀렉트('이예준'), { target: { value: 'EMPLOYEE' } });

    expect(roleMutate).not.toHaveBeenCalled();
    expect(screen.getByText(/관리자 화면에 더 이상 들어올 수 없고/)).toBeInTheDocument();
  });

  it('확인을 누르면 그때 내 역할이 저장된다', () => {
    renderPage();
    fireEvent.change(역할셀렉트('이예준'), { target: { value: 'EMPLOYEE' } });

    fireEvent.click(screen.getByRole('button', { name: '사원(으)로 변경' }));

    expect(roleMutate).toHaveBeenCalledWith(
      expect.objectContaining({ id: 100, role: 'EMPLOYEE' }),
      expect.anything(),
    );
  });

  // 배정된 부서가 비활성이 되어도 선택지에 남겨야 한다. 빼면 브라우저가 첫 항목을 대신 보여줘
  // 관리자는 부서가 조용히 바뀐 것으로 오해한다.
  it('비활성이 된 소속 부서도 선택지에 남는다', () => {
    const 폐지된팀사원 = { ...남, departmentId: 9, departmentName: '폐지된팀' };
    renderPage([폐지된팀사원], [개발팀, 디자인팀, 폐지된팀]);

    expect(부서셀렉트('박준호')).toHaveValue('9');
    expect(screen.getByRole('option', { name: /폐지된팀/ })).toBeInTheDocument();
  });

  it('같은 값을 다시 고르면 저장하지 않는다', () => {
    renderPage();

    fireEvent.change(역할셀렉트('박준호'), { target: { value: 'EMPLOYEE' } });

    expect(roleMutate).not.toHaveBeenCalled();
  });
});

describe('AdminMembersPage 퇴직 복구', () => {
  const 퇴직자 = { ...남, name: '최민서', retiredAt: '2026-05-01' };

  beforeEach(() => {
    vi.clearAllMocks();
  });

  /**
   * 퇴직 탭으로 옮긴다.
   * 이름을 `/퇴직/`로 찾으면 안 된다 — 재직 탭의 "퇴직 처리" 아이콘 버튼까지 걸린다.
   * 탭 버튼의 접근성 이름은 라벨 + 건수 배지라 "퇴직1"이므로 끝까지 고정한다.
   */
  function 퇴직탭(retired = [퇴직자]) {
    renderPage([나], [개발팀], retired);
    fireEvent.click(screen.getByRole('button', { name: /^퇴직\d*$/ }));
  }

  it('퇴직 탭의 각 행에 복구 버튼이 있다', () => {
    퇴직탭();

    expect(screen.getByRole('button', { name: '퇴직 복구' })).toBeInTheDocument();
  });

  // 복구는 퇴직의 완전한 역연산이 아니다. "퇴직을 취소한다"고만 읽으면 관리자는 팀장직과
  // 결재까지 되돌아온다고 생각하고, 그 부서 결재선이 어긋난 채 방치된다.
  it('복구 전에 되살아나지 않는 것을 알린다', () => {
    퇴직탭();

    fireEvent.click(screen.getByRole('button', { name: '퇴직 복구' }));

    expect(restoreMutate).not.toHaveBeenCalled();
    expect(screen.getByText(/해제된 팀장직과 다른 승인자에게 넘어간 결재는 되돌아오지 않습니다/)).toBeInTheDocument();
  });

  it('확인을 누르면 그때 복구한다', () => {
    퇴직탭();
    fireEvent.click(screen.getByRole('button', { name: '퇴직 복구' }));

    fireEvent.click(screen.getByRole('button', { name: '복구' }));

    expect(restoreMutate).toHaveBeenCalledWith(퇴직자.id, expect.anything());
  });

  it('재직 탭에는 복구 버튼이 없다', () => {
    renderPage();

    expect(screen.queryByRole('button', { name: '퇴직 복구' })).not.toBeInTheDocument();
  });
});
