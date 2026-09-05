package com.mlsoft.backend.domain.email.service;

import com.mlsoft.backend.domain.credential.SecretCipher;
import com.mlsoft.backend.domain.email.dto.MailCredentialMaskDto;
import com.mlsoft.backend.domain.email.entity.EmailHistory;
import com.mlsoft.backend.domain.email.entity.EmailType;
import com.mlsoft.backend.domain.email.entity.MailCredential;
import com.mlsoft.backend.domain.email.event.EmailDispatchEvent;
import com.mlsoft.backend.domain.email.repository.EmailHistoryRepository;
import com.mlsoft.backend.domain.email.repository.MailCredentialRepository;
import com.mlsoft.backend.domain.user.entity.User;
import com.mlsoft.backend.domain.user.repository.UserRepository;
import com.mlsoft.backend.global.exception.BusinessException;
import com.mlsoft.backend.global.exception.ErrorCode;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Locale;
import java.util.Optional;

/**
 * 메일 발신 계정의 암호화 저장·회전·조회 경계.
 *
 * <p>DB에는 암호문만 저장하고, 발송기는 {@link #resolveActiveCredential()}로 복호화된
 * 일회성 계정 정보를 받는다. DB 계정이 없거나 복호화에 실패한 경우에는 빈 Optional을
 * 반환해 발송기가 기존 환경변수 계정으로 안전하게 fallback할 수 있게 한다.</p>
 */
@Service
public class MailCredentialService {

    public static final String SMTP_PROVIDER = "SMTP";
    private static final String SECRET_MASK = "••••";

    private final MailCredentialRepository repository;
    private final SecretCipher cipher;
    private final UserRepository userRepository;
    private final EmailHistoryRepository emailHistoryRepository;
    private final ApplicationEventPublisher eventPublisher;

    /** 애플리케이션에서 사용하는 전체 의존성 생성자. */
    @Autowired
    public MailCredentialService(
            MailCredentialRepository repository,
            SecretCipher cipher,
            UserRepository userRepository,
            EmailHistoryRepository emailHistoryRepository,
            ApplicationEventPublisher eventPublisher
    ) {
        this.repository = repository;
        this.cipher = cipher;
        this.userRepository = userRepository;
        this.emailHistoryRepository = emailHistoryRepository;
        this.eventPublisher = eventPublisher;
    }

    /** 자격 증명 단위 테스트에서 사용하는 최소 생성자. */
    public MailCredentialService(MailCredentialRepository repository, SecretCipher cipher) {
        this(repository, cipher, null, null, null);
    }

    /** 활성 SMTP 계정을 복호화한다. 실패 시 원문을 남기지 않고 빈 값으로 fallback을 유도한다. */
    @Transactional(readOnly = true)
    public Optional<ResolvedCredential> resolveActiveCredential() {
        Optional<MailCredential> stored = repository.findByProviderAndActiveTrue(SMTP_PROVIDER);
        if (stored.isEmpty()) {
            return Optional.empty();
        }

        MailCredential credential = stored.get();
        try {
            String secret = cipher.decrypt(credential.getEncryptedSecret());
            if (credential.getUsername() == null || credential.getUsername().isBlank()
                    || secret == null || secret.isBlank()) {
                return Optional.empty();
            }
            return Optional.of(new ResolvedCredential(
                    credential.getUsername().trim(),
                    secret,
                    cacheKey(credential)));
        } catch (RuntimeException e) {
            return Optional.empty();
        }
    }

    /** 현재 SMTP 계정을 원문 없이 마스킹해 조회한다. */
    @Transactional(readOnly = true)
    public Optional<MailCredentialMaskDto> findMaskedCredential() {
        return repository.findByProvider(SMTP_PROVIDER).map(this::toMaskedDto);
    }

    /** W4 관리자 화면이 사용할 provider 지정 마스킹 조회. */
    @Transactional(readOnly = true)
    public Optional<MailCredentialMaskDto> findMaskedCredential(String provider) {
        String normalizedProvider = normalizeProvider(provider);
        return repository.findByProvider(normalizedProvider).map(this::toMaskedDto);
    }

    /** SMTP 계정을 새로 저장하거나 사용자명·비밀값을 함께 회전한다. */
    @Transactional
    public MailCredentialMaskDto saveOrRotate(String provider, String username, String secret) {
        String normalizedProvider = normalizeProvider(provider);
        String normalizedUsername = requireText(username, "username");
        String normalizedSecret = requireText(secret, "secret");
        String encryptedSecret;
        try {
            encryptedSecret = cipher.encrypt(normalizedSecret);
        } catch (IllegalStateException e) {
            throw new BusinessException(ErrorCode.CREDENTIAL_ENCRYPTION_NOT_CONFIGURED);
        }

        MailCredential credential = repository.findByProvider(normalizedProvider)
                .map(existing -> {
                    existing.rotate(normalizedUsername, encryptedSecret);
                    return existing;
                })
                .orElseGet(() -> MailCredential.create(
                        normalizedProvider, normalizedUsername, encryptedSecret));
        MailCredential saved = repository.save(credential);
        return toMaskedDto(saved, normalizedSecret);
    }

    /** SMTP 계정 저장의 명시적 별칭 — 관리자 회전 API에서 읽기 쉽게 사용한다. */
    @Transactional
    public MailCredentialMaskDto rotate(String username, String secret) {
        return saveOrRotate(SMTP_PROVIDER, username, secret);
    }

    /**
     * 관리자 본인에게 테스트 메일을 큐에 넣는다.
     *
     * <p>실제 SMTP 호출은 기존 AFTER_COMMIT 아웃박스 경계를 그대로 타므로 관리자 업무
     * 트랜잭션과 발송을 분리하고 email_history에도 이력이 남는다. 컨트롤러는 인증 주체의
     * 이메일만 전달해야 한다.</p>
     */
    @Transactional
    public void sendTestMail(String toEmail) {
        if (userRepository == null || emailHistoryRepository == null || eventPublisher == null) {
            throw new IllegalStateException("테스트 메일 발송 의존성이 설정되지 않았습니다.");
        }
        String normalizedEmail = requireText(toEmail, "toEmail");
        User recipient = userRepository.findByEmail(normalizedEmail)
                .orElseThrow(() -> new BusinessException(ErrorCode.USER_NOT_FOUND));
        EmailHistory history = emailHistoryRepository.save(EmailHistory.create(
                recipient,
                null,
                EmailType.NOTICE,
                "[메일 테스트] MLsoft 연차관리",
                "메일 발송 설정이 정상적으로 동작하는지 확인하는 테스트 메일입니다."));
        eventPublisher.publishEvent(new EmailDispatchEvent(List.of(history.getId())));
    }

    private MailCredentialMaskDto toMaskedDto(MailCredential credential) {
        String secret = "";
        try {
            secret = cipher.decrypt(credential.getEncryptedSecret());
        } catch (RuntimeException ignored) {
            // 복호화할 수 없는 기존 행도 원문 없이 안전하게 표시한다.
        }
        return toMaskedDto(credential, secret);
    }

    private MailCredentialMaskDto toMaskedDto(MailCredential credential, String plainSecret) {
        return new MailCredentialMaskDto(
                credential.getProvider(),
                credential.getUsername(),
                maskSecret(plainSecret),
                credential.isActive());
    }

    private String maskSecret(String secret) {
        if (secret == null || secret.isBlank()) {
            return SECRET_MASK;
        }
        String normalized = secret.trim();
        String suffix = normalized.length() <= 4
                ? normalized
                : normalized.substring(normalized.length() - 4);
        return SECRET_MASK + suffix;
    }

    private String cacheKey(MailCredential credential) {
        // 암호문은 평문이 아니므로 sender 캐시의 회전 감지 키로만 사용한다.
        return String.valueOf(credential.getId()) + ":"
                + credential.getUsername() + ":" + credential.getEncryptedSecret();
    }

    private String normalizeProvider(String provider) {
        return requireText(provider, "provider").toUpperCase(Locale.ROOT);
    }

    private String requireText(String value, String field) {
        if (value == null || value.isBlank()) {
            throw new BusinessException(ErrorCode.INVALID_INPUT_VALUE);
        }
        return value.trim();
    }

    /** 발송기 내부에서만 사용하는 복호화 결과. 평문은 로그·응답에 사용하지 않는다. */
    public record ResolvedCredential(String username, String secret, String cacheKey) {
    }
}
