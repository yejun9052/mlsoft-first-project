import { beforeEach, describe, expect, it, vi } from 'vitest';
import { act, fireEvent, render, screen } from '@testing-library/react';
import { useState } from 'react';
import {
  Link,
  RouterProvider,
  createMemoryRouter,
} from 'react-router-dom';
import MyInfoPage from './MyInfoPage.jsx';
import { useCurrentUser } from '../hooks/useAuth.js';
import { useHolidays } from '../hooks/useHolidays.js';
import { useLeaveSummary, useMyAnnualUsage } from '../hooks/useLeaves.js';
import { useUpdateMyProfile } from '../hooks/useUsers.js';

vi.mock('react-hot-toast', () => ({ default: { success: vi.fn(), error: vi.fn() } }));
vi.mock('../hooks/useAuth.js', () => ({ useCurrentUser: vi.fn() }));
vi.mock('../hooks/useHolidays.js', () => ({ useHolidays: vi.fn() }));
vi.mock('../hooks/useLeaves.js', () => ({
  useLeaveSummary: vi.fn(),
  useMyAnnualUsage: vi.fn(),
}));
vi.mock('../hooks/useUsers.js', () => ({ useUpdateMyProfile: vi.fn() }));

const ME = {
  name: '홍길동',
  birthDay: '1995-03-14',
  email: 'hong@mlsoft.example',
  role: 'EMPLOYEE',
  position: '선임',
  departmentName: '개발팀',
  hireDate: '2020-01-02',
};

const SUMMARY = {
  baseDays: '15.0',
  bonusDays: '0.0',
  useDays: '3.0',
  pendingDays: '1.0',
  remainingDays: '12.0',
  nextResetDate: '2027-01-02',
};

// 화면 안쪽에서 다시 렌더시키는 손잡이. 아래 applyServerResponse 주석 참고
let rerenderPage = () => {};

// 페이지에는 링크가 없다 — 사이드바 메뉴를 대신할 링크를 옆에 두고 그걸 눌러 이탈을 흉내 낸다
function MyInfoHarness() {
  const [, setTick] = useState(0);
  rerenderPage = () => setTick((tick) => tick + 1);
  return (
    <>
      <MyInfoPage />
      <Link to="/dashboard">대시보드로</Link>
    </>
  );
}

function createTestRouter() {
  return createMemoryRouter(
    [
      {
        path: '/myinfo',
        element: <MyInfoHarness />,
      },
      {
        path: '/dashboard',
        element: <div>대시보드 화면</div>,
      },
    ],
    {
      initialEntries: ['/myinfo'],
    },
  );
}

function setMe(me) {
  useCurrentUser.mockReturnValue({ data: me, isError: false, refetch: vi.fn() });
}

function renderWithRouter() {
  const router = createTestRouter();
  const result = render(<RouterProvider router={router} />);

  return {
    router,
    ...result,
  };
}

function renderPage(me = ME) {
  setMe(me);
  return renderWithRouter();
}

// 아직 응답 전 — react-query의 data는 undefined다.
// renderPage(undefined)로는 이 상태를 만들 수 없다. **기본 인자가 걸려 ME가 들어간다** —
// 그래서 아래 검증이 로딩 상태를 한 번도 그리지 않고 통과했다.
function renderBeforeLoad() {
  setMe(undefined);
  return renderWithRouter();
}

/**
 * 서버 응답이 뒤늦게 도착한 상황을 만든다 — 모킹된 값을 바꾸고 **같은 인스턴스를 유지한 채** 다시 그린다.
 *
 * <p>{@code rerender(<RouterProvider router={router} />)}로는 안 된다. RouterProvider는 라우터
 * 상태를 스스로 구독하므로 props가 같으면 React가 라우트 요소까지 내려가지 않는다 — 화면은
 * 옛 값을 그대로 들고 있다. 반대로 key를 바꿔 다시 마운트하면 <b>폼 상태가 초기화돼</b>
 * 이 검증들이 보려는 것(첫 렌더 이후의 재동기화)이 사라진다.
 */
function applyServerResponse(me) {
  setMe(me);
  act(() => rerenderPage());
}

function nameInput() {
  return screen.getByDisplayValue('홍길동');
}

function goToDashboard() {
  fireEvent.click(screen.getByText('대시보드로'));
}

function dialogTitle() {
  return screen.queryByText('저장하지 않고 나가시겠습니까?');
}

describe('MyInfoPage', () => {
  beforeEach(() => {
    vi.clearAllMocks();
    useLeaveSummary.mockReturnValue({ data: SUMMARY, isError: false, refetch: vi.fn() });
    useMyAnnualUsage.mockReturnValue({
      data: [{ date: '2026-01-05', days: '0.5' }],
      isLoading: false,
      isError: false,
      refetch: vi.fn(),
    });
    useHolidays.mockReturnValue({ data: [], isError: false });
    useUpdateMyProfile.mockReturnValue({ mutate: vi.fn(), isPending: false });
  });

  // useState 초기값은 첫 렌더에 한 번만 쓰인다. localStorage 부트스트랩이 없으면 그 첫 렌더에
  // me가 undefined라 폼이 빈 칸으로 굳고, 그대로 저장하면 "모두 입력해 주세요"가 뜬다.
  it('서버 값이 늦게 도착해도 폼이 그 값으로 채워진다', () => {
    renderBeforeLoad();

    applyServerResponse(ME);

    expect(screen.getByDisplayValue('홍길동')).toBeInTheDocument();
    expect(screen.getByDisplayValue('1995-03-14')).toBeInTheDocument();
  });

  // localStorage 값은 낡을 수 있다(initialDataUpdatedAt: 0이라 매번 재검증한다).
  // 폼이 옛 값을 든 채로 있으면 **손대지도 않았는데** 미저장으로 잡혀 이동이 막힌다.
  it('저장값이 낡았으면 서버 값으로 갈아 끼우고, 손대지 않았으므로 붙잡지 않는다', async () => {
    const { router } = renderPage({ ...ME, name: '옛이름' });

    applyServerResponse(ME);
    expect(screen.getByDisplayValue('홍길동')).toBeInTheDocument();

    goToDashboard();

    expect(dialogTitle()).toBeNull();
    expect(await screen.findByText('대시보드 화면')).toBeInTheDocument();
    expect(router.state.location.pathname).toBe('/dashboard');
  });

  it('이름을 바꾸고 나가려 하면 붙잡는다', () => {
    const { router } = renderPage();

    fireEvent.change(nameInput(), { target: { value: '홍길순' } });
    goToDashboard();

    expect(dialogTitle()).toBeInTheDocument();
    expect(router.state.location.pathname).toBe('/myinfo');
  });

  it('생년월일만 바꿔도 붙잡는다', () => {
    const { router } = renderPage();

    fireEvent.change(
      screen.getByDisplayValue('1995-03-14'),
      { target: { value: '1995-03-15' } },
    );
    goToDashboard();

    expect(dialogTitle()).toBeInTheDocument();
    expect(router.state.location.pathname).toBe('/myinfo');
  });

  it('직책만 바꾸고 나가려 해도 붙잡는다', () => {
    const { router } = renderPage();

    fireEvent.change(screen.getByDisplayValue('선임'), { target: { value: '책임' } });
    goToDashboard();

    expect(dialogTitle()).toBeInTheDocument();
    expect(router.state.location.pathname).toBe('/myinfo');
  });

  it('직책을 이름·생년월일과 함께 저장한다', () => {
    const mutate = vi.fn();
    useUpdateMyProfile.mockReturnValue({ mutate, isPending: false });
    renderPage();

    fireEvent.change(screen.getByDisplayValue('선임'), { target: { value: ' 책임 연구원 ' } });
    fireEvent.click(screen.getByRole('button', { name: '저장' }));

    expect(mutate).toHaveBeenCalledWith(
      {
        name: '홍길동',
        birthDay: '1995-03-14',
        position: '책임 연구원',
      },
      expect.objectContaining({ onSuccess: expect.any(Function) }),
    );
  });

  it('연도 선택을 바꾸면 개인 기록과 공휴일을 같은 연도로 조회한다', () => {
    renderPage();

    fireEvent.change(screen.getByLabelText('히트맵 연도'), { target: { value: '2025' } });

    expect(useMyAnnualUsage).toHaveBeenLastCalledWith(2025);
    expect(useHolidays).toHaveBeenLastCalledWith(2025);
  });

  it('취소하면 화면에 남고 고치던 값도 그대로다', () => {
    const { router } = renderPage();
    fireEvent.change(nameInput(), { target: { value: '홍길순' } });
    goToDashboard();

    fireEvent.click(screen.getByText('취소'));

    expect(dialogTitle()).toBeNull();
    expect(router.state.location.pathname).toBe('/myinfo');
    expect(screen.getByDisplayValue('홍길순')).toBeInTheDocument();
  });

  it('그냥 나가기를 누르면 이동한다', async () => {
    const { router } = renderPage();
    fireEvent.change(nameInput(), { target: { value: '홍길순' } });
    goToDashboard();

    fireEvent.click(screen.getByText('그냥 나가기'));

    expect(await screen.findByText('대시보드 화면')).toBeInTheDocument();
    expect(router.state.location.pathname).toBe('/dashboard');
  });

  it('다음 회차 예약이 0이면 연차 요약에 다음 회차 예약 행이 없다', () => {
    renderPage();

    expect(screen.queryByText('다음 회차 예약')).not.toBeInTheDocument();
  });

  it('다음 회차 예약이 있으면 연차 요약에 일수가 보인다', () => {
    useLeaveSummary.mockReturnValue({
      data: { ...SUMMARY, nextCycleReservedDays: '4.0' },
      isError: false,
      refetch: vi.fn(),
    });

    renderPage();

    expect(screen.getByText('다음 회차 예약').closest('div')).toHaveTextContent('4.0일');
  });

  it('저장돼 서버 값이 따라오면 더 이상 붙잡지 않는다', async () => {
    const { router } = renderPage();
    fireEvent.change(nameInput(), { target: { value: '홍길순' } });

    // 저장 성공 → ['auth'] 무효화 → 재조회가 새 이름을 물고 온다
    applyServerResponse({ ...ME, name: '홍길순' });
    goToDashboard();

    expect(dialogTitle()).toBeNull();
    expect(await screen.findByText('대시보드 화면')).toBeInTheDocument();
    expect(router.state.location.pathname).toBe('/dashboard');
  });
});
