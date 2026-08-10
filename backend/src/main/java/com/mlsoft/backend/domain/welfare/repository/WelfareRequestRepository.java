package com.mlsoft.backend.domain.welfare.repository;

import com.mlsoft.backend.domain.common.RequestStatus;
import com.mlsoft.backend.domain.user.entity.User;
import com.mlsoft.backend.domain.welfare.entity.WelfareRequest;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;

/**
 * 복리후생 신청 저장소.
 * - 승인자는 {@code User} 연관이다 (리뷰 D-5 — 예전에는 FK 없는 raw {@code Long}이었다).
 *   연차 저장소와 같은 방식으로 엔티티를 넘겨 조회한다.
 * - 상태 전이(승인·반려·취소)는 {@link #updateStatusIfCurrent}의 조건부 갱신(WHERE status = 기대상태)으로
 *   처리해 primary/sub 동시 처리를 1행만 claim 시킨다 (연차와 동일한 검증 R-5 패턴).
 *
 * <h3>N+1 대책 (리뷰 D-2)</h3>
 * {@code WelfareResponse}가 행마다 신청자·부서·정책을 읽는데 대책이 하나도 없었다 —
 * 관리자 목록(페이지 20)에서 추가 쿼리가 최대 60회였다. 목록 3종에 to-one 그래프를 붙였다.
 * 승인자는 {@code getId()}만 읽으므로(프록시에 FK가 있다) 그래프에 넣지 않는다.
 */
public interface WelfareRequestRepository extends JpaRepository<WelfareRequest, Long> {

    /** 전체 신청 목록 (GET /api/welfare-requests, SA) */
    @Override
    @EntityGraph(attributePaths = {"user", "user.department", "policy"})
    Page<WelfareRequest> findAll(Pageable pageable);

    /** 내 신청 내역 (GET /api/welfare-requests/me) */
    @EntityGraph(attributePaths = {"user", "user.department", "policy"})
    Page<WelfareRequest> findByUser(User user, Pageable pageable);

    /**
     * 내가 승인자(primary 또는 sub)인 대기 목록 (GET /api/welfare-requests/pending).
     *
     * <p>{@code subApprover.id}는 nullable 연관이지만 <b>조인을 만들지 않는다</b> — to-one 연관의
     * 식별자만 참조하면 Hibernate가 FK 컬럼을 그대로 쓴다. 조인으로 바꾸면(INNER) 서브 승인자가
     * 없는 건이 목록에서 빠진다. 이 성질은 연차 쪽 같은 쿼리에서 통합 테스트로 고정해 뒀다
     * ({@code LeaveActionHistoryRepositoryIntegrationTest.findByApprover_primary}).
     */
    @EntityGraph(attributePaths = {"user", "user.department", "policy"})
    @Query("select wr from WelfareRequest wr "
            + "where (wr.primaryApprover.id = :approverId or wr.subApprover.id = :approverId) "
            + "and wr.status = :status")
    Page<WelfareRequest> findPendingForApprover(@Param("approverId") Long approverId,
                                                @Param("status") RequestStatus status,
                                                Pageable pageable);

    /**
     * 상태 조건부 전이 (승인·반려·취소) — 기대 상태일 때만 1행 갱신.
     * 반환 0이면 이미 다른 승인자가 처리(또는 본인이 취소)한 것 → 서비스가 ALREADY_PROCESSED 로 변환.
     * 벌크 갱신이므로 실행 전 flush, 실행 후 영속성 컨텍스트를 정리하고 필요한 엔티티를 재조회한다.
     */
    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query("update WelfareRequest wr set wr.status = :next "
            + "where wr.id = :id and wr.status = :expected")
    int updateStatusIfCurrent(@Param("id") Long id,
                              @Param("expected") RequestStatus expected,
                              @Param("next") RequestStatus next);

    /**
     * 이 사람이 primary 승인자인 대기 건 — 퇴직 이관 대상 조회 (docs/01 2-9).
     * WelfareRequest는 CANCEL_PENDING 상태가 없고 cancel()도 PENDING에서만 가능하므로 PENDING만 대상이다.
     */
    List<WelfareRequest> findByPrimaryApproverAndStatus(User primaryApprover, RequestStatus status);

    /** 이 사람이 sub 승인자인 대기 건 — 퇴직 이관 대상 조회 (PENDING만, docs/01 2-9) */
    List<WelfareRequest> findBySubApproverAndStatus(User subApprover, RequestStatus status);
}
