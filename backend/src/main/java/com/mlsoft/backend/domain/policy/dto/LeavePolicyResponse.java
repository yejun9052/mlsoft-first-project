package com.mlsoft.backend.domain.policy.dto;

import com.mlsoft.backend.domain.policy.entity.LeavePolicy;

import java.math.BigDecimal;

/**
 * 근속년수별 연차 정책 응답 (GET/PATCH /api/admin/leave-policies — docs/03).
 */
public record LeavePolicyResponse(
        Long id,
        int yearsOfService,
        BigDecimal annualLeaveDays,
        String description,
        boolean active
) {

    public static LeavePolicyResponse of(LeavePolicy policy) {
        return new LeavePolicyResponse(
                policy.getId(),
                policy.getYearsOfService(),
                policy.getAnnualLeaveDays(),
                policy.getDescription(),
                policy.isActive()
        );
    }
}
