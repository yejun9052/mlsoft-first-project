package com.mlsoft.backend.domain.auth.dto;

import com.mlsoft.backend.domain.user.entity.User;

import java.time.LocalDate;
import java.time.temporal.ChronoUnit;

/**
 * 온보딩 승인 대기 항목 (GET /api/admin/onboardings, SA) — 리뷰 S-1.
 *
 * <p>관리자가 승인 여부를 판단하는 데 필요한 것만 담는다. 특히 {@code backdatedDays}와
 * {@code estimatedAnnualDays}는 <b>화면에서 계산하지 않고 서버가 준다</b> — 프론트가 근속년수
 * 공식을 다시 구현하면 정책이 바뀔 때 관리자에게 틀린 숫자가 보인다.
 *
 * @param backdatedDays       입력한 입사일이 오늘로부터 며칠 전인지 — 클수록 의심스럽다
 * @param estimatedAnnualDays 승인하면 부여될 연차(근속 1년 이상일 때). 1년 미만이면 월차 소급분이라 null
 */
public record OnboardingApprovalResponse(
        Long userId,
        String name,
        String email,
        LocalDate hireDate,
        LocalDate birthDay,
        long backdatedDays,
        Integer yearsOfService
) {
    public static OnboardingApprovalResponse of(User user, LocalDate today) {
        long backdated = ChronoUnit.DAYS.between(user.getHireDate(), today);
        long years = ChronoUnit.YEARS.between(user.getHireDate(), today);
        return new OnboardingApprovalResponse(
                user.getId(),
                user.getName(),
                user.getEmail(),
                user.getHireDate(),
                user.getBirthDay(),
                backdated,
                years < 1 ? null : (int) years
        );
    }
}
