package com.mlsoft.backend.domain.leave.dto;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

/**
 * 승인/반려 요청 (POST /api/leaves/{id}/approval, /cancel-approval — docs/03).
 * approved=true 승인, false 반려. comment는 처리 이력에 기록 (선택, 없으면 빈 문자열).
 */
public record ApprovalRequest(
        @NotNull(message = "승인 여부를 지정해주세요.")
        Boolean approved,

        @Size(max = 255, message = "처리 코멘트는 255자 이내로 입력해주세요.")
        String comment
) {
}
