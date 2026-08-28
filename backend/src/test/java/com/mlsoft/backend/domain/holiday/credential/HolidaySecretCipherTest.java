package com.mlsoft.backend.domain.holiday.credential;

import org.junit.jupiter.api.Test;

import java.util.Base64;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class HolidaySecretCipherTest {

    private static final String KEY = Base64.getEncoder().encodeToString(new byte[32]);

    @Test
    void encryptThenDecryptRestoresTheApiKeyWithoutStoringPlaintext() {
        HolidaySecretCipher cipher = new HolidaySecretCipher(KEY);

        String encrypted = cipher.encrypt("test-holiday-api-key");

        assertNotEquals("test-holiday-api-key", encrypted);
        assertEquals("test-holiday-api-key", cipher.decrypt(encrypted));
        assertNotEquals(encrypted, cipher.encrypt("test-holiday-api-key"));
    }

    @Test
    void missingEncryptionKeyCannotBeUsedForDbStorage() {
        HolidaySecretCipher cipher = new HolidaySecretCipher("");

        assertThrows(IllegalStateException.class, () -> cipher.encrypt("api-key"));
    }
}
