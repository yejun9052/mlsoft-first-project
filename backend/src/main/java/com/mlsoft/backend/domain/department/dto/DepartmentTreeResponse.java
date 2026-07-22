package com.mlsoft.backend.domain.department.dto;

import com.mlsoft.backend.domain.department.entity.Department;
import com.mlsoft.backend.domain.user.entity.User;

import java.util.List;

/**
 * 부서 2단계 계층 트리 응답 (GET /api/departments/tree — docs/03).
 * LAZY 연관(팀장)을 접근하므로 트랜잭션 내에서 변환한다.
 */
public record DepartmentTreeResponse(
        Long id,
        String name,
        String description,
        Long leaderId,
        String leaderName,
        List<DepartmentTreeResponse> children
) {

    public static DepartmentTreeResponse of(Department root, List<Department> children) {
        User leader = root.getLeader();
        return new DepartmentTreeResponse(
                root.getId(),
                root.getName(),
                root.getDescription(),
                leader != null ? leader.getId() : null,
                leader != null ? leader.getName() : null,
                children.stream().map(child -> DepartmentTreeResponse.of(child, List.of())).toList()
        );
    }
}
