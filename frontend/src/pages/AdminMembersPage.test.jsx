import { beforeEach, describe, expect, it, vi } from 'vitest';
import { fireEvent, render, screen, within } from '@testing-library/react';
import { createMemoryRouter, RouterProvider } from 'react-router-dom';
import AdminMembersPage from './AdminMembersPage.jsx';
import {
  usePlacePurgeHold,
  usePurgeUser,
  useRehireUser,
  useReleasePurgeHold,
  useRestoreUser,
  useRetiredUsers,
  useRetireUser,
  useUpdateUserDepartment,
  useUpdateUserRole,
  useUpdateUserRoleAndDepartment,
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
  usePlacePurgeHold: vi.fn(),
  usePurgeUser: vi.fn(),
  useRehireUser: vi.fn(),
  useReleasePurgeHold: vi.fn(),
  useRestoreUser: vi.fn(),
  useRetiredUsers: vi.fn(),
  useRetireUser: vi.fn(),
  useUpdateUserDepartment: vi.fn(),
  useUpdateUserRole: vi.fn(),
  useUpdateUserRoleAndDepartment: vi.fn(),
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
const roleAndDepartmentMutate = vi.fn();
const restoreMutate = vi.fn();
const rehireMutate = vi.fn();
const purgeMutate = vi.fn();
const placeHoldMutate = vi.fn();
const releaseHoldMutate = vi.fn();

const 개발팀 = { id: 1, name: '개발팀', parentId: null, active: true, unassigned: false };
const 디자인팀 = { id: 2, name: '디자인팀', parentId: null, active: true, unassigned: false };
const 개발1팀 = { id: 3, name: '개발1팀', parentId: 1, active: true, unassigned: false };
const 미배정부서 = { id: 4, name: '표시 이름은 식별자가 아님', parentId: null, active: true, unassigned: true };
const 폐지된팀 = { id: 9, name: '폐지된팀', parentId: null, active: false, unassigned: false };

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
  useRehireUser.mockReturnValue({ mutate: rehireMutate, isPending: false });
  useDepartments.mockReturnValue({ data: departments, isLoading: false });
  usePendingOnboardings.mockReturnValue({ data: { content: [], page: { totalElements: 0 } }, isLoading: false, isError: false, refetch: vi.fn() });
  useApproveOnboarding.mockReturnValue({ mutate: vi.fn(), isPending: false });
  useRejectOnboarding.mockReturnValue({ mutate: vi.fn(), isPending: false });
  useUpdateUserRole.mockReturnValue({ mutate: roleMutate, isPending: false });
  useUpdateUserDepartment.mockReturnValue({ mutate: deptMutate, isPending: false });
  useUpdateUserRoleAndDepartment.mockReturnValue({
    mutate: roleAndDepartmentMutate,
    isPending: false,
  });
  useRetireUser.mockReturnValue({ mutate: vi.fn(), isPending: false });
  usePurgeUser.mockReturnValue({ mutate: purgeMutate, isPending: false });
  usePlacePurgeHold.mockReturnValue({ mutate: placeHoldMutate, isPending: false });
  useReleasePurgeHold.mockReturnValue({ mutate: releaseHoldMutate, isPending: false });
  useCurrentUser.mockReturnValue({ data: { id: 나.id } });

  // 앱이 데이터 라우터를 사용하므로 실제 라우팅 컨텍스트와 같은 방식으로 렌더한다.
  const router = createMemoryRouter(
    [{ path: '/', element: <AdminMembersPage /> }],
    { initialEntries: ['/'] },
  );
  return render(<RouterProvider router={router} />);
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

  it('부서가 있는 사원을 팀장으로 올리면 부서 선택이 나타나지 않는다', () => {
    renderPage();

    fireEvent.change(역할셀렉트('박준호'), { target: { value: 'TEAM_LEADER' } });

    expect(screen.queryByLabelText('박준호님의 팀장 부서')).not.toBeInTheDocument();
    expect(screen.getByText(/박준호님을 개발팀 팀장으로 정말 지정하시겠습니까/)).toBeInTheDocument();
  });

  it('부서가 없는 사원은 확인 창에서 부서를 고르기 전 진행할 수 없다', () => {
    const 미배정 = { ...남, departmentId: null, departmentName: null };
    renderPage([나, 미배정]);

    fireEvent.change(역할셀렉트('박준호'), { target: { value: 'TEAM_LEADER' } });

    expect(screen.getByLabelText('박준호님의 팀장 부서')).toHaveValue('');
    expect(screen.getByRole('button', { name: '팀장(으)로 변경' })).toBeDisabled();
    expect(roleMutate).not.toHaveBeenCalled();
    expect(roleAndDepartmentMutate).not.toHaveBeenCalled();
  });

  it('미배정 사원의 부서를 골라 확정하면 새 엔드포인트 뮤테이션만 한 번 호출한다', () => {
    const 미배정 = { ...남, departmentId: null, departmentName: null };
    renderPage([나, 미배정]);

    fireEvent.change(역할셀렉트('박준호'), { target: { value: 'TEAM_LEADER' } });
    fireEvent.change(screen.getByLabelText('박준호님의 팀장 부서'), {
      target: { value: '2' },
    });
    fireEvent.click(screen.getByRole('button', { name: '팀장(으)로 변경' }));

    expect(roleAndDepartmentMutate).toHaveBeenCalledTimes(1);
    expect(roleAndDepartmentMutate).toHaveBeenCalledWith(
      {
        id: 200,
        role: 'TEAM_LEADER',
        departmentId: 2,
      },
      expect.anything(),
    );
    expect(roleMutate).not.toHaveBeenCalled();
    expect(deptMutate).not.toHaveBeenCalled();
  });

  it('미배정 승격의 부서 선택지는 상위 부서 바로 뒤에 하위 부서가 온다', () => {
    const 미배정 = { ...남, departmentId: null, departmentName: null };
    // API 원본 순서에서는 개발1팀이 디자인팀 뒤지만 화면은 계층 순서로 재배열해야 한다.
    renderPage([나, 미배정], [개발팀, 디자인팀, 개발1팀]);

    fireEvent.change(역할셀렉트('박준호'), { target: { value: 'TEAM_LEADER' } });

    const optionLabels = Array.from(
      screen.getByLabelText('박준호님의 팀장 부서').options,
      (option) => option.textContent,
    );
    expect(optionLabels).toEqual([
      '부서를 선택해 주세요',
      '개발팀',
      '  ↳ 개발1팀',
      '디자인팀',
    ]);
  });

  it('실제 미배정 부서 소속도 팀장 승격 전에 새 부서를 고르게 한다', () => {
    const 미배정소속 = {
      ...남,
      departmentId: 미배정부서.id,
      departmentName: 미배정부서.name,
    };
    renderPage([나, 미배정소속], [개발팀, 디자인팀, 미배정부서]);

    fireEvent.change(역할셀렉트('박준호'), { target: { value: 'TEAM_LEADER' } });

    expect(screen.getByLabelText('박준호님의 팀장 부서')).toHaveValue('');
    expect(screen.getByRole('button', { name: '팀장(으)로 변경' })).toBeDisabled();
  });

  it('팀장 승격 부서 선택지에서는 미배정 플래그가 있는 부서를 제외한다', () => {
    const 미배정소속 = {
      ...남,
      departmentId: 미배정부서.id,
      departmentName: 미배정부서.name,
    };
    renderPage([나, 미배정소속], [개발팀, 디자인팀, 미배정부서]);

    fireEvent.change(역할셀렉트('박준호'), { target: { value: 'TEAM_LEADER' } });

    const select = screen.getByLabelText('박준호님의 팀장 부서');
    expect(within(select).queryByRole('option', { name: 미배정부서.name })).not.toBeInTheDocument();
    expect(within(select).getByRole('option', { name: '개발팀' })).toBeInTheDocument();
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

  it('본인 행의 퇴직 버튼은 비활성이고 남의 행은 활성이다', () => {
    renderPage();

    const selfRetireButton = screen.getByRole('button', {
      name: /본인 계정은 퇴직 처리할 수 없습니다/,
    });

    expect(selfRetireButton).toBeDisabled();
    expect(selfRetireButton).toHaveAttribute('title', expect.stringContaining('다른 관리자에게 요청'));
    expect(screen.getByRole('button', { name: '퇴직 처리' })).toBeEnabled();
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
    vi.useFakeTimers();
    vi.setSystemTime(new Date('2026-08-20T12:00:00+09:00'));
  });

  afterEach(() => {
    vi.useRealTimers();
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

  // 경과 기간은 서버가 계산해 내려준다(UserResponse.elapsedYears/elapsedMonths) — 파기 가능
  // 여부 판정과 같은 기준을 써야 하므로 화면이 dayjs로 다시 세지 않는다.
  it('퇴직일과 오늘 사이의 경과 기간을 "N년 N개월" 형태로 함께 보여준다', () => {
    퇴직탭([{ ...퇴직자, retiredAt: '2026-05-03', elapsedYears: 0, elapsedMonths: 3 }]);

    expect(screen.getByText('2026-05-03 (3개월)')).toBeInTheDocument();
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

describe('AdminMembersPage 재입사 처리', () => {
  const 퇴직자 = { ...남, name: '최민서', retiredAt: '2026-05-01' };
  const 파기된자 = {
    ...남,
    name: '퇴직사원#999',
    retiredAt: '2018-01-01',
    purgedAt: '2026-01-01T00:00:00',
  };

  beforeEach(() => {
    vi.clearAllMocks();
  });

  function 퇴직탭(retired = [퇴직자]) {
    renderPage([나], [개발팀], retired);
    fireEvent.click(screen.getByRole('button', { name: /^퇴직\d*$/ }));
  }

  // 이름만으로는 두 버튼이 구분되지 않는다 — 화면이 언제 어느 것을 쓰는지 함께 말해야 한다
  // (설계-초안 §3, §6, 지시서).
  it('퇴직 탭에 복구·재입사 두 버튼이 있고 설명이 서로 다르다', () => {
    퇴직탭();

    expect(screen.getByRole('button', { name: '퇴직 복구' })).toBeInTheDocument();
    expect(screen.getByRole('button', { name: '재입사 처리' })).toBeInTheDocument();
    expect(screen.getByText(/착오 처리를 되돌리거나 계속근로로\s*인정할 때 씁니다/)).toBeInTheDocument();
    expect(screen.getByText(/그만뒀다 다시 입사했을 때 씁니다/)).toBeInTheDocument();
  });

  it('파기된 행에는 재입사 버튼이 없다', () => {
    퇴직탭([파기된자]);

    expect(screen.queryByRole('button', { name: '재입사 처리' })).not.toBeInTheDocument();
    // 복구 버튼은 파기와 무관하게 그대로 남는다 (기존 규칙)
    expect(screen.getByRole('button', { name: '퇴직 복구' })).toBeInTheDocument();
  });

  it('재입사일을 비우면 실행 버튼이 잠긴다', () => {
    퇴직탭();
    fireEvent.click(screen.getByRole('button', { name: '재입사 처리' }));

    const dialog = screen.getByRole('dialog', { name: '재입사 처리' });
    expect(within(dialog).getByRole('button', { name: '재입사 처리' })).toBeDisabled();
    expect(rehireMutate).not.toHaveBeenCalled();
  });

  it('퇴직일보다 앞선 재입사일이면 실행이 막히고 이유가 보인다', () => {
    퇴직탭();
    fireEvent.click(screen.getByRole('button', { name: '재입사 처리' }));

    const dialog = screen.getByRole('dialog', { name: '재입사 처리' });
    fireEvent.change(within(dialog).getByLabelText(`${퇴직자.name}님의 재입사일`), {
      target: { value: '2026-04-30' },
    });

    expect(within(dialog).getByRole('button', { name: '재입사 처리' })).toBeDisabled();
    expect(within(dialog).getByText(/퇴직일\(2026-05-01\)보다 앞설 수 없습니다/)).toBeInTheDocument();
    expect(rehireMutate).not.toHaveBeenCalled();
  });

  it('퇴직일과 재입사일이 같으면 공백 경고가 뜨지만 실행은 막지 않는다', () => {
    퇴직탭();
    fireEvent.click(screen.getByRole('button', { name: '재입사 처리' }));

    const dialog = screen.getByRole('dialog', { name: '재입사 처리' });
    fireEvent.change(within(dialog).getByLabelText(`${퇴직자.name}님의 재입사일`), {
      target: { value: '2026-05-01' },
    });

    expect(within(dialog).getByText(/공백이 없습니다. 계속근로로 인정해야 하는지 확인하세요/)).toBeInTheDocument();
    expect(within(dialog).getByRole('button', { name: '재입사 처리' })).toBeEnabled();
  });

  it('정상 입력 시 mutation이 올바른 body로 호출된다', () => {
    퇴직탭();
    fireEvent.click(screen.getByRole('button', { name: '재입사 처리' }));

    const dialog = screen.getByRole('dialog', { name: '재입사 처리' });
    fireEvent.change(within(dialog).getByLabelText(`${퇴직자.name}님의 재입사일`), {
      target: { value: '2026-09-08' },
    });
    fireEvent.change(within(dialog).getByLabelText(`${퇴직자.name}님의 재입사 부서`), {
      target: { value: '1' },
    });
    fireEvent.click(within(dialog).getByRole('button', { name: '재입사 처리' }));

    expect(rehireMutate).toHaveBeenCalledWith(
      { id: 퇴직자.id, hireDate: '2026-09-08', departmentId: 1 },
      expect.anything(),
    );
  });

  it('부서를 고르지 않으면 미배정(null)으로 호출된다', () => {
    퇴직탭();
    fireEvent.click(screen.getByRole('button', { name: '재입사 처리' }));

    const dialog = screen.getByRole('dialog', { name: '재입사 처리' });
    fireEvent.change(within(dialog).getByLabelText(`${퇴직자.name}님의 재입사일`), {
      target: { value: '2026-09-08' },
    });
    fireEvent.click(within(dialog).getByRole('button', { name: '재입사 처리' }));

    expect(rehireMutate).toHaveBeenCalledWith(
      { id: 퇴직자.id, hireDate: '2026-09-08', departmentId: null },
      expect.anything(),
    );
  });
});

describe('AdminMembersPage 표 가로 스크롤에서도 관리 열이 보인다', () => {
  // 컬럼이 많아 표가 가로로 스크롤되면 관리 열(복구·재입사·보류·파기)이 오른쪽 밖으로 잘려
  // 있는지조차 모르는 결함이었다 (B-2). sticky로 고정해 항상 보이게 한다.
  const 퇴직자 = { ...남, name: '최민서', retiredAt: '2026-05-01' };

  beforeEach(() => {
    vi.clearAllMocks();
  });

  it('재직 탭의 관리 열은 오른쪽에 고정된다', () => {
    renderPage();

    const 관리열 = screen.getByRole('columnheader', { name: '관리' });
    expect(관리열.className).toContain('sticky');
    expect(관리열.className).toContain('right-0');
  });

  it('퇴직 탭의 관리 열도 오른쪽에 고정된다', () => {
    renderPage([나], [개발팀], [퇴직자]);
    fireEvent.click(screen.getByRole('button', { name: /^퇴직\d*$/ }));

    const 관리열 = screen.getByRole('columnheader', { name: '관리' });
    expect(관리열.className).toContain('sticky');
    expect(관리열.className).toContain('right-0');
  });
});

describe('AdminMembersPage 퇴직자 파기', () => {
  // 3년 이상 지나 파기할 수 있는 사람 — purgeEligible은 서버가 보존 기간·보류·파기 여부를
  // 모두 반영해 계산해 내려주므로(UserService.toRetiredResponse) 화면은 이 값 하나만 본다.
  const 파기가능자 = {
    ...남,
    id: 301,
    name: '김파기',
    retiredAt: '2020-01-01',
    elapsedYears: 6,
    elapsedMonths: 8,
    purgedAt: null,
    purgeHoldReason: null,
    purgeEligible: true,
  };
  const 파기미달자 = {
    ...남,
    id: 302,
    name: '이미달',
    retiredAt: '2026-01-01',
    elapsedYears: 0,
    elapsedMonths: 8,
    purgedAt: null,
    purgeHoldReason: null,
    purgeEligible: false,
  };
  const 보류자 = {
    ...남,
    id: 303,
    name: '박보류',
    retiredAt: '2019-01-01',
    elapsedYears: 7,
    elapsedMonths: 8,
    purgedAt: null,
    purgeHoldReason: '재입사 예정',
    purgeEligible: false,
  };
  const 파기된자 = {
    ...남,
    id: 304,
    name: '퇴직사원#304',
    email: 'deleted-304@invalid',
    position: null,
    hireDate: null,
    retiredAt: '2018-01-01',
    elapsedYears: 8,
    elapsedMonths: 8,
    purgedAt: '2026-01-01T00:00:00',
    purgeHoldReason: null,
    purgeEligible: false,
  };

  beforeEach(() => {
    vi.clearAllMocks();
  });

  // 라벨에 파기 대상 배지가 붙으면 접근성 이름이 "퇴직 · 파기대상 N..."으로 늘어나므로
  // 다른 describe의 `/^퇴직\d*$/`처럼 끝까지 고정하지 않고 시작만 맞춘다.
  function 퇴직탭(retired) {
    renderPage([나], [개발팀], retired);
    fireEvent.click(screen.getByRole('button', { name: /^퇴직/ }));
  }

  it('3년 미만 행의 파기 버튼이 비활성이고 사유가 보인다', () => {
    퇴직탭([파기미달자]);

    const button = screen.getByRole('button', {
      name: '퇴직 후 3년이 지나야 파기할 수 있습니다.',
    });
    expect(button).toBeDisabled();
  });

  it('3년 이상 지난 행은 파기 버튼이 활성이다', () => {
    퇴직탭([파기가능자]);

    expect(screen.getByRole('button', { name: '파기' })).toBeEnabled();
  });

  it('파기 모달에서 이름을 틀리게 입력하면 실행 버튼이 잠겨 있다', () => {
    퇴직탭([파기가능자]);
    fireEvent.click(screen.getByRole('button', { name: '파기' }));

    const dialog = screen.getByRole('dialog', { name: '퇴직자 개인정보 파기' });
    fireEvent.change(within(dialog).getByLabelText(`${파기가능자.name}님 이름 확인`), {
      target: { value: '다른이름' },
    });

    expect(within(dialog).getByRole('button', { name: '파기' })).toBeDisabled();
    expect(purgeMutate).not.toHaveBeenCalled();
  });

  it('이름을 정확히 입력하면 실행 버튼이 열리고 파기 mutation이 호출된다', () => {
    퇴직탭([파기가능자]);
    fireEvent.click(screen.getByRole('button', { name: '파기' }));

    const dialog = screen.getByRole('dialog', { name: '퇴직자 개인정보 파기' });
    fireEvent.change(within(dialog).getByLabelText(`${파기가능자.name}님 이름 확인`), {
      target: { value: 파기가능자.name },
    });
    fireEvent.click(within(dialog).getByRole('button', { name: '파기' }));

    expect(purgeMutate).toHaveBeenCalledWith(파기가능자.id, expect.anything());
  });

  it('보류된 행은 사유가 보이고 파기 버튼이 잠긴다', () => {
    퇴직탭([보류자]);

    expect(screen.getByText(/보류: 재입사 예정/)).toBeInTheDocument();
    const purgeButton = screen.getByRole('button', {
      name: '파기 보류 상태입니다. 보류를 해제한 뒤 다시 시도해주세요.',
    });
    expect(purgeButton).toBeDisabled();
    expect(screen.getByRole('button', { name: '파기 보류 해제' })).toBeInTheDocument();
  });

  it('보류 해제 버튼을 누르면 바로 해제한다', () => {
    퇴직탭([보류자]);

    fireEvent.click(screen.getByRole('button', { name: '파기 보류 해제' }));

    expect(releaseHoldMutate).toHaveBeenCalledWith(보류자.id, expect.anything());
  });

  it('보류 설정은 사유 입력을 받아야 실행된다', () => {
    퇴직탭([파기가능자]);
    fireEvent.click(screen.getByRole('button', { name: '파기 보류' }));

    const dialog = screen.getByRole('dialog', { name: '파기 보류' });
    expect(within(dialog).getByRole('button', { name: '보류' })).toBeDisabled();

    fireEvent.change(within(dialog).getByLabelText(`${파기가능자.name}님의 파기 보류 사유`), {
      target: { value: '재입사 예정' },
    });
    fireEvent.click(within(dialog).getByRole('button', { name: '보류' }));

    expect(placeHoldMutate).toHaveBeenCalledWith(
      { id: 파기가능자.id, reason: '재입사 예정' },
      expect.anything(),
    );
  });

  it('이미 파기된 행에는 파기·보류 버튼이 없고 이름이 익명화된 채로 보인다', () => {
    퇴직탭([파기된자]);

    expect(screen.getByText('퇴직사원#304')).toBeInTheDocument();
    expect(screen.queryByRole('button', { name: '파기' })).not.toBeInTheDocument();
    expect(screen.queryByRole('button', { name: '파기 보류' })).not.toBeInTheDocument();
    expect(screen.queryByRole('button', { name: '파기 보류 해제' })).not.toBeInTheDocument();
    // 스펙이 명시한 것은 파기·보류 버튼뿐이다 — 복구 버튼은 그대로 남는다
    expect(screen.getByRole('button', { name: '퇴직 복구' })).toBeInTheDocument();
  });

  // 라벨과 Tabs 자체의 건수 배지(전체 퇴직자 수)가 한 버튼 안에 같이 붙으므로 접근성 이름은
  // 부분 일치로 확인한다 — 정확히 일치시키면 전체 건수가 바뀔 때마다 깨진다.
  it('파기 대상 건수 배지가 맞게 나온다', () => {
    renderPage([나], [개발팀], [파기가능자, 파기미달자, 보류자, 파기된자]);

    expect(screen.getByRole('button', { name: /파기대상 1/ })).toBeInTheDocument();
  });

  it('파기 대상이 없으면 배지를 띄우지 않는다', () => {
    renderPage([나], [개발팀], [파기미달자, 보류자]);

    expect(screen.queryByRole('button', { name: /파기대상/ })).not.toBeInTheDocument();
  });
});
