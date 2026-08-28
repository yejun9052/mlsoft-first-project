package com.mlsoft.backend.domain.holiday.credential;

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
 * DB에 저장할 공휴일 API 키 암호화기.
 *
 * <p>암호화 키는 DB가 아니라 {@code HOLIDAY_CREDENTIAL_ENCRYPTION_KEY} 환경변수로만
 * 공급한다. 값은 Base64로 인코딩한 32바이트 AES 키다.</p>
 */
@Component
public class HolidaySecretCipher {

    private static final String ALGORITHM = "AES/GCM/NoPadding";
    private static final int KEY_BYTES = 32;
    private static final int IV_BYTES = 12;
    private static final int TAG_BITS = 128;

    private final SecureRandom secureRandom = new SecureRandom();
    private final SecretKeySpec secretKey;

    public HolidaySecretCipher(
            @Value("${holiday.credential-encryption-key:}") String encodedKey) {
        if (encodedKey == null || encodedKey.isBlank()) {
            this.secretKey = null;
            return;
        }

        byte[] decoded;
        try {
            decoded = Base64.getDecoder().decode(encodedKey.trim());
        } catch (IllegalArgumentException e) {
            throw new IllegalStateException(
                    "HOLIDAY_CREDENTIAL_ENCRYPTION_KEY must be Base64", e);
        }
        if (decoded.length != KEY_BYTES) {
            throw new IllegalStateException(
                    "HOLIDAY_CREDENTIAL_ENCRYPTION_KEY must decode to 32 bytes");
        }
        this.secretKey = new SecretKeySpec(decoded, "AES");
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
            throw new IllegalStateException("Holiday API key encryption failed", e);
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
            throw new IllegalArgumentException("Invalid encrypted holiday API key", e);
        }
        if (payload.length <= IV_BYTES) {
            throw new IllegalArgumentException("Invalid encrypted holiday API key length");
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
            throw new IllegalArgumentException("Unable to decrypt holiday API key", e);
        }
    }

    private void requireConfigured() {
        if (!isConfigured()) {
            throw new IllegalStateException(
                    "HOLIDAY_CREDENTIAL_ENCRYPTION_KEY is required for DB credential storage");
        }
    }
}
