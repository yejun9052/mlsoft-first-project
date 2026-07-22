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
        LocalDateTime createdAt
) {

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
                user.getCreatedAt()
        );
    }
}
