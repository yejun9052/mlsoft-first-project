package com.mlsoft.backend.domain.leave.dto;

import com.mlsoft.backend.domain.leave.entity.LeaveType;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;

import java.time.LocalDate;
import java.util.List;

/**
 * 연차 신청 요청 (POST /api/leaves — docs/03).
 * 신청자 ID는 body로 받지 않는다 — Authentication에서 추출 (docs/04).
 * 주말·과거·중복·잔여 검증은 서비스에서 수행한다.
 */
public record LeaveCreateRequest(
        @NotNull(message = "연차 종류를 선택해주세요.")
        LeaveType leaveType,

        @NotEmpty(message = "신청 날짜를 선택해주세요.")
        List<LocalDate> dates,

        @NotBlank(message = "신청 사유를 입력해주세요.")
        String reason,

        /** 서브 승인자 (선택) — 재직 중 TEAM_LEADER·SYSTEM_ADMIN만 지정 가능 */
        Long subApproverId
) {
}
