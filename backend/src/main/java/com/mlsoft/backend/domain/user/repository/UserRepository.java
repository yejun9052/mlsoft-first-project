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

    /** 재직 중인 해당 권한의 최초(id 오름차순) 사용자 — 퇴직 시 결재 이관 대상 조회 등 */
    Optional<User> findFirstByRoleAndIsActiveTrueOrderByIdAsc(Role role);

    /**
     * primary 승인자 SYSTEM_ADMIN fallback (검증 Y-3, 리뷰 I-5b·I-5c).
     * <p>온보딩 미완료자는 인터셉터가 막아 결재를 못 하므로 제외하고, 신청자 본인도 제외한다
     * (셀프 결재 방지 — 첫 SA가 본인이면 자기 신청을 자기가 승인할 수 있었다).
     */
    Optional<User> findFirstByRoleAndIsActiveTrueAndHireDateIsNotNullAndIdNotOrderByIdAsc(
            Role role, Long excludeId);

    /** 퇴직자 목록 (GET /api/users/retired, SA) */
    Page<User> findByIsActiveFalse(Pageable pageable);

    /**
     * 서브 승인자 후보 — 재직 중 TEAM_LEADER·SYSTEM_ADMIN, 본인 제외 (GET /api/users/approvers).
     * 온보딩 미완료자는 지정돼도 결재를 못 하므로 후보에서 뺀다 (리뷰 I-5b).
     */
    List<User> findByRoleInAndIsActiveTrueAndHireDateIsNotNullAndIdNot(List<Role> roles, Long excludeId);

    /** 내 부서 팀원 목록 — 재직 중만, 이름순 (GET /api/users/team-members) */
    List<User> findByDepartmentAndIsActiveTrueOrderByNameAsc(Department department);

    /** 전체 목록 검색 — keyword(이름·이메일)·role 필터, 퇴직자 제외 (GET /api/users, SA) */
    @Query("select u from User u where u.isActive = true "
            + "and (:role is null or u.role = :role) "
            + "and (:keyword is null or u.name like concat('%', :keyword, '%') or u.email like concat('%', :keyword, '%'))")
    Page<User> search(@Param("role") Role role, @Param("keyword") String keyword, Pageable pageable);
}
