package com.mlsoft.backend.domain.welfare.dto;

import com.mlsoft.backend.domain.welfare.entity.WelfareTarget;
import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

import java.math.BigDecimal;

/**
 * 복리후생 정책 생성/수정 요청 (POST·PATCH /api/welfare-policies — docs/03).
 * 생성·수정 모두 전체 필드를 다시 지정하는 전체 갱신 방식이다(v1 정책과 동일).
 */
public record WelfarePolicyRequest(
        @NotBlank(message = "카테고리를 입력해주세요.")
        String category,

        @NotNull(message = "대상을 선택해주세요.")
        WelfareTarget target,

        @NotNull(message = "부여 일수를 입력해주세요.")
        @DecimalMin(value = "0.0", message = "부여 일수는 0~365일 사이여야 합니다.")
        @DecimalMax(value = "365.0", message = "부여 일수는 0~365일 사이여야 합니다.")
        BigDecimal defaultDays,

        @NotBlank(message = "제출자료 안내를 입력해주세요.")
        String defaultEvidence,

        @NotBlank(message = "설명을 입력해주세요.")
        String description
) {
}
