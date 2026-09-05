package com.mlsoft.backend.domain.email.dto;

import com.mlsoft.backend.domain.email.entity.EmailHistory;
import com.mlsoft.backend.domain.email.entity.EmailStatus;
import com.mlsoft.backend.domain.email.entity.EmailType;

import java.time.LocalDateTime;

/** 관리자 이메일 발송 이력 응답 — 수신자 주소는 항상 마스킹한다. */
public record EmailHistoryResponse(
        Long id,
        String recipientName,
        String recipientEmailMasked,
        EmailType type,
        EmailStatus status,
        String title,
        int retryCount,
        String errorMessage,
        LocalDateTime sentAt,
        LocalDateTime createdAt
) {

    public static EmailHistoryResponse of(EmailHistory history) {
        String email = history.getUser() == null ? "" : history.getUser().getEmail();
        return new EmailHistoryResponse(
                history.getId(),
                history.getUser() == null ? "" : history.getUser().getName(),
                maskEmail(email),
                history.getEmailType(),
                history.getStatus(),
                history.getTitle(),
                history.getRetryCount(),
                history.getErrorMessage(),
                history.getSentAt(),
                history.getCreatedAt());
    }

    /** 앞 2자와 @ 뒤 도메인만 남긴다. 주소가 짧아도 원문은 반환하지 않는다. */
    public static String maskEmail(String email) {
        if (email == null || email.isBlank()) {
            return "";
        }
        int at = email.indexOf('@');
        if (at <= 0) {
            return email.substring(0, Math.min(2, email.length())) + "***";
        }
        return email.substring(0, Math.min(2, at)) + "***" + email.substring(at);
    }
}
