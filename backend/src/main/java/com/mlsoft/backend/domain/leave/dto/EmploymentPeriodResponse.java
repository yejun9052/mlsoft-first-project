package com.mlsoft.backend.domain.leave.dto;

import com.mlsoft.backend.domain.user.entity.EmploymentPeriod;
import com.mlsoft.backend.domain.user.entity.User;

import java.time.LocalDate;

/**
 * 본인 근속 구간 응답 (GET /api/leaves/me/periods, 설계-초안/재입사자-처리-설계-2026-09-07 §7).
 *
 * <p>과거 근속 구간은 {@code employment_periods}에서, 마지막 현재 구간은 {@code users}에서
 * 조합한다. 과거 잔액 스냅샷이 없으므로 구간별 부여·사용 일수는 집계하지 않고 순번과 날짜만
 * 제공한다.
 */
public record EmploymentPeriodResponse(
        int seq,
        LocalDate hireDate,
        LocalDate retiredAt,
        boolean current
) {

    /** 종료된 과거 근속 구간 응답을 만든다. */
    public static EmploymentPeriodResponse of(EmploymentPeriod period) {
        return new EmploymentPeriodResponse(
                period.getSeq(),
                period.getHireDate(),
                period.getRetiredAt(),
                false
        );
    }

    /** users에 보관된 마지막 근속 구간 응답을 만든다. */
    public static EmploymentPeriodResponse current(User user, int seq) {
        return new EmploymentPeriodResponse(
                seq,
                user.getHireDate(),
                user.getRetiredAt(),
                user.isActive()
        );
    }
}
