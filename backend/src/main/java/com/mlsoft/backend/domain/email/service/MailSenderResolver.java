package com.mlsoft.backend.domain.email.service;

import lombok.extern.slf4j.Slf4j;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mail.javamail.JavaMailSenderImpl;

import java.util.Properties;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicBoolean;

/** DB SMTP 계정과 기존 환경변수 발송기를 선택하고, 동일 계정의 sender를 재사용한다. */
@Slf4j
final class MailSenderResolver {

    private static final String DEFAULT_HOST = "smtp.gmail.com";
    private static final int DEFAULT_PORT = 587;

    private final JavaMailSender environmentSender;
    private final MailCredentialService credentialService;
    private final String environmentUsername;
    private final AtomicBoolean fallbackWarningLogged = new AtomicBoolean();
    private volatile CachedSender cachedSender;

    MailSenderResolver(
            JavaMailSender environmentSender,
            MailCredentialService credentialService,
            String environmentUsername
    ) {
        this.environmentSender = environmentSender;
        this.credentialService = credentialService;
        this.environmentUsername = environmentUsername == null ? "" : environmentUsername.trim();
    }

    SenderSelection resolve() {
        if (credentialService == null) {
            return new SenderSelection(environmentSender, environmentUsername);
        }

        Optional<MailCredentialService.ResolvedCredential> resolved;
        try {
            resolved = credentialService.resolveActiveCredential();
        } catch (RuntimeException e) {
            return fallback("DB 발신 계정 조회 실패");
        }

        if (resolved.isEmpty()) {
            return fallback("DB 발신 계정 없음 또는 복호화 실패");
        }

        MailCredentialService.ResolvedCredential credential = resolved.get();
        CachedSender cached = cachedSender;
        if (cached != null && cached.cacheKey().equals(credential.cacheKey())) {
            fallbackWarningLogged.set(false);
            return new SenderSelection(cached.sender(), credential.username());
        }

        JavaMailSenderImpl sender = createSender(credential.username(), credential.secret());
        cachedSender = new CachedSender(credential.cacheKey(), sender);
        // 다음 장애가 발생하면 원인 전환 시 WARN을 다시 남긴다.
        fallbackWarningLogged.set(false);
        return new SenderSelection(sender, credential.username());
    }

    private SenderSelection fallback(String reason) {
        if (fallbackWarningLogged.compareAndSet(false, true)) {
            log.warn("[메일] {} — 기존 환경변수 JavaMailSender를 사용합니다.", reason);
        }
        return new SenderSelection(environmentSender, environmentUsername);
    }

    private JavaMailSenderImpl createSender(String username, String secret) {
        JavaMailSenderImpl sender = new JavaMailSenderImpl();
        if (environmentSender instanceof JavaMailSenderImpl source) {
            sender.setHost(source.getHost());
            sender.setPort(source.getPort());
            sender.setProtocol(source.getProtocol());
            sender.setDefaultEncoding(source.getDefaultEncoding());
            Properties properties = new Properties();
            properties.putAll(source.getJavaMailProperties());
            sender.setJavaMailProperties(properties);
        } else {
            // 테스트용 mock 등 JavaMailSenderImpl이 아닌 fallback도 기본 SMTP 속성으로 동작한다.
            sender.setHost(DEFAULT_HOST);
            sender.setPort(DEFAULT_PORT);
            Properties properties = new Properties();
            properties.put("mail.smtp.auth", "true");
            properties.put("mail.smtp.starttls.enable", "true");
            sender.setJavaMailProperties(properties);
        }
        sender.setUsername(username);
        sender.setPassword(secret);
        return sender;
    }

    record SenderSelection(JavaMailSender sender, String username) {
    }

    private record CachedSender(String cacheKey, JavaMailSenderImpl sender) {
    }
}
