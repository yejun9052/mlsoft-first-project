package com.mlsoft.backend.domain.user.repository;

import com.mlsoft.backend.domain.department.entity.Department;
import com.mlsoft.backend.domain.user.entity.OnboardingStatus;
import com.mlsoft.backend.domain.user.entity.Role;
import com.mlsoft.backend.domain.user.entity.User;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDate;
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

    /** 재직 중 특정 권한 사용자 전체 — 이메일 알림 수신자 결정 (검증 R-4) */
    List<User> findByRoleAndIsActiveTrue(Role role);

    /**
     * primary 승인자 SYSTEM_ADMIN fallback (검증 Y-3, 리뷰 I-5b·I-5c).
     * <p>온보딩 미완료자는 인터셉터가 막아 결재를 못 하므로 제외하고, 신청자 본인도 제외한다
     * (셀프 결재 방지 — 첫 SA가 본인이면 자기 신청을 자기가 승인할 수 있었다).
     * <p>판별 기준은 {@code hire_date}가 아니라 {@code onboarding_status}다 — 승인 대기 중에도
     * 입사일은 채워져 있어서 그걸로 거르면 결재 못 하는 사람이 승인자로 지정된다 (리뷰 S-1).
     */
    Optional<User> findFirstByRoleAndIsActiveTrueAndOnboardingStatusAndIdNotOrderByIdAsc(
            Role role, OnboardingStatus onboardingStatus, Long excludeId);

    /**
     * 재직 중 관리자 전체를 <b>행 잠금과 함께</b> 조회 (리뷰 S-2 — 동시성 결함 대응).
     *
     * <p>단순 {@code count}로는 막을 수 없다. 관리자 A와 B를 서로 다른 요청에서 동시에 강등하면
     * 각 트랜잭션이 <b>상대를 세어</b> 검증을 통과하고, 서로 다른 {@code User} 행을 갱신하므로
     * {@code @Version} 낙관적 락도 충돌하지 않는다 → 둘 다 커밋돼 관리자가 0명이 된다
     * (쓰기 스큐). 그 뒤로는 권한 부여 엔드포인트가 SA 전용이라 DB 직접 UPDATE 외에 복구 수단이 없다.
     *
     * <p><b>대상 본인도 포함해</b> 잠근다. 대상을 제외하면 두 트랜잭션이 서로 다른 행을 잠가
     * 직렬화되지 않는다 — A를 강등하는 쪽은 B만, B를 강등하는 쪽은 A만 잠그기 때문이다.
     * 전체를 잠그면 두 요청이 같은 행 집합을 노려 뒤에 온 쪽이 기다렸다가 <b>갱신된 상태를 다시 읽는다</b>
     * (잠금 읽기는 스냅샷이 아니라 최신 커밋을 본다).
     *
     * <p>{@code order by u.id}는 교착 방지다 — 잠금 순서가 요청마다 같아야 한다.
     * 온보딩 완료 여부는 잠근 뒤 자바에서 걸러 조건을 한곳에 모은다.
     */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select u from User u where u.role = :role and u.isActive = true order by u.id")
    List<User> findActiveByRoleForUpdate(@Param("role") Role role);

    /** 퇴직자 목록 (GET /api/users/retired, SA) */
    @EntityGraph(attributePaths = {"department"})
    Page<User> findByIsActiveFalse(Pageable pageable);

    /**
     * 서브 승인자 후보 — 재직 중 TEAM_LEADER·SYSTEM_ADMIN, 본인 제외 (GET /api/users/approvers).
     * 온보딩이 확정되지 않은 사원은 지정돼도 결재를 못 하므로 후보에서 뺀다 (리뷰 I-5b·S-1).
     */
    @EntityGraph(attributePaths = {"department"})
    List<User> findByRoleInAndIsActiveTrueAndOnboardingStatusAndIdNot(
            List<Role> roles, OnboardingStatus onboardingStatus, Long excludeId);

    /** 온보딩 승인 대기 목록 (GET /api/admin/onboardings, SA) — 오래 기다린 순 (리뷰 S-1) */
    @EntityGraph(attributePaths = {"department"})
    Page<User> findByOnboardingStatusOrderByUpdateAtAsc(OnboardingStatus onboardingStatus, Pageable pageable);

    /** 내 부서 팀원 목록 — 재직 중만, 이름순 (GET /api/users/team-members) */
    List<User> findByDepartmentAndIsActiveTrueOrderByNameAsc(Department department);

    // ── 스케줄러 대상 조회 (docs/09 §2) ─────────────────────────────────────
    // 공통 가드 2개가 모든 잡에 들어간다 — 퇴직자 제외(is_active), 온보딩 미완료 제외(hire_date not null, 검증 Y-2).
    // 대상만 id로 좁혀 받고 실제 처리는 유저 1명당 1트랜잭션으로 끊는다 (docs/09 §7).

    /**
     * 기산일이 지난 사원 id — {@code last_reset_date + 1년 <= 오늘} (검증 Y-1).
     * <p>"오늘이 입사일"인 사원이 걸리지 않도록 <b>같거나 이전</b>으로 비교한다.
     * 며칠 밀린 경우도 여기 걸리고, 몇 년치 소급은 서비스의 catch-up 루프가 처리한다 (docs/09 §6).
     */
    @Query("select u.id from User u where u.isActive = true and u.onboardingStatus = COMPLETED "
            + "and u.lastResetDate is not null and u.lastResetDate <= :resetDueOnOrBefore")
    List<Long> findIdsDueForAnnualReset(@Param("resetDueOnOrBefore") LocalDate resetDueOnOrBefore);

    /**
     * 월차 적립 대상 사원 id — 입사 1년 미만({@code hire_date > 오늘 − 1년}).
     * <p>적립 시점이 실제로 도래했는지(횟수·상한)는 날짜 계산이 필요해 서비스에서 판정한다.
     */
    @Query("select u.id from User u where u.isActive = true and u.onboardingStatus = COMPLETED "
            + "and u.hireDate > :hiredAfter")
    List<Long> findIdsUnderOneYear(@Param("hiredAfter") LocalDate hiredAfter);

    /**
     * 생일 반차 미지급자 id — 올해 아직 못 받은 사람만.
     * <p>생일이 지났는지·입사 전 생일인지는 월/일 비교라 서비스에서 판정한다(연도별 2/29 보정 포함).
     * 여기서 연도로 먼저 걸러 두면 매일 전 사원을 훑지 않는다.
     */
    @Query("select u.id from User u where u.isActive = true and u.onboardingStatus = COMPLETED "
            + "and u.birthDay is not null "
            + "and (u.lastBirthdayGrantYear is null or u.lastBirthdayGrantYear < :year)")
    List<Long> findIdsWithoutBirthdayLeave(@Param("year") int year);

    /**
     * 전체 목록 검색 — keyword(이름·이메일)·role 필터, 퇴직자 제외 (GET /api/users, SA).
     * {@code UserResponse}가 행마다 부서명을 읽으므로 함께 적재한다 (리뷰 D-2).
     */
    @EntityGraph(attributePaths = {"department"})
    @Query("select u from User u where u.isActive = true "
            + "and (:role is null or u.role = :role) "
            + "and (:keyword is null or u.name like concat('%', :keyword, '%') or u.email like concat('%', :keyword, '%'))")
    Page<User> search(@Param("role") Role role, @Param("keyword") String keyword, Pageable pageable);
}
