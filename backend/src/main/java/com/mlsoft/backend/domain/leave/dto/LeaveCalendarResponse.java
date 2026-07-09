package com.mlsoft.backend.domain.leave.dto;

import com.mlsoft.backend.domain.department.entity.Department;
import com.mlsoft.backend.domain.leave.entity.LeaveRequest;
import com.mlsoft.backend.domain.user.entity.User;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

/**
 * 캘린더·팀 현황 응답 (GET /api/leaves/calendar, /team — docs/03).
 * 사유 마스킹 (docs/01 2-5(b), 검증 Y-4): 타인의 연차는 이름·기간·종류만, 사유는 열람 권한자
 * (본인·해당 건 승인자·SYSTEM_ADMIN)에게만 채워 보낸다 — 그 외에는 reason=null.
 */
public record LeaveCalendarResponse(
        Long id,
        Long userId,
        String userName,
        String departmentName,
        String leaveType,
        BigDecimal days,
        List<LocalDate> dates,
        String status,
        String reason
) {

    public static LeaveCalendarResponse of(LeaveRequest leave, boolean includeReason) {
        User applicant = leave.getUser();
        Department department = applicant.getDepartment();
        return new LeaveCalendarResponse(
                leave.getId(),
                applicant.getId(),
                applicant.getName(),
                department != null ? department.getName() : null,
                leave.getLeaveType().name(),
                leave.getDays(),
                List.copyOf(leave.getDates()),
                leave.getStatus().name(),
                includeReason ? leave.getRequestReason() : null
        );
    }
}
