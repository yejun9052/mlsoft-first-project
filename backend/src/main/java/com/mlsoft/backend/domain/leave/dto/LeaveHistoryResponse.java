package com.mlsoft.backend.domain.leave.dto;

import com.mlsoft.backend.domain.leave.entity.LeaveActionHistory;

import java.time.LocalDateTime;

/**
 * 연차 처리 이력 응답 (GET /api/leaves/{id}/histories — docs/03).
 */
public record LeaveHistoryResponse(
        Long id,
        String action,
        String actorName,
        String comment,
        LocalDateTime createdAt
) {

    public static LeaveHistoryResponse of(LeaveActionHistory history) {
        return new LeaveHistoryResponse(
                history.getId(),
                history.getAction().name(),
                history.getActor().getName(),
                history.getComment(),
                history.getCreatedAt()
        );
    }
}
