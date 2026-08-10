package com.mlsoft.backend.domain.leave.dto;

import com.mlsoft.backend.domain.leave.entity.LeaveType;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

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

        /**
         * 신청 날짜 목록. {@code @Size} 상한은 <b>페이로드 절대 가드</b>다 —
         * 실제 정책 상한은 설정 {@code leave_max_dates_per_request}(기본 30일)이고 서비스에서 검사한다.
         * 여기서 1년치를 넘는 요청을 먼저 잘라 DB 조회·사용자 로딩까지 가지 않게 한다 (리뷰 I-3).
         */
        @NotEmpty(message = "신청 날짜를 선택해주세요.")
        @Size(max = 366, message = "신청 날짜가 너무 많습니다.")
        List<LocalDate> dates,

        @NotBlank(message = "신청 사유를 입력해주세요.")
        @Size(max = 255, message = "사유는 255자 이내로 입력해주세요.")
        String reason,

        /** 서브 승인자 (선택) — 재직 중 TEAM_LEADER·SYSTEM_ADMIN만 지정 가능 */
        Long subApproverId
) {
}
