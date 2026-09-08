package com.mlsoft.backend.domain.audit.dto;

import com.mlsoft.backend.domain.audit.entity.AdminAuditLog;
import com.mlsoft.backend.domain.user.entity.User;

import java.time.LocalDateTime;

/**
 * 감사 로그 응답 (GET /api/admin/audit-logs).
 * 라벨을 서버가 내려주므로 프론트가 액션 이름을 하드코딩하지 않는다.
 */
public record AdminAuditLogResponse(
        Long id,
        String action,
        String actionLabel,
        Long actorId,
        String actorName,
        Long targetUserId,
        String targetLabel,
        String beforeValue,
        String afterValue,
        LocalDateTime createdAt
) {

    private static final String SYSTEM_ACTOR_LABEL = "시스템";

    public static AdminAuditLogResponse of(AdminAuditLog log) {
        User actor = log.getActor();
        User targetUser = log.getTargetUser();
        return new AdminAuditLogResponse(
                log.getId(),
                log.getAction().name(),
                log.getAction().getLabel(),
                actor == null ? null : actor.getId(),
                actor == null ? SYSTEM_ACTOR_LABEL : actor.getName(),
                targetUser == null ? null : targetUser.getId(),
                log.getTargetLabel(),
                log.getBeforeValue(),
                log.getAfterValue(),
                log.getCreatedAt()
        );
    }
}
