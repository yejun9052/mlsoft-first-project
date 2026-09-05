package com.mlsoft.backend.domain.email.dto;

/**
 * 메일 계정 마스킹 조회 응답.
 *
 * <p>비밀값 원문은 절대 응답에 담지 않고 마스크와 마지막 네 자리만 제공한다.</p>
 */
public record MailCredentialMaskDto(
        String provider,
        String username,
        String maskedSecret,
        boolean active
) {
}
