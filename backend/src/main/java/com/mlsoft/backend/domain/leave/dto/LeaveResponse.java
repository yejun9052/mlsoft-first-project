package com.mlsoft.backend.domain.leave.dto;

import com.mlsoft.backend.domain.department.entity.Department;
import com.mlsoft.backend.domain.leave.entity.LeaveRequest;
import com.mlsoft.backend.domain.user.entity.User;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

/**
 * 연차 신청 상세/목록 응답 (docs/03).
 * 사유가 노출되는 컨텍스트(본인 내역·승인자 대기·SA 전체)에서 사용한다 — 마스킹이 필요한
 * 캘린더·팀 현황은 {@link LeaveCalendarResponse}를 쓴다 (docs/01 2-5(b) 사유 마스킹).
 * LAZY 연관(신청자·부서·승인자)을 접근하므로 트랜잭션 내에서 변환한다.
 */
public record LeaveResponse(
        Long id,
        Long userId,
        String userName,
        Long departmentId,
        String departmentName,
        String leaveType,
        BigDecimal days,
        List<LocalDate> dates,
        String status,
        String requestReason,
        String cancelReason,
        Long primaryApproverId,
        String primaryApproverName,
        Long subApproverId,
        String subApproverName,
        LocalDateTime createdAt
) {

    public static LeaveResponse of(LeaveRequest leave) {
        User applicant = leave.getUser();
        Department department = applicant.getDepartment();
        User primary = leave.getPrimaryApprover();
        User sub = leave.getSubApprover();
        return new LeaveResponse(
                leave.getId(),
                applicant.getId(),
                applicant.getName(),
                department != null ? department.getId() : null,
                department != null ? department.getName() : null,
                leave.getLeaveType().name(),
                leave.getDays(),
                List.copyOf(leave.getDates()),
                leave.getStatus().name(),
                leave.getRequestReason(),
                leave.getCancelReason(),
                primary.getId(),
                primary.getName(),
                sub != null ? sub.getId() : null,
                sub != null ? sub.getName() : null,
                leave.getCreatedAt()
        );
    }
}
