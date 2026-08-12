package com.mlsoft.backend.domain.email.event;

import com.mlsoft.backend.domain.email.entity.EmailType;

import java.util.List;

/**
 * 업무 트랜잭션 커밋 후 처리할 이메일 이벤트.
 *
 * <p>준영속 엔티티의 지연 로딩을 피하기 위해 id와 문자열 등 원시값만 담는다.
 */
public record EmailNotificationEvent(
        EmailType emailType,
        List<EmailRecipient> recipients,
        EmailTemplateData templateData
) {
    public EmailNotificationEvent {
        recipients = List.copyOf(recipients);
    }
}
