import { beforeEach, describe, expect, it, vi } from 'vitest';
import { fireEvent, render, screen } from '@testing-library/react';
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

beforeEach(() => {
  vi.clearAllMocks();
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
