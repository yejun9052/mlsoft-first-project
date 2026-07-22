package com.mlsoft.backend.domain.user.repository;

import com.mlsoft.backend.domain.department.entity.Department;
import com.mlsoft.backend.domain.user.entity.Role;
import com.mlsoft.backend.domain.user.entity.User;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

/**
 * 사원 저장소.
 */
public interface UserRepository extends JpaRepository<User, Long> {

    /** 이메일로 사원 조회 (OAuth 로그인 키) */
    Optional<User> findByEmail(String email);

    /** 이메일 가입 여부 (자동 가입 판별) */
    boolean existsByEmail(String email);

    /** 재직 중인 해당 권한의 최초(id 오름차순) 사용자 — primary 승인자 SYSTEM_ADMIN fallback (검증 Y-3) */
    Optional<User> findFirstByRoleAndIsActiveTrueOrderByIdAsc(Role role);

    /** 퇴직자 목록 (GET /api/users/retired, SA) */
    Page<User> findByIsActiveFalse(Pageable pageable);

    /** 서브 승인자 후보 — 재직 중 TEAM_LEADER·SYSTEM_ADMIN, 본인 제외 (GET /api/users/approvers) */
    List<User> findByRoleInAndIsActiveTrueAndIdNot(List<Role> roles, Long excludeId);

    /** 내 부서 팀원 목록 — 재직 중만, 이름순 (GET /api/users/team-members) */
    List<User> findByDepartmentAndIsActiveTrueOrderByNameAsc(Department department);

    /** 전체 목록 검색 — keyword(이름·이메일)·role 필터, 퇴직자 제외 (GET /api/users, SA) */
    @Query("select u from User u where u.isActive = true "
            + "and (:role is null or u.role = :role) "
            + "and (:keyword is null or u.name like concat('%', :keyword, '%') or u.email like concat('%', :keyword, '%'))")
    Page<User> search(@Param("role") Role role, @Param("keyword") String keyword, Pageable pageable);
}
