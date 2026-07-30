package com.mlsoft.backend.domain.leave.dto;

import com.mlsoft.backend.domain.department.entity.Department;
import com.mlsoft.backend.domain.leave.entity.LeaveActionHistory;
import com.mlsoft.backend.domain.leave.entity.LeaveRequest;
import com.mlsoft.backend.domain.user.entity.User;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * 연차 처리 로그 응답 (GET /api/leave-histories — docs/03 처리 이력).
 *
 * <p>건별 이력({@link LeaveHistoryResponse})과 달리 "누가 누구의 어떤 신청을 처리했는가"를 한 행에서
 * 읽을 수 있어야 하므로 신청자·부서·신청 내용을 함께 내려준다.
 * LAZY 연관을 접근하므로 트랜잭션 내에서 변환한다 (DepartmentResponse와 동일 원칙).
 */
public record LeaveHistoryLogResponse(
        Long id,
        Long requestId,
        String action,
        String actorName,
        String userName,
        String departmentName,
        String leaveType,
        BigDecimal days,
        String comment,
        LocalDateTime createdAt
) {

    public static LeaveHistoryLogResponse of(LeaveActionHistory history) {
        User user = history.getUser();
        Department department = user.getDepartment();
        LeaveRequest request = history.getLeaveRequest();
        return new LeaveHistoryLogResponse(
                history.getId(),
                request.getId(),
                history.getAction().name(),
                history.getActor().getName(),
                user.getName(),
                department != null ? department.getName() : null,
                request.getLeaveType().name(),
                request.getDays(),
                history.getComment(),
                history.getCreatedAt()
        );
    }
}
