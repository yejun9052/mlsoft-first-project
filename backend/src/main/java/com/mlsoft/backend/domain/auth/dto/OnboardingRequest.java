package com.mlsoft.backend.domain.auth.dto;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Past;
import jakarta.validation.constraints.PastOrPresent;

import java.time.LocalDate;

/**
 * 온보딩 요청 (POST /api/auth/onboarding — docs/01 2-1).
 * base_days는 입력받지 않는다 — 입사일 기준 정책 자동 계산 (위조 방지, 갭분석 C-1).
 */
public record OnboardingRequest(
        @NotNull(message = "생일을 입력해주세요.")
        @Past(message = "생일은 과거 날짜여야 합니다.")
        LocalDate birthDay,

        @NotNull(message = "입사일을 입력해주세요.")
        @PastOrPresent(message = "입사일은 오늘 이전이어야 합니다.")
        LocalDate hireDate
) {
}
