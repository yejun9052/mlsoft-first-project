package com.mlsoft.backend.domain.department.repository;

import com.mlsoft.backend.domain.department.entity.Department;
import org.springframework.data.jpa.repository.JpaRepository;

/**
 * 부서 저장소.
 */
public interface DepartmentRepository extends JpaRepository<Department, Long> {

    /** 부서명 존재 여부 (초기 데이터 스킵 판별) */
    boolean existsByName(String name);
}
