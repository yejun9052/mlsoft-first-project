package com.mlsoft.backend.domain.leave.dto;

import jakarta.validation.constraints.NotNull;

/**
 * 승인/반려 요청 (POST /api/leaves/{id}/approval, /cancel-approval — docs/03).
 * approved=true 승인, false 반려. comment는 처리 이력에 기록 (선택, 없으면 빈 문자열).
 */
public record ApprovalRequest(
        @NotNull(message = "승인 여부를 지정해주세요.")
        Boolean approved,

        String comment
) {
}
