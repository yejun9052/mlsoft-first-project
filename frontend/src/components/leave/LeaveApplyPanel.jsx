import { useEffect, useRef, useState } from 'react';
import dayjs from 'dayjs';
import toast from 'react-hot-toast';
import { GripVertical, X } from 'lucide-react';
import { LEAVE_TYPE_LABEL } from '../../constants/status.js';

// 신청 가능한 연차 종류 (WELFARE는 복리후생 페이지에서 별도 신청)
const APPLY_TYPES = ['ANNUAL', 'HALF_AM', 'HALF_PM'];
const WEEKDAY_KO = ['일', '월', '화', '수', '목', '금', '토'];

/**
 * 연차 신청 플로팅 패널 — 캘린더 위에 떠서 헤더를 잡고 드래그로 옮길 수 있다.
 * 배경을 가리지 않는 논모달이라 패널을 열어둔 채 캘린더 날짜를 계속 클릭해 담는 흐름.
 * 날짜 선택 상태는 부모(캘린더)가 소유하고, 이 패널은 종류·사유·승인자만 관리한다.
 *
 * ⚠️ 제출은 mock — 실제 서버 연동 시 handleSubmit을 POST /api/leaves 호출로 교체.
 */
export default function LeaveApplyPanel({ dates, remainingDays, approvers, onRemoveDate, onClose }) {
  const [type, setType] = useState('ANNUAL');
  const [reason, setReason] = useState('');
  const [approverId, setApproverId] = useState('');
  const [subApproverId, setSubApproverId] = useState('');

  // 패널 위치 — 처음엔 우측 상단, 이후 드래그 값 유지
  const [pos, setPos] = useState(() => ({
    x: Math.max(16, window.innerWidth - 452),
    y: 108,
  }));
  const dragOffset = useRef(null); // 드래그 중일 때만 {dx, dy}

  // 창이 좁아져도(윈도우 스냅·모니터 변경) 패널이 화면 밖에 고립되지 않게 재클램프
  useEffect(() => {
    function clampToViewport() {
      setPos((p) => ({
        x: Math.min(Math.max(8, p.x), window.innerWidth - 120),
        y: Math.min(Math.max(8, p.y), window.innerHeight - 60),
      }));
    }
    window.addEventListener('resize', clampToViewport);
    return () => window.removeEventListener('resize', clampToViewport);
  }, []);

  // Esc로 닫기 — 논모달이라 포커스 트랩이 없어 키보드 닫기 수단을 따로 제공
  useEffect(() => {
    function onKeyDown(e) {
      if (e.key === 'Escape') onClose();
    }
    window.addEventListener('keydown', onKeyDown);
    return () => window.removeEventListener('keydown', onKeyDown);
  }, [onClose]);

  function onDragStart(e) {
    dragOffset.current = { dx: e.clientX - pos.x, dy: e.clientY - pos.y };
    e.currentTarget.setPointerCapture(e.pointerId);
  }

  function onDragMove(e) {
    if (!dragOffset.current) return;
    setPos({
      // 화면 밖으로 완전히 나가지 않게만 제한
      x: Math.min(Math.max(8, e.clientX - dragOffset.current.dx), window.innerWidth - 120),
      y: Math.min(Math.max(8, e.clientY - dragOffset.current.dy), window.innerHeight - 60),
    });
  }

  function onDragEnd() {
    dragOffset.current = null;
  }

  // 차감 일수 — 반차는 날짜당 0.5일
  const days = dates.length * (type === 'ANNUAL' ? 1 : 0.5);
  const afterRemaining = Math.round((remainingDays - days) * 10) / 10;

  function handleSubmit() {
    if (dates.length === 0) {
      toast.error('캘린더에서 날짜를 먼저 선택해 주세요.');
      return;
    }
    if (!reason.trim()) {
      toast.error('신청 사유를 입력해 주세요.');
      return;
    }
    if (!approverId) {
      toast.error('승인자를 선택해 주세요.');
      return;
    }
    // mock 제출 — 실제 연동 시 여기서 POST /api/leaves 호출
    // (잔여 초과분은 당겨쓰기 설계(advance_max_days, 기본 5일)에 따라 백엔드가 검증 — ADVANCE_LIMIT_EXCEEDED)
    toast.success(`(목업) ${LEAVE_TYPE_LABEL[type]} ${days}일 신청이 접수되었습니다.`, { icon: '🗓️' });
    onClose();
  }

  return (
    <div
      role="dialog"
      aria-label="연차 신청 패널"
      className="fixed z-50 w-[424px] rounded-card bg-navy-card shadow-card ring-1 ring-white/12"
      style={{ left: pos.x, top: pos.y }}
    >
      {/* 드래그 핸들 헤더 */}
      <div
        onPointerDown={onDragStart}
        onPointerMove={onDragMove}
        onPointerUp={onDragEnd}
        onPointerCancel={onDragEnd}
        className="flex cursor-move touch-none select-none items-center justify-between gap-2 rounded-t-card border-b border-white/8 bg-navy-app/50 px-4 py-3"
      >
        <div className="flex items-center gap-2">
          <GripVertical size={15} className="text-ink-dim" />
          <span className="text-[15px] font-semibold text-ink-hi">연차 신청</span>
          <span className="text-[12px] text-ink-dim">끌어서 이동</span>
        </div>
        <button
          type="button"
          onClick={onClose}
          onPointerDown={(e) => e.stopPropagation()}
          className="rounded-btn p-1 text-ink-mute transition-colors hover:bg-white/6 hover:text-ink-body"
          aria-label="닫기"
        >
          <X size={16} />
        </button>
      </div>

      <div className="flex flex-col gap-4 px-5 py-4">
        {/* 종류 — 세그먼트 버튼 */}
        <div className="grid grid-cols-3 gap-1 rounded-btn bg-navy-app/50 p-1">
          {APPLY_TYPES.map((t) => (
            <button
              key={t}
              type="button"
              onClick={() => setType(t)}
              className={`rounded-btn px-2 py-2 text-[13px] font-semibold transition-colors ${
                type === t ? 'bg-accent text-white' : 'text-ink-mute hover:text-ink-body'
              }`}
            >
              {LEAVE_TYPE_LABEL[t]}
            </button>
          ))}
        </div>

        {/* 선택한 날짜 칩 — 캘린더 클릭으로 추가, ×로 제거 */}
        <div>
          <FieldLabel>선택한 날짜 ({dates.length}일)</FieldLabel>
          {dates.length === 0 ? (
            <p className="rounded-btn border border-dashed border-white/12 px-3 py-3 text-[13px] text-ink-dim">
              캘린더에서 날짜를 클릭해 담으세요. 다시 클릭하면 빠집니다.
            </p>
          ) : (
            <div className="flex flex-wrap gap-1.5">
              {dates.map((d) => (
                <span
                  key={d}
                  className="flex items-center gap-1 rounded-badge bg-accent/16 py-1.5 pl-3 pr-2 text-[13px] font-medium text-accent-light"
                >
                  {dayjs(d).format('M/D')} ({WEEKDAY_KO[dayjs(d).day()]})
                  <button
                    type="button"
                    onClick={() => onRemoveDate(d)}
                    className="rounded p-0.5 hover:bg-white/10"
                    aria-label={`${d} 제거`}
                  >
                    <X size={12} />
                  </button>
                </span>
              ))}
            </div>
          )}
        </div>

        {/* 사유 (필수) */}
        <div>
          <FieldLabel>신청 사유</FieldLabel>
          <textarea
            value={reason}
            onChange={(e) => setReason(e.target.value)}
            rows={2}
            placeholder="사유를 입력하세요 (승인자에게만 표시)"
            className="w-full resize-none rounded-btn border border-white/8 bg-navy-btn2 px-3 py-2.5 text-[14px] text-ink-hi placeholder:text-ink-dim focus:border-accent/50 focus:outline-none"
          />
        </div>

        {/* 승인자 — 기본(필수) / 서브(선택) */}
        <div className="grid grid-cols-2 gap-2">
          <div>
            <FieldLabel>승인자</FieldLabel>
            <ApproverSelect
              value={approverId}
              onChange={(v) => {
                setApproverId(v);
                // 새 승인자가 서브 승인자와 같으면 서브를 비움 (동일인 이중 지정 방지)
                if (v && v === subApproverId) setSubApproverId('');
              }}
              approvers={approvers}
              placeholder="선택 (필수)"
            />
          </div>
          <div>
            <FieldLabel>서브 승인자</FieldLabel>
            <ApproverSelect
              value={subApproverId}
              onChange={setSubApproverId}
              approvers={approvers.filter((a) => String(a.id) !== approverId)}
              placeholder="선택 안 함"
            />
          </div>
        </div>

        {/* 차감 요약 + 제출 */}
        <div className="flex items-center justify-between rounded-btn bg-navy-app/50 px-3.5 py-3 text-[13px]">
          <span className="text-ink-mute">차감 예정</span>
          <span className="font-semibold text-ink-hi tabular-nums">
            <span className="text-[16px]">{days}</span>일
            <span className="ml-2 font-normal text-ink-mute">
              잔여 {remainingDays} → <span className={afterRemaining < 0 ? 'text-warn' : ''}>{afterRemaining}일</span>
            </span>
          </span>
        </div>
        <button
          type="button"
          onClick={handleSubmit}
          className="rounded-btn bg-accent px-4 py-3 text-[14px] font-semibold text-white shadow-btn transition-colors hover:bg-accent-dark"
        >
          신청하기
        </button>
      </div>
    </div>
  );
}

function FieldLabel({ children }) {
  return <p className="mb-1.5 text-[13px] font-medium text-ink-mute">{children}</p>;
}

function ApproverSelect({ value, onChange, approvers, placeholder }) {
  return (
    <select
      value={value}
      onChange={(e) => onChange(e.target.value)}
      className="w-full rounded-btn border border-white/8 bg-navy-btn2 px-2.5 py-2.5 text-[14px] text-ink-hi focus:border-accent/50 focus:outline-none"
    >
      <option value="">{placeholder}</option>
      {approvers.map((a) => (
        <option key={a.id} value={a.id}>
          {a.name} · {a.departmentName}
        </option>
      ))}
    </select>
  );
}
