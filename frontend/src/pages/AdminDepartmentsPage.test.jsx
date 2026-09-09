import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';
import { fireEvent, render, screen, within } from '@testing-library/react';
import AdminDepartmentsPage from './AdminDepartmentsPage.jsx';
import {
  useCreateDepartment,
  useDeactivateDepartment,
  useDepartments,
  useUpdateDepartment,
} from '../hooks/useDepartments.js';
import { useLeaderCandidates } from '../hooks/useUsers.js';

vi.mock('react-hot-toast', () => ({
  default: { success: vi.fn() },
}));

vi.mock('../hooks/useDepartments.js', () => ({
  useCreateDepartment: vi.fn(),
  useDeactivateDepartment: vi.fn(),
  useDepartments: vi.fn(),
  useUpdateDepartment: vi.fn(),
}));

vi.mock('../hooks/useUsers.js', () => ({
  useLeaderCandidates: vi.fn(),
}));

const updateMutate = vi.fn();

const 개발본부 = { id: 1, name: '개발본부', description: '개발', leaderId: 10, leaderName: '김도현', parentId: null };
const 개발팀 = { id: 2, name: '개발팀', description: '개발 실무', leaderId: 20, leaderName: '박준호', parentId: 1 };
const 미배정 = { id: 3, name: '미배정', description: '소속 없음', leaderId: null, leaderName: null, parentId: null };

function renderPage(rows) {
  useDepartments.mockReturnValue({
    data: rows,
    isLoading: false,
    isError: false,
    refetch: vi.fn(),
  });
  useLeaderCandidates.mockReturnValue({ data: [], isLoading: false });
  useCreateDepartment.mockReturnValue({ mutate: vi.fn(), isPending: false });
  useUpdateDepartment.mockReturnValue({ mutate: updateMutate, isPending: false });
  useDeactivateDepartment.mockReturnValue({ mutate: vi.fn(), isPending: false });
  return render(<AdminDepartmentsPage />);
}

/** 행 요소 — 부서명 셀에서 <tr>까지 올라간다 */
function row(name) {
  return screen.getByText(name).closest('tr');
}

/** jsdom은 DataTransfer를 구현하지 않아 최소한만 흉내 낸다 */
function dataTransfer() {
  return { effectAllowed: '', dropEffect: '', setData: vi.fn(), getData: vi.fn() };
}

// 최상위 드롭 영역을 문장 전체로 찾으면 안 된다 — getByText는 자식 요소(<span>최상위 부서</span>)의
// 텍스트를 건너뛰고 직속 텍스트 노드만 이어 붙이므로 "여기에 놓으면 가 됩니다"로 읽힌다.
const ROOT_ZONE_TEXT = /여기에 놓으면/;

/** 끌어서 놓기 한 번 — dragStart → dragOver → drop */
function dragOnto(fromName, toName) {
  const dt = dataTransfer();
  fireEvent.dragStart(row(fromName), { dataTransfer: dt });
  const target = toName ? row(toName) : screen.getByText(ROOT_ZONE_TEXT);
  fireEvent.dragOver(target, { dataTransfer: dt });
  fireEvent.drop(target, { dataTransfer: dt });
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
});

afterEach(() => {
  window.matchMedia = REAL_MATCH_MEDIA;
});

describe('AdminDepartmentsPage 드래그로 상위 부서 옮기기', () => {
  /** 사용자가 요청한 동작 그대로 — 개발팀을 미배정 위로 끌어다 놓는다 */
  it('부서를 다른 부서 위에 놓으면 그 부서의 하위로 옮겨진다', () => {
    renderPage([개발본부, 미배정]);

    dragOnto('개발본부', '미배정');

    expect(updateMutate).toHaveBeenCalledTimes(1);
    expect(updateMutate.mock.calls[0][0]).toEqual({
      id: 1,
      name: '개발본부',
      description: '개발',
      leaderId: 10,
      parentId: 3,
    });
  });

  /**
   * PUT이 전체 갱신이라 팀장을 함께 보내지 않으면 공석이 된다.
   * 드래그 한 번에 그 부서의 결재선이 총관리자로 넘어가므로 여기서 고정한다.
   */
  it('옮길 때 팀장이 유지된다 — parentId만 보내면 공석이 된다', () => {
    renderPage([개발본부, 미배정]);

    dragOnto('개발본부', '미배정');

    expect(updateMutate.mock.calls[0][0].leaderId).toBe(10);
  });

  it('하위 부서를 가진 부서는 다른 부서 아래로 못 간다 — 3단계가 되기 때문', () => {
    renderPage([개발본부, 개발팀, 미배정]);

    dragOnto('개발본부', '미배정'); // 개발본부에는 개발팀이 달려 있다

    expect(updateMutate).not.toHaveBeenCalled();
  });

  it('이미 그 부서의 하위면 아무 일도 하지 않는다', () => {
    renderPage([개발본부, 개발팀]);

    dragOnto('개발팀', '개발본부');

    expect(updateMutate).not.toHaveBeenCalled();
  });

  it('드래그 중에만 나타나는 영역에 놓으면 최상위로 빠진다', () => {
    renderPage([개발본부, 개발팀]);

    // 드래그 전에는 그 영역이 없다
    expect(screen.queryByText(ROOT_ZONE_TEXT)).toBeNull();

    dragOnto('개발팀', null);

    expect(updateMutate).toHaveBeenCalledTimes(1);
    expect(updateMutate.mock.calls[0][0]).toMatchObject({ id: 2, parentId: null });
  });

  it('드롭다운으로 옮기는 기존 경로가 남아 있다 — 드래그는 마우스 전용이라 대체재가 아니다', () => {
    renderPage([개발본부, 미배정]);

    fireEvent.click(screen.getAllByLabelText('부서 수정')[0]);

    expect(screen.getByText('상위 부서')).toBeInTheDocument();
  });
});

// 좁은 화면 터치 대안 (C-3) — 드래그가 안 먹는 화면에서 카드 + "상위 부서 변경" 버튼으로 대신한다
describe('AdminDepartmentsPage 좁은 화면 상위 부서 변경', () => {
  it('좁은 화면에서는 표 대신 카드로 부서를 보여준다', () => {
    setViewport(true);
    renderPage([개발본부, 개발팀, 미배정]);

    expect(screen.getByTestId('responsive-table-cards')).toBeInTheDocument();
    expect(screen.queryByTestId('responsive-table-table')).not.toBeInTheDocument();
  });

  it('넓은 화면에서는 카드 없이 표를 그대로 보여준다', () => {
    setViewport(false);
    renderPage([개발본부, 미배정]);

    expect(screen.getByTestId('responsive-table-table')).toBeInTheDocument();
    expect(screen.queryByTestId('responsive-table-cards')).not.toBeInTheDocument();
  });

  it('하위 부서 카드는 상위 부서 이름을 글자로 보여준다 — 들여쓰기만으로는 좁은 폭에서 잘린다', () => {
    setViewport(true);
    renderPage([개발본부, 개발팀, 미배정]);

    const card = cardFor(개발팀.name);
    expect(within(card).getByText(개발본부.name)).toBeInTheDocument();
  });

  it('최상위 부서 카드는 "최상위"로 표시한다', () => {
    setViewport(true);
    renderPage([개발본부, 미배정]);

    const card = cardFor(개발본부.name);
    expect(within(card).getByText('최상위')).toBeInTheDocument();
  });

  it('"상위 부서 변경" 버튼이 새 흐름이 아니라 기존 부서 수정 폼을 그대로 연다', () => {
    setViewport(true);
    renderPage([개발본부, 개발팀, 미배정]);

    const card = cardFor(개발팀.name);
    fireEvent.click(within(card).getByRole('button', { name: '상위 부서 변경' }));

    // 드롭다운 경로 테스트와 같은 모달 — 상위 부서 select에 현재 값이 채워져 있다
    expect(screen.getByDisplayValue(개발팀.name)).toBeInTheDocument();
    const [, parentSelect] = screen.getAllByRole('combobox');
    expect(parentSelect).toHaveValue(String(개발본부.id));
  });

  it('그 폼에서 저장하면 드래그와 같은 PUT 본문이 나간다 — 팀장이 공석이 되지 않는다', () => {
    setViewport(true);
    renderPage([개발본부, 개발팀, 미배정]);

    const card = cardFor(개발팀.name);
    fireEvent.click(within(card).getByRole('button', { name: '상위 부서 변경' }));

    const [, parentSelect] = screen.getAllByRole('combobox');
    fireEvent.change(parentSelect, { target: { value: String(미배정.id) } });
    fireEvent.click(screen.getByRole('button', { name: '저장' }));

    expect(updateMutate).toHaveBeenCalledTimes(1);
    expect(updateMutate.mock.calls[0][0]).toEqual({
      id: 개발팀.id,
      name: 개발팀.name,
      description: 개발팀.description,
      leaderId: 개발팀.leaderId,
      parentId: 미배정.id,
    });
  });
});

/** 모바일 카드 루트 요소 — 부서명 텍스트에서 <article>까지 올라간다 */
function cardFor(name) {
  return screen.getByText(name).closest('article');
}
