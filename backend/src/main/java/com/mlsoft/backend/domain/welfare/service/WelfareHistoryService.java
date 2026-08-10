package com.mlsoft.backend.domain.welfare.service;

import com.mlsoft.backend.domain.common.RequestAction;
import com.mlsoft.backend.domain.welfare.dto.WelfareHistoryLogResponse;
import com.mlsoft.backend.domain.welfare.entity.WelfareActionHistory;
import com.mlsoft.backend.domain.welfare.repository.WelfareActionHistoryRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 복리후생 처리 로그 조회 (docs/03 처리 이력 — 관리자·팀장 로그 화면).
 * 스코프 규칙은 {@link com.mlsoft.backend.domain.leave.service.LeaveHistoryService}와 동일하다.
 */
@Service
@RequiredArgsConstructor
public class WelfareHistoryService {

    private final WelfareActionHistoryRepository welfareActionHistoryRepository;

    /** 전체 처리 로그 (GET /api/welfare-histories, SA) — action이 null이면 전체 */
    @Transactional(readOnly = true)
    public Page<WelfareHistoryLogResponse> getHistories(RequestAction action, Pageable pageable) {
        Page<WelfareActionHistory> histories = action == null
                ? welfareActionHistoryRepository.findAll(pageable)
                : welfareActionHistoryRepository.findByAction(action, pageable);
        return histories.map(WelfareHistoryLogResponse::of);
    }

    /**
     * 내가 결재자인 신청의 이력 (GET /api/welfare-histories/my-approvals, TL·SA — 리뷰 S-6).
     * 연차와 같은 기준으로 확정됐다 (2026-08-10) — 신청자 부서가 아니라 승인자 지정.
     */
    @Transactional(readOnly = true)
    public Page<WelfareHistoryLogResponse> getMyApprovalHistories(Long approverId, RequestAction action,
                                                                  Pageable pageable) {
        return welfareActionHistoryRepository.findByApprover(approverId, action, pageable)
                .map(WelfareHistoryLogResponse::of);
    }

    /** 내가 처리한 로그 (GET /api/welfare-histories/my-actions) — 결재 화면 "완료" 탭용. LeaveHistoryService와 동일 규칙 */
    @Transactional(readOnly = true)
    public Page<WelfareHistoryLogResponse> getMyActionHistories(Long actorId, RequestAction action,
                                                                Pageable pageable) {
        Page<WelfareActionHistory> histories = action == null
                ? welfareActionHistoryRepository.findByActorId(actorId, pageable)
                : welfareActionHistoryRepository.findByActorIdAndAction(actorId, action, pageable);
        return histories.map(WelfareHistoryLogResponse::of);
    }
}
