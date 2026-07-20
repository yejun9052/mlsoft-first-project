package com.mlsoft.backend.domain.welfare.repository;

import com.mlsoft.backend.domain.common.RequestStatus;
import com.mlsoft.backend.domain.user.entity.User;
import com.mlsoft.backend.domain.welfare.entity.WelfareRequest;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

/**
 * 복리후생 신청 저장소.
 * - 승인자는 FK 연관 없이 primary_approver_id/sub_approver_id로 보관하므로(엔티티 설계) Long 비교로 조회한다.
 * - 상태 전이(승인·반려·취소)는 {@link #updateStatusIfCurrent}의 조건부 갱신(WHERE status = 기대상태)으로
 *   처리해 primary/sub 동시 처리를 1행만 claim 시킨다 (연차와 동일한 검증 R-5 패턴).
 */
public interface WelfareRequestRepository extends JpaRepository<WelfareRequest, Long> {

    /** 내 신청 내역 (GET /api/welfare-requests/me) */
    Page<WelfareRequest> findByUser(User user, Pageable pageable);

    /** 내가 승인자(primary 또는 sub)인 대기 목록 (GET /api/welfare-requests/pending) */
    @Query("select wr from WelfareRequest wr "
            + "where (wr.primaryApproverId = :approverId or wr.subApproverId = :approverId) "
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
}
