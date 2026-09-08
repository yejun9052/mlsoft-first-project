package com.mlsoft.backend.domain.auth.dto;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Past;
import jakarta.validation.constraints.Size;

import java.time.LocalDate;

/**
 * 온보딩 요청 (POST /api/auth/onboarding — docs/01 2-1).
 * base_days는 입력받지 않는다 — 입사일 기준 정책 자동 계산 (위조 방지, 갭분석 C-1).
 */
public record OnboardingRequest(
        @NotNull(message = "생일을 입력해주세요.")
        @Past(message = "생일은 과거 날짜여야 합니다.")
        LocalDate birthDay,

        /**
         * 입사일. <b>{@code @PastOrPresent}를 쓰지 않는다</b> — 그 애노테이션은 JVM 기본 시간대로
         * "오늘"을 판단하는데 이 시스템의 모든 날짜 계산은 KST로 고정돼 있다 (리뷰 I-7).
         *
         * <p>운영 컨테이너의 JVM 기본값이 UTC이면 KST 00~09시 사이에는 UTC 날짜가 하루 이르므로,
         * 한국에서 <b>오늘 입사한 신입이 오늘 날짜를 넣으면 미래로 판정돼 400</b>이 됐다.
         * 경계 판정은 {@code AuthService}가 KST로 한다 ({@code FUTURE_HIRE_DATE}).
         */
        @NotNull(message = "입사일을 입력해주세요.")
        LocalDate hireDate,

        /** 직급은 회사별 체계가 달라 자유 입력하며 선택 항목이다. */
        @Size(max = 50, message = "직급은 50자 이내로 입력해주세요.")
        String jobGrade
) {

    /** 기존 호출부와 테스트의 2개 필드 생성 시그니처를 유지한다. */
    public OnboardingRequest(LocalDate birthDay, LocalDate hireDate) {
        this(birthDay, hireDate, null);
    }
}
