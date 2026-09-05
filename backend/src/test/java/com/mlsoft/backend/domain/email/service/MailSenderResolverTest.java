package com.mlsoft.backend.domain.email.service;

import org.junit.jupiter.api.Test;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mail.javamail.JavaMailSenderImpl;

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotSame;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.mock;

class MailSenderResolverTest {

    @Test
    void DB계정이있으면_환경설정은유지하고DB사용자명으로sender를만든다() {
        JavaMailSenderImpl environment = new JavaMailSenderImpl();
        environment.setHost("smtp.internal");
        environment.setPort(2525);
        MailCredentialService service = mock(MailCredentialService.class);
        given(service.resolveActiveCredential()).willReturn(Optional.of(
                new MailCredentialService.ResolvedCredential("db@mlsoft.com", "secret", "v1")));

        MailSenderResolver resolver = new MailSenderResolver(environment, service, "env@mlsoft.com");

        MailSenderResolver.SenderSelection result = resolver.resolve();
        JavaMailSenderImpl sender = (JavaMailSenderImpl) result.sender();

        assertEquals("db@mlsoft.com", result.username());
        assertEquals("db@mlsoft.com", sender.getUsername());
        assertEquals("smtp.internal", sender.getHost());
        assertEquals(2525, sender.getPort());
    }

    @Test
    void DB계정이없으면_기존환경변수sender를그대로쓴다() {
        JavaMailSender environment = mock(JavaMailSender.class);
        MailCredentialService service = mock(MailCredentialService.class);
        given(service.resolveActiveCredential()).willReturn(Optional.empty());

        MailSenderResolver.SenderSelection result =
                new MailSenderResolver(environment, service, "env@mlsoft.com").resolve();

        assertSame(environment, result.sender());
        assertEquals("env@mlsoft.com", result.username());
    }

    @Test
    void 회전된계정은암호문cacheKey가달라져새sender를쓴다() {
        JavaMailSenderImpl environment = new JavaMailSenderImpl();
        MailCredentialService service = mock(MailCredentialService.class);
        given(service.resolveActiveCredential())
                .willReturn(Optional.of(new MailCredentialService.ResolvedCredential(
                        "old@mlsoft.com", "old-secret", "v1")))
                .willReturn(Optional.of(new MailCredentialService.ResolvedCredential(
                        "new@mlsoft.com", "new-secret", "v2")));
        MailSenderResolver resolver = new MailSenderResolver(environment, service, "env@mlsoft.com");

        MailSenderResolver.SenderSelection oldSelection = resolver.resolve();
        MailSenderResolver.SenderSelection newSelection = resolver.resolve();

        assertNotSame(oldSelection.sender(), newSelection.sender());
        assertEquals("new@mlsoft.com", newSelection.username());
    }

    private static void assertSame(Object expected, Object actual) {
        if (expected != actual) {
            throw new AssertionError("같은 fallback sender여야 합니다.");
        }
    }
}
