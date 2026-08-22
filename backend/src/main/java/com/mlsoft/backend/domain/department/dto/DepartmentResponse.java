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
        boolean active,
        /**
         * 시스템 기본 "미배정" 부서인가 — 프론트가 <b>이름 문자열로 판별하지 않게</b> 하려고 내린다.
         * 이름은 바뀔 수 있고, 바뀌면 이름 비교는 조용히 깨진다 (2026-08-20).
         */
        boolean unassigned
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
                department.isActive(),
                department.isSystemDefault()
        );
    }
}
