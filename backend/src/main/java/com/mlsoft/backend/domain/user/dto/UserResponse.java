package com.mlsoft.backend.domain.user.dto;

import com.mlsoft.backend.domain.department.entity.Department;
import com.mlsoft.backend.domain.user.entity.User;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

/**
 * 사용자 상세 응답 (docs/03 사용자).
 * LAZY 연관(부서)을 접근하므로 트랜잭션 내에서 변환한다 (LeaveResponse와 동일 원칙).
 */
public record UserResponse(
        Long id,
        String name,
        String email,
        String role,
        String position,
        Long departmentId,
        String departmentName,
        BigDecimal baseDays,
        BigDecimal useDays,
        BigDecimal bonusDays,
        BigDecimal advanceDays,
        BigDecimal remainingDays,
        LocalDate hireDate,
        LocalDate birthDay,
        boolean isActive,
        LocalDate retiredAt,
        LocalDateTime createdAt,
        LocalDateTime purgedAt,
        String purgeHoldReason,
        long elapsedDays,
        int elapsedYears,
        int elapsedMonths,
        boolean purgeEligible
) {

    /** 기존 호출부와 테스트의 생성 시그니처를 유지한다. */
    public UserResponse(Long id, String name, String email, String role, String position,
                        Long departmentId, String departmentName, BigDecimal baseDays,
                        BigDecimal useDays, BigDecimal bonusDays, BigDecimal advanceDays,
                        BigDecimal remainingDays, LocalDate hireDate, LocalDate birthDay,
                        boolean isActive, LocalDate retiredAt, LocalDateTime createdAt) {
        this(id, name, email, role, position, departmentId, departmentName, baseDays, useDays,
                bonusDays, advanceDays, remainingDays, hireDate, birthDay, isActive, retiredAt,
                createdAt, null, null, 0L, 0, 0, false);
    }

    public static UserResponse of(User user) {
        Department department = user.getDepartment();
        return new UserResponse(
                user.getId(),
                user.getName(),
                user.getEmail(),
                user.getRole().name(),
                user.getPosition(),
                department != null ? department.getId() : null,
                department != null ? department.getName() : null,
                user.getBaseDays(),
                user.getUseDays(),
                user.getBonusDays(),
                user.getAdvanceDays(),
                user.getRemainingDays(),
                user.getHireDate(),
                user.getBirthDay(),
                user.isActive(),
                user.getRetiredAt(),
                user.getCreatedAt(),
                user.getPurgedAt(),
                user.getPurgeHoldReason(),
                0L,
                0,
                0,
                false
        );
    }

    /** 퇴직 목록용 응답 — 퇴직 경과 기간과 파기 가능 여부를 함께 담는다. */
    public static UserResponse ofRetired(User user, long elapsedDays, int elapsedYears,
                                         int elapsedMonths, boolean purgeEligible) {
        Department department = user.getDepartment();
        return new UserResponse(
                user.getId(),
                user.getName(),
                user.getEmail(),
                user.getRole().name(),
                user.getPosition(),
                department != null ? department.getId() : null,
                department != null ? department.getName() : null,
                user.getBaseDays(),
                user.getUseDays(),
                user.getBonusDays(),
                user.getAdvanceDays(),
                user.getRemainingDays(),
                user.getHireDate(),
                user.getBirthDay(),
                user.isActive(),
                user.getRetiredAt(),
                user.getCreatedAt(),
                user.getPurgedAt(),
                user.getPurgeHoldReason(),
                elapsedDays,
                elapsedYears,
                elapsedMonths,
                purgeEligible
        );
    }
}
