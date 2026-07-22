package com.mlsoft.backend.domain.department.repository;

import com.mlsoft.backend.domain.department.entity.Department;
import com.mlsoft.backend.domain.user.entity.User;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

/**
 * 부서 저장소.
 */
public interface DepartmentRepository extends JpaRepository<Department, Long> {

    /** 부서명 존재 여부 (초기 데이터 스킵 판별) */
    boolean existsByName(String name);

    /** 부서명으로 조회 (신규 가입 시 미배정 부서 배속) */
    Optional<Department> findByName(String name);

    /** 활성 부서 전체 — 플랫 목록/트리 조회 기반 (GET /api/departments, /tree) */
    List<Department> findByActiveTrueOrderByIdAsc();

    /** 활성 부서 단건 조회 — 수정·비활성화·부서배정 시 대상 조회 */
    Optional<Department> findByIdAndActiveTrue(Long id);

    /** 이 사람이 팀장인 부서 전부 — 퇴직 이관 시 leader 해제 대상 (active 필터 없음, docs/01 2-9) */
    List<Department> findByLeader(User leader);
}
