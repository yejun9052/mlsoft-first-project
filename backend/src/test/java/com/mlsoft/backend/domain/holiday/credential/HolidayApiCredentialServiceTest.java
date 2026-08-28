package com.mlsoft.backend.domain.holiday.credential;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Base64;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
class HolidayApiCredentialServiceTest {

    private static final String KEY = Base64.getEncoder().encodeToString(new byte[32]);

    @Mock
    private HolidayApiCredentialRepository repository;

    private HolidayApiCredentialService service;
    private HolidaySecretCipher cipher;

    @BeforeEach
    void setUp() {
        cipher = new HolidaySecretCipher(KEY);
        service = new HolidayApiCredentialService(repository, cipher, "environment-key", true);
    }

    @Test
    void seedsEnvironmentKeyAsEncryptedSingleProviderRow() {
        given(repository.findByProvider(HolidayApiCredentialService.DATA_GO_KR_PROVIDER))
                .willReturn(Optional.empty());

        assertTrue(service.seedFromEnvironment());

        var captor = org.mockito.ArgumentCaptor.forClass(HolidayApiCredential.class);
        verify(repository).save(captor.capture());
        HolidayApiCredential saved = captor.getValue();
        assertEquals(HolidayApiCredentialService.DATA_GO_KR_PROVIDER, saved.getProvider());
        assertEquals("environment-key", cipher.decrypt(saved.getEncryptedApiKey()));
        org.junit.jupiter.api.Assertions.assertNotEquals(
                "environment-key", saved.getEncryptedApiKey());
    }

    @Test
    void prefersActiveEncryptedDbKeyOverEnvironmentFallback() {
        HolidayApiCredential stored = HolidayApiCredential.create(
                HolidayApiCredentialService.DATA_GO_KR_PROVIDER,
                cipher.encrypt("db-key"));
        given(repository.findByProviderAndActiveTrue(HolidayApiCredentialService.DATA_GO_KR_PROVIDER))
                .willReturn(Optional.of(stored));

        assertEquals(Optional.of("db-key"), service.resolveApiKey());
    }

    @Test
    void doesNotOverwriteDatabaseKeyUnlessExplicitSeedIsEnabled() {
        service = new HolidayApiCredentialService(repository, cipher, "environment-key", false);

        assertEquals(Optional.of("environment-key"), service.resolveApiKey());
        org.junit.jupiter.api.Assertions.assertFalse(service.seedFromEnvironment());
        verify(repository, org.mockito.Mockito.never()).save(any());
    }
}
