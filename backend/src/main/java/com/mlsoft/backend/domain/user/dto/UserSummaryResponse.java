package com.mlsoft.backend.domain.user.dto;

import com.mlsoft.backend.domain.department.entity.Department;
import com.mlsoft.backend.domain.user.entity.User;

/**
 * 사용자 경량 응답 — 팀원 목록·승인자 후보용 (GET /api/users/team-members, /api/users/approvers, docs/03).
 * 잔여 연차 등 민감·불필요 정보는 노출하지 않는다.
 */
public record UserSummaryResponse(
        Long id,
        String name,
        String email,
        String role,
        String position,
        Long departmentId,
        String departmentName
) {

    public static UserSummaryResponse of(User user) {
        Department department = user.getDepartment();
        return new UserSummaryResponse(
                user.getId(),
                user.getName(),
                user.getEmail(),
                user.getRole().name(),
                user.getPosition(),
                department != null ? department.getId() : null,
                department != null ? department.getName() : null
        );
    }
}
