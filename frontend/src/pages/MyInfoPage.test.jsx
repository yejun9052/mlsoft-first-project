import { beforeEach, describe, expect, it, vi } from 'vitest';
import { fireEvent, render, screen } from '@testing-library/react';
import { BrowserRouter, Link } from 'react-router-dom';
import MyInfoPage from './MyInfoPage.jsx';
import { useCurrentUser } from '../hooks/useAuth.js';
import { useLeaveSummary } from '../hooks/useLeaves.js';
import { useUpdateMyProfile } from '../hooks/useUsers.js';

vi.mock('react-hot-toast', () => ({ default: { success: vi.fn(), error: vi.fn() } }));
vi.mock('../hooks/useAuth.js', () => ({ useCurrentUser: vi.fn() }));
vi.mock('../hooks/useLeaves.js', () => ({ useLeaveSummary: vi.fn() }));
vi.mock('../hooks/useUsers.js', () => ({ useUpdateMyProfile: vi.fn() }));

const ME = {
  name: '홍길동',
  birthDay: '1995-03-14',
  email: 'hong@mlsoft.example',
  role: 'EMPLOYEE',
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

// 페이지에는 링크가 없다 — 사이드바 메뉴를 대신할 링크를 옆에 두고 그걸 눌러 이탈을 흉내 낸다
function tree() {
  return (
    <BrowserRouter>
      <MyInfoPage />
      <Link to="/dashboard">대시보드로</Link>
    </BrowserRouter>
  );
}

function setMe(me) {
  useCurrentUser.mockReturnValue({ data: me, isError: false, refetch: vi.fn() });
}

function renderPage(me = ME) {
  setMe(me);
  window.history.pushState({}, '', '/myinfo');
  return render(tree());
}

// 아직 응답 전 — react-query의 data는 undefined다.
// renderPage(undefined)로는 이 상태를 만들 수 없다. **기본 인자가 걸려 ME가 들어간다** —
// 그래서 아래 검증이 로딩 상태를 한 번도 그리지 않고 통과했다.
function renderBeforeLoad() {
  setMe(undefined);
  window.history.pushState({}, '', '/myinfo');
  return render(tree());
}

const nameInput = () => screen.getByDisplayValue('홍길동');
const goToDashboard = () => fireEvent.click(screen.getByText('대시보드로'));
const dialogTitle = () => screen.queryByText('저장하지 않고 나가시겠습니까?');

describe('MyInfoPage', () => {
  beforeEach(() => {
    vi.clearAllMocks();
    useLeaveSummary.mockReturnValue({ data: SUMMARY, isError: false, refetch: vi.fn() });
    useUpdateMyProfile.mockReturnValue({ mutate: vi.fn(), isPending: false });
  });

  // useState 초기값은 첫 렌더에 한 번만 쓰인다. localStorage 부트스트랩이 없으면 그 첫 렌더에
  // me가 undefined라 폼이 빈 칸으로 굳고, 그대로 저장하면 "모두 입력해 주세요"가 뜬다.
  it('서버 값이 늦게 도착해도 폼이 그 값으로 채워진다', () => {
    const { rerender } = renderBeforeLoad();

    setMe(ME);
    rerender(tree());

    expect(screen.getByDisplayValue('홍길동')).toBeInTheDocument();
    expect(screen.getByDisplayValue('1995-03-14')).toBeInTheDocument();
  });

  // localStorage 값은 낡을 수 있다(initialDataUpdatedAt: 0이라 매번 재검증한다).
  // 폼이 옛 값을 든 채로 있으면 **손대지도 않았는데** 미저장으로 잡혀 이동이 막힌다.
  it('저장값이 낡았으면 서버 값으로 갈아 끼우고, 손대지 않았으므로 붙잡지 않는다', () => {
    const { rerender } = renderPage({ ...ME, name: '옛이름' });

    setMe(ME);
    rerender(tree());
    expect(screen.getByDisplayValue('홍길동')).toBeInTheDocument();

    goToDashboard();

    expect(dialogTitle()).toBeNull();
    expect(window.location.pathname).toBe('/dashboard');
  });

  it('이름을 바꾸고 나가려 하면 붙잡는다', () => {
    renderPage();

    fireEvent.change(nameInput(), { target: { value: '홍길순' } });
    goToDashboard();

    expect(dialogTitle()).toBeInTheDocument();
    expect(window.location.pathname).toBe('/myinfo');
  });

  it('생년월일만 바꿔도 붙잡는다', () => {
    renderPage();

    fireEvent.change(screen.getByDisplayValue('1995-03-14'), { target: { value: '1995-03-15' } });
    goToDashboard();

    expect(dialogTitle()).toBeInTheDocument();
    expect(window.location.pathname).toBe('/myinfo');
  });

  it('취소하면 화면에 남고 고치던 값도 그대로다', () => {
    renderPage();
    fireEvent.change(nameInput(), { target: { value: '홍길순' } });
    goToDashboard();

    fireEvent.click(screen.getByText('취소'));

    expect(dialogTitle()).toBeNull();
    expect(window.location.pathname).toBe('/myinfo');
    expect(screen.getByDisplayValue('홍길순')).toBeInTheDocument();
  });

  it('그냥 나가기를 누르면 이동한다', () => {
    renderPage();
    fireEvent.change(nameInput(), { target: { value: '홍길순' } });
    goToDashboard();

    fireEvent.click(screen.getByText('그냥 나가기'));

    expect(window.location.pathname).toBe('/dashboard');
  });

  it('저장돼 서버 값이 따라오면 더 이상 붙잡지 않는다', () => {
    const { rerender } = renderPage();
    fireEvent.change(nameInput(), { target: { value: '홍길순' } });

    // 저장 성공 → ['auth'] 무효화 → 재조회가 새 이름을 물고 온다
    setMe({ ...ME, name: '홍길순' });
    rerender(tree());
    goToDashboard();

    expect(dialogTitle()).toBeNull();
    expect(window.location.pathname).toBe('/dashboard');
  });
});
