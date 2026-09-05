package com.mlsoft.backend.domain.holiday.credential;

import com.mlsoft.backend.domain.credential.SecretCipher;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Optional;

/**
 * 공휴일 API 키의 조회·초기 저장 경계.
 *
 * <p>기존 환경변수는 DB 시딩과 암호화 키가 준비되지 않은 개발 환경의 fallback으로만
 * 사용한다. 운영에서는 암호화된 DB 값을 우선한다.</p>
 */
@Slf4j
@Service
public class HolidayApiCredentialService {

    public static final String DATA_GO_KR_PROVIDER = "DATA_GO_KR";

    private final HolidayApiCredentialRepository repository;
    private final SecretCipher cipher;
    private final String environmentApiKey;
    private final boolean environmentSeedEnabled;

    public HolidayApiCredentialService(
            HolidayApiCredentialRepository repository,
            SecretCipher cipher,
            @Value("${holiday.api-key:}") String environmentApiKey,
            @Value("${holiday.credential-seed-enabled:false}") boolean environmentSeedEnabled) {
        this.repository = repository;
        this.cipher = cipher;
        this.environmentApiKey = environmentApiKey == null ? "" : environmentApiKey.trim();
        this.environmentSeedEnabled = environmentSeedEnabled;
    }

    @Transactional(readOnly = true)
    public Optional<String> resolveApiKey() {
        Optional<HolidayApiCredential> stored =
                repository.findByProviderAndActiveTrue(DATA_GO_KR_PROVIDER);
        if (stored.isPresent() && cipher.isConfigured()) {
            try {
                return Optional.of(cipher.decrypt(stored.get().getEncryptedApiKey()));
            } catch (IllegalArgumentException e) {
                log.warn("[공휴일] 저장된 API 자격 증명을 복호화하지 못했습니다. 환경변수 fallback을 사용합니다.");
            }
        }
        return environmentApiKey.isBlank() ? Optional.empty() : Optional.of(environmentApiKey);
    }

    /**
     * 환경변수 키를 암호화해 DB에 한 번 저장한다. 원문 키는 감사 로그나 holiday 행에 기록하지 않는다.
     * @return 새로 저장 또는 회전했으면 true
     */
    @Transactional
    public boolean seedFromEnvironment() {
        if (!environmentSeedEnabled || environmentApiKey.isBlank()) {
            return false;
        }
        if (!cipher.isConfigured()) {
            log.warn("[공휴일] APP_CREDENTIAL_ENCRYPTION_KEY(또는 이전 HOLIDAY_CREDENTIAL_ENCRYPTION_KEY)가 없어 API 키를 DB에 저장하지 않습니다.");
            return false;
        }

        Optional<HolidayApiCredential> existing =
                repository.findByProvider(DATA_GO_KR_PROVIDER);
        if (existing.isPresent()) {
            try {
                if (environmentApiKey.equals(cipher.decrypt(existing.get().getEncryptedApiKey()))
                        && existing.get().isActive()) {
                    return false;
                }
            } catch (IllegalArgumentException ignored) {
                // 암호화 키 회전 또는 이전 형식이면 아래에서 새 값으로 회전한다.
            }
            existing.get().rotate(cipher.encrypt(environmentApiKey));
            repository.save(existing.get());
            log.info("[공휴일] API 자격 증명을 DB에서 회전했습니다.");
            return true;
        }

        repository.save(HolidayApiCredential.create(
                DATA_GO_KR_PROVIDER,
                cipher.encrypt(environmentApiKey)));
        log.info("[공휴일] API 자격 증명을 암호화해 DB에 저장했습니다.");
        return true;
    }
}
