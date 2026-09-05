package com.mlsoft.backend.domain.email.dto;

import java.time.LocalDateTime;
import java.util.List;

/** 관리자 이메일 양식 응답. */
public record EmailTemplateResponse(
        String templateKey,
        String subjectTemplate,
        String bodyTemplate,
        int version,
        LocalDateTime updatedAt,
        String updatedByName,
        List<String> variables
) {
}
