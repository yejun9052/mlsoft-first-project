package com.mlsoft.backend.domain.department.repository;

import com.mlsoft.backend.domain.department.entity.Department;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

/**
 * 부서 저장소.
 */
public interface DepartmentRepository extends JpaRepository<Department, Long> {

    /** 부서명 존재 여부 (초기 데이터 스킵 판별) */
    boolean existsByName(String name);

    /** 부서명으로 조회 (신규 가입 시 미배정 부서 배속) */
    Optional<Department> findByName(String name);
}
