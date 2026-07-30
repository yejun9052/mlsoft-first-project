package com.mlsoft.backend.domain.welfare.repository;

import com.mlsoft.backend.domain.common.RequestAction;
import com.mlsoft.backend.domain.welfare.entity.WelfareActionHistory;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;

/**
 * 복리후생 처리 이력 저장소.
 *
 * <p>목록 조회는 LeaveActionHistoryRepository와 동일하게 {@code @EntityGraph}로 LAZY 연관을
 * 함께 적재한다 (리뷰 D-2 N+1).
 */
public interface WelfareActionHistoryRepository extends JpaRepository<WelfareActionHistory, Long> {

    /** 전체 처리 로그 (GET /api/welfare-histories, SA) */
    @Override
    @EntityGraph(attributePaths = {"actor", "user", "user.department", "welfareRequest"})
    Page<WelfareActionHistory> findAll(Pageable pageable);

    /** 전체 처리 로그 — action 필터 (GET /api/welfare-histories?action=, SA) */
    @EntityGraph(attributePaths = {"actor", "user", "user.department", "welfareRequest"})
    Page<WelfareActionHistory> findByAction(RequestAction action, Pageable pageable);

    /** 팀 처리 로그 — 신청자 소속 부서 기준 (GET /api/welfare-histories/my-team, TL) */
    @EntityGraph(attributePaths = {"actor", "user", "user.department", "welfareRequest"})
    Page<WelfareActionHistory> findByUserDepartmentId(Long departmentId, Pageable pageable);

    /** 팀 처리 로그 — action 필터 (GET /api/welfare-histories/my-team?action=, TL) */
    @EntityGraph(attributePaths = {"actor", "user", "user.department", "welfareRequest"})
    Page<WelfareActionHistory> findByUserDepartmentIdAndAction(Long departmentId, RequestAction action,
                                                               Pageable pageable);
}
