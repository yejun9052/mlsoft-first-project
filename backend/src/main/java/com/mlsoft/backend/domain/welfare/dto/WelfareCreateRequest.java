package com.mlsoft.backend.domain.welfare.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

/**
 * 복리후생 신청 요청 (POST /api/welfare-requests — docs/03).
 * 신청자 ID는 body로 받지 않는다 — Authentication에서 추출 (docs/04).
 * reason은 서버가 자동 생성하지 않고 클라이언트가 입력한 값을 그대로 저장한다.
 */
public record WelfareCreateRequest(
        @NotNull(message = "정책을 선택해주세요.")
        Long policyId,

        @NotBlank(message = "신청 사유를 입력해주세요.")
        String reason,

        /** 서브 승인자 (선택) — 재직 중 TEAM_LEADER·SYSTEM_ADMIN만 지정 가능 */
        Long subApproverId
) {
}
