package com.mlsoft.backend.domain.policy.dto;

import com.mlsoft.backend.domain.leave.entity.LeaveResetHistory;

import java.math.BigDecimal;
import java.time.LocalDate;

/**
 * 기산일 리셋·소멸 이력 응답 (GET /api/admin/reset-histories, SA — docs/03).
 * LAZY 연관(user)을 접근하므로 트랜잭션 내에서 변환한다 (UserResponse·DepartmentResponse와 동일 원칙).
 */
public record LeaveResetHistoryResponse(
        Long id,
        Long userId,
        String userName,
        LocalDate resetDate,
        BigDecimal prevBaseDays,
        BigDecimal prevUseDays,
        BigDecimal expiredDays,
        BigDecimal advanceSettled,
        BigDecimal newBaseDays
) {

    public static LeaveResetHistoryResponse of(LeaveResetHistory history) {
        return new LeaveResetHistoryResponse(
                history.getId(),
                history.getUser().getId(),
                history.getUser().getName(),
                history.getResetDate(),
                history.getPrevBaseDays(),
                history.getPrevUseDays(),
                history.getExpiredDays(),
                history.getAdvanceSettled(),
                history.getNewBaseDays()
        );
    }
}
