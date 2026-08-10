package com.mlsoft.backend.domain.leave.service;

import com.mlsoft.backend.domain.common.RequestAction;
import com.mlsoft.backend.domain.leave.dto.LeaveHistoryLogResponse;
import com.mlsoft.backend.domain.leave.entity.LeaveActionHistory;
import com.mlsoft.backend.domain.leave.repository.LeaveActionHistoryRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 연차 처리 로그 조회 (docs/03 처리 이력 — 관리자·팀장 로그 화면).
 *
 * <p>이력 적재는 {@link LeaveService}가 담당하고 여기서는 조회만 한다.
 * 스코프는 요청 파라미터가 아니라 <b>토큰의 요청자 id</b>로 서버가 결정한다 — 팀장이 남의 로그를
 * 조회하도록 파라미터를 바꿔치기하는 것을 막기 위함 (docs/04 본인 식별 규칙).
 */
@Service
@RequiredArgsConstructor
public class LeaveHistoryService {

    private final LeaveActionHistoryRepository leaveActionHistoryRepository;

    /** 전체 처리 로그 (GET /api/leave-histories, SA) — action이 null이면 전체 */
    @Transactional(readOnly = true)
    public Page<LeaveHistoryLogResponse> getHistories(RequestAction action, Pageable pageable) {
        Page<LeaveActionHistory> histories = action == null
                ? leaveActionHistoryRepository.findAll(pageable)
                : leaveActionHistoryRepository.findByAction(action, pageable);
        return histories.map(LeaveHistoryLogResponse::of);
    }

    /**
     * 내가 결재자인 신청의 이력 (GET /api/leave-histories/my-approvals, TL·SA — 리뷰 S-6).
     *
     * <p><b>2026-08-10 스코프 확정</b>: 요청자의 소속 부서가 아니라 <b>내가 primary·sub 승인자로
     * 지정된 신청</b>을 기준으로 한다. 부서 기준은 실제 결재 권한과 어긋났다 —
     * 부서를 옮긴 사원의 과거 이력이 새 팀장에게 보이고, 반대로 퇴직 이관으로 결재를 넘겨받은
     * 건은 내 부서가 아니라서 안 보였다.
     *
     * <p>부서를 보지 않으므로 <b>부서 미배정 분기가 필요 없다</b> — 예전에는 부서가 없으면
     * 스코프가 성립하지 않아 빈 페이지로 빠져나갔다.
     */
    @Transactional(readOnly = true)
    public Page<LeaveHistoryLogResponse> getMyApprovalHistories(Long approverId, RequestAction action,
                                                                Pageable pageable) {
        return leaveActionHistoryRepository.findByApprover(approverId, action, pageable)
                .map(LeaveHistoryLogResponse::of);
    }

    /**
     * 내가 처리한 로그 (GET /api/leave-histories/my-actions) — actor가 요청자인 이력만.
     *
     * <p>{@code my-approvals}와 다르다 — 그쪽은 <b>내가 승인자로 지정된</b> 신청의 모든 이력이라
     * 같은 건을 서브 승인자가 처리한 기록도 포함된다. 이쪽은 <b>내가 직접 누른 것</b>만이다.
     * 결재 화면의 "승인·반려 완료" 탭이 필요한 것은 후자다.
     */
    @Transactional(readOnly = true)
    public Page<LeaveHistoryLogResponse> getMyActionHistories(Long actorId, RequestAction action,
                                                              Pageable pageable) {
        Page<LeaveActionHistory> histories = action == null
                ? leaveActionHistoryRepository.findByActorId(actorId, pageable)
                : leaveActionHistoryRepository.findByActorIdAndAction(actorId, action, pageable);
        return histories.map(LeaveHistoryLogResponse::of);
    }
}
