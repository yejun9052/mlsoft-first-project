package com.mlsoft.backend.domain.credential;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import javax.crypto.Cipher;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.SecretKeySpec;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.security.SecureRandom;
import java.util.Base64;

/**
 * DB 자격 증명을 암호화·복호화하는 공통 경계.
 *
 * <p>암호화 키는 DB나 소스에 저장하지 않고 환경변수로만 공급한다. 새 이름인
 * {@code APP_CREDENTIAL_ENCRYPTION_KEY}가 비어 있으면 기존 공휴일 시드에서 사용하던
 * {@code HOLIDAY_CREDENTIAL_ENCRYPTION_KEY}를 fallback으로 사용한다.</p>
 *
 * <p>기존 staging 데이터의 복호화를 위해 알고리즘과 payload 형식은 유지한다.
 * AES-256-GCM, 12바이트 IV, 128비트 인증 태그를 사용하며 저장값은
 * {@code Base64(IV || ciphertext || tag)}이다.</p>
 */
@Component
public class SecretCipher {

    private static final String ALGORITHM = "AES/GCM/NoPadding";
    private static final int KEY_BYTES = 32;
    private static final int IV_BYTES = 12;
    private static final int TAG_BITS = 128;
    private static final String CURRENT_KEY_NAME = "APP_CREDENTIAL_ENCRYPTION_KEY";

    private final SecureRandom secureRandom = new SecureRandom();
    private final SecretKeySpec secretKey;

    /** Spring 환경 설정 — 새 키가 우선이고 기존 키는 staging 호환용으로만 사용한다. */
    @Autowired
    public SecretCipher(
            @Value("${app.credential-encryption-key:}") String currentKey,
            @Value("${holiday.credential-encryption-key:}") String legacyKey) {
        this.secretKey = createSecretKey(selectKey(currentKey, legacyKey));
    }

    /** 단위 테스트와 이전 공휴일 테스트가 사용할 수 있는 명시적 키 생성자. */
    public SecretCipher(String encodedKey) {
        this.secretKey = createSecretKey(encodedKey);
    }

    public boolean isConfigured() {
        return secretKey != null;
    }

    public String encrypt(String plainText) {
        requireConfigured();
        if (plainText == null || plainText.isBlank()) {
            throw new IllegalArgumentException("plainText must not be blank");
        }

        byte[] iv = new byte[IV_BYTES];
        secureRandom.nextBytes(iv);
        try {
            Cipher cipher = Cipher.getInstance(ALGORITHM);
            cipher.init(Cipher.ENCRYPT_MODE, secretKey, new GCMParameterSpec(TAG_BITS, iv));
            byte[] encrypted = cipher.doFinal(plainText.getBytes(StandardCharsets.UTF_8));
            return Base64.getEncoder().encodeToString(
                    ByteBuffer.allocate(iv.length + encrypted.length)
                            .put(iv)
                            .put(encrypted)
                            .array());
        } catch (Exception e) {
            throw new IllegalStateException("자격 증명 암호화에 실패했습니다.", e);
        }
    }

    public String decrypt(String encryptedValue) {
        requireConfigured();
        if (encryptedValue == null || encryptedValue.isBlank()) {
            throw new IllegalArgumentException("encryptedValue must not be blank");
        }

        byte[] payload;
        try {
            payload = Base64.getDecoder().decode(encryptedValue);
        } catch (IllegalArgumentException e) {
            throw new IllegalArgumentException("암호화된 자격 증명 형식이 올바르지 않습니다.", e);
        }
        if (payload.length <= IV_BYTES) {
            throw new IllegalArgumentException("암호화된 자격 증명 길이가 올바르지 않습니다.");
        }

        try {
            byte[] iv = new byte[IV_BYTES];
            byte[] encrypted = new byte[payload.length - IV_BYTES];
            System.arraycopy(payload, 0, iv, 0, IV_BYTES);
            System.arraycopy(payload, IV_BYTES, encrypted, 0, encrypted.length);

            Cipher cipher = Cipher.getInstance(ALGORITHM);
            cipher.init(Cipher.DECRYPT_MODE, secretKey, new GCMParameterSpec(TAG_BITS, iv));
            return new String(cipher.doFinal(encrypted), StandardCharsets.UTF_8);
        } catch (Exception e) {
            throw new IllegalArgumentException("자격 증명을 복호화하지 못했습니다.", e);
        }
    }

    private static String selectKey(String currentKey, String legacyKey) {
        if (currentKey != null && !currentKey.isBlank()) {
            return currentKey.trim();
        }
        return legacyKey == null ? "" : legacyKey.trim();
    }

    private static SecretKeySpec createSecretKey(String encodedKey) {
        if (encodedKey == null || encodedKey.isBlank()) {
            return null;
        }

        byte[] decoded;
        try {
            decoded = Base64.getDecoder().decode(encodedKey.trim());
        } catch (IllegalArgumentException e) {
            throw new IllegalStateException(CURRENT_KEY_NAME + "은 Base64여야 합니다.", e);
        }
        if (decoded.length != KEY_BYTES) {
            throw new IllegalStateException(CURRENT_KEY_NAME + "은 디코딩 후 32바이트여야 합니다.");
        }
        return new SecretKeySpec(decoded, "AES");
    }

    private void requireConfigured() {
        if (!isConfigured()) {
            throw new IllegalStateException(
                    CURRENT_KEY_NAME + "이 DB 자격 증명 저장에 필요합니다.");
        }
    }
}
