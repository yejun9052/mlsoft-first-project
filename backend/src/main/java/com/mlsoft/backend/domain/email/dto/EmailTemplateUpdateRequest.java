package com.mlsoft.backend.domain.email.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/** 이메일 양식 저장·미리보기 요청. */
public record EmailTemplateUpdateRequest(
        @NotBlank(message = "메일 제목 양식을 입력해주세요.")
        @Size(max = 255, message = "메일 제목 양식은 255자 이내로 입력해주세요.")
        String subjectTemplate,
        @NotBlank(message = "메일 본문 양식을 입력해주세요.")
        @Size(max = 20_000, message = "메일 본문 양식은 20,000자 이내로 입력해주세요.")
        String bodyTemplate
) {
}
