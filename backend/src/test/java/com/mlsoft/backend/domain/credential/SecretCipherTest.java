package com.mlsoft.backend.domain.credential;

import org.junit.jupiter.api.Test;

import java.util.Base64;

import static org.junit.jupiter.api.Assertions.assertEquals;

class SecretCipherTest {

    private static final String LEGACY_KEY = Base64.getEncoder().encodeToString(new byte[32]);

    @Test
    void currentKey가비어있으면_기존공휴일키로복호화한다() {
        SecretCipher cipher = new SecretCipher("", LEGACY_KEY);

        String encrypted = cipher.encrypt("staging-secret");

        assertEquals("staging-secret", cipher.decrypt(encrypted));
    }
}
