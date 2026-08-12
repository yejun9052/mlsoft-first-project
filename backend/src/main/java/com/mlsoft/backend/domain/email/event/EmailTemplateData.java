package com.mlsoft.backend.domain.email.event;

/**
 * 제목·본문 생성에 필요한 원시값 스냅샷.
 *
 * <p>사용하지 않는 값은 빈 문자열로 전달한다. JPA 엔티티나 컬렉션은 담지 않는다.
 */
public record EmailTemplateData(
        EmailTemplateKind kind,
        Long requestId,
        String applicantName,
        String itemName,
        String dates,
        String days,
        String reason,
        String actorName
) {
    public EmailTemplateData {
        applicantName = safe(applicantName);
        itemName = safe(itemName);
        dates = safe(dates);
        days = safe(days);
        reason = safe(reason);
        actorName = safe(actorName);
    }

    private static String safe(String value) {
        return value == null ? "" : value;
    }
}
