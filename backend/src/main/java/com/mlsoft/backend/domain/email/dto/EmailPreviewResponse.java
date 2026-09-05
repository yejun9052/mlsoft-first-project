package com.mlsoft.backend.domain.email.dto;

/** 이메일 양식 미리보기 결과. */
public record EmailPreviewResponse(
        String subject,
        String html
) {
}
