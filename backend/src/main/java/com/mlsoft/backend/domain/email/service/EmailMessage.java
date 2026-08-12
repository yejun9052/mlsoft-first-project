package com.mlsoft.backend.domain.email.service;

/**
 * 실제 발송·이력 저장에 사용하는 완성된 메일.
 */
public record EmailMessage(
        String title,
        String content
) {
}
