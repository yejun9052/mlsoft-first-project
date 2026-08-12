package com.mlsoft.backend.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * 애플리케이션 이메일 설정.
 *
 * @param replyTo 회신 주소. 비어 있으면 Reply-To 헤더를 만들지 않는다
 */
@ConfigurationProperties(prefix = "app.mail")
public record MailAppProperties(String replyTo) {

    public MailAppProperties {
        replyTo = replyTo == null ? "" : replyTo.trim();
    }

    public boolean hasReplyTo() {
        return !replyTo.isBlank();
    }
}
