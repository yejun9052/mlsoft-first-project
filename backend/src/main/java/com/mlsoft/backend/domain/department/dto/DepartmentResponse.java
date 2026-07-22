package com.mlsoft.backend.domain.department.dto;

import com.mlsoft.backend.domain.department.entity.Department;
import com.mlsoft.backend.domain.user.entity.User;

/**
 * 부서 응답 (docs/03 부서).
 * LAZY 연관(팀장)을 접근하므로 트랜잭션 내에서 변환한다.
 */
public record DepartmentResponse(
        Long id,
        String name,
        String description,
        Long leaderId,
        String leaderName,
        Long parentId,
        boolean active
) {

    public static DepartmentResponse of(Department department) {
        User leader = department.getLeader();
        return new DepartmentResponse(
                department.getId(),
                department.getName(),
                department.getDescription(),
                leader != null ? leader.getId() : null,
                leader != null ? leader.getName() : null,
                department.getParentId(),
                department.isActive()
        );
    }
}
