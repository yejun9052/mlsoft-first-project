package com.mlsoft.backend.domain.email.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.util.List;

/** 관리자 일괄 메일 요청. */
public record EmailBulkRequest(
        @NotEmpty(message = "수신자를 한 명 이상 선택해주세요.")
        @Size(max = 100, message = "한 번에 최대 100명까지 발송할 수 있습니다.")
        List<@NotNull(message = "수신자 식별자가 올바르지 않습니다.") Long> userIds,
        @NotBlank(message = "메일 제목을 입력해주세요.")
        @Size(max = 255, message = "메일 제목은 255자 이내로 입력해주세요.")
        String title,
        @NotBlank(message = "메일 본문을 입력해주세요.")
        @Size(max = 20_000, message = "메일 본문은 20,000자 이내로 입력해주세요.")
        String content
) {
}
