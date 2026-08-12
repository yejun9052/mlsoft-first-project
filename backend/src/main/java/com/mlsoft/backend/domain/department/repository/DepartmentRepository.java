package com.mlsoft.backend.domain.department.repository;

import com.mlsoft.backend.domain.department.entity.Department;
import com.mlsoft.backend.domain.user.entity.User;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

/**
 * 부서 저장소.
 *
 * <p>목록 조회는 {@code @EntityGraph}로 팀장을 함께 적재한다 — {@code DepartmentResponse}가
 * 행마다 팀장 이름을 읽으므로 LAZY로 두면 부서 수만큼 추가 쿼리가 붙었다 (리뷰 D-2).
 * 부서는 행이 수십 개라 아픈 정도는 작지만, 화면이 항상 전체를 받아 가므로 상시 발생한다.
 */
public interface DepartmentRepository extends JpaRepository<Department, Long> {

    /** 부서명 존재 여부 (초기 데이터 스킵 판별) */
    boolean existsByName(String name);

    /** 부서명으로 조회 (신규 가입 시 미배정 부서 배속) */
    Optional<Department> findByName(String name);

    /** 활성 부서 전체 — 플랫 목록/트리 조회 기반 (GET /api/departments, /tree) */
    @EntityGraph(attributePaths = {"leader"})
    List<Department> findByActiveTrueOrderByIdAsc();

    /** 활성 부서 단건 조회 — 수정·비활성화·부서배정 시 대상 조회 */
    Optional<Department> findByIdAndActiveTrue(Long id);

    /**
     * 하위 부서 보유 여부 — 2단계 계층 강제의 나머지 반쪽 (1차 테스트 F).
     * <p>비활성 자식도 센다: 되살리면 그대로 3단계가 되기 때문이다.
     */
    boolean existsByParentId(Long parentId);

    /** 이 사람이 팀장인 부서 전부 — 퇴직 이관 시 leader 해제 대상 (active 필터 없음, docs/01 2-9) */
    List<Department> findByLeader(User leader);
}
