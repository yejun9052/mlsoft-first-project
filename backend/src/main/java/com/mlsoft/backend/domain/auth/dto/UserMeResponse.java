package com.mlsoft.backend.domain.auth.dto;

import com.mlsoft.backend.domain.user.entity.User;

import java.math.BigDecimal;
import java.time.LocalDate;

/**
 * 내 정보 응답 (GET /api/auth/me — docs/03 인증).
 * onboarded=false면 프론트가 온보딩 페이지로 유도한다.
 */
public record UserMeResponse(
        Long id,
        String name,
        String email,
        String role,
        Long departmentId,
        String departmentName,
        BigDecimal baseDays,
        BigDecimal useDays,
        BigDecimal bonusDays,
        BigDecimal advanceDays,
        LocalDate hireDate,
        LocalDate birthDay,
        boolean onboarded
) {

    /** User 엔티티 → 응답 변환 (부서 LAZY 접근 — 트랜잭션 내 호출 필수) */
    public static UserMeResponse from(User user) {
        boolean hasDepartment = user.getDepartment() != null;
        return new UserMeResponse(
                user.getId(),
                user.getName(),
                user.getEmail(),
                user.getRole().name(),
                hasDepartment ? user.getDepartment().getId() : null,
                hasDepartment ? user.getDepartment().getName() : null,
                user.getBaseDays(),
                user.getUseDays(),
                user.getBonusDays(),
                user.getAdvanceDays(),
                user.getHireDate(),
                user.getBirthDay(),
                user.isOnboardingCompleted()
        );
    }
}
