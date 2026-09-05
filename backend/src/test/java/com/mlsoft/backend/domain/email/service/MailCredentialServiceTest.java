package com.mlsoft.backend.domain.email.service;

import com.mlsoft.backend.domain.credential.SecretCipher;
import com.mlsoft.backend.domain.email.dto.MailCredentialMaskDto;
import com.mlsoft.backend.domain.email.entity.MailCredential;
import com.mlsoft.backend.domain.email.repository.MailCredentialRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
class MailCredentialServiceTest {

    @Mock
    private MailCredentialRepository repository;
    @Mock
    private SecretCipher cipher;

    @Test
    void 저장시_원문이아닌암호문을저장하고_조회는마스킹한다() {
        MailCredentialService service = new MailCredentialService(repository, cipher);
        MailCredential saved = MailCredential.create("SMTP", "sender@mlsoft.com", "encrypted");
        given(repository.findByProvider("SMTP")).willReturn(Optional.empty());
        given(cipher.encrypt("app-password")).willReturn("encrypted");
        given(repository.save(any(MailCredential.class))).willReturn(saved);

        MailCredentialMaskDto result = service.saveOrRotate("smtp", "sender@mlsoft.com", "app-password");

        verify(cipher).encrypt("app-password");
        assertEquals("••••word", result.maskedSecret());
        assertFalse(result.maskedSecret().contains("app-password"));
    }

    @Test
    void 활성계정은복호화해발송기에전달할수있다() {
        MailCredentialService service = new MailCredentialService(repository, cipher);
        MailCredential stored = MailCredential.create("SMTP", "db@mlsoft.com", "encrypted");
        given(repository.findByProviderAndActiveTrue("SMTP")).willReturn(Optional.of(stored));
        given(cipher.decrypt("encrypted")).willReturn("db-secret");

        var result = service.resolveActiveCredential().orElseThrow();

        assertEquals("db@mlsoft.com", result.username());
        assertEquals("db-secret", result.secret());
    }

    @Test
    void 복호화실패는빈값으로반환해환경변수fallback을허용한다() {
        MailCredentialService service = new MailCredentialService(repository, cipher);
        MailCredential stored = MailCredential.create("SMTP", "db@mlsoft.com", "bad");
        given(repository.findByProviderAndActiveTrue("SMTP")).willReturn(Optional.of(stored));
        given(cipher.decrypt("bad")).willThrow(new IllegalArgumentException("bad key"));

        assertTrue(service.resolveActiveCredential().isEmpty());
    }
}
