package com.mlsoft.backend.domain.welfare.repository;

import com.mlsoft.backend.domain.common.RequestAction;
import com.mlsoft.backend.domain.welfare.entity.WelfareActionHistory;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

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

    /**
     * 내가 결재자인 신청의 이력 (GET /api/welfare-histories/my-approvals, TL·SA — 리뷰 S-6).
     * 연차와 같은 기준 — 신청자 부서가 아니라 승인자 지정으로 좁힌다.
     */
    @EntityGraph(attributePaths = {"actor", "user", "user.department", "welfareRequest"})
    @Query("select h from WelfareActionHistory h "
            + "where (h.welfareRequest.primaryApproverId = :approverId "
            + "or h.welfareRequest.subApproverId = :approverId) "
            + "and (:action is null or h.action = :action)")
    Page<WelfareActionHistory> findByApprover(@Param("approverId") Long approverId,
                                              @Param("action") RequestAction action,
                                              Pageable pageable);

    /** 내가 처리한 로그 (GET /api/welfare-histories/my-actions) — actor 기준 */
    @EntityGraph(attributePaths = {"actor", "user", "user.department", "welfareRequest"})
    Page<WelfareActionHistory> findByActorId(Long actorId, Pageable pageable);

    /** 내가 처리한 로그 — action 필터 */
    @EntityGraph(attributePaths = {"actor", "user", "user.department", "welfareRequest"})
    Page<WelfareActionHistory> findByActorIdAndAction(Long actorId, RequestAction action, Pageable pageable);
}
