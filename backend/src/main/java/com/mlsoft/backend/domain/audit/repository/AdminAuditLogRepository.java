package com.mlsoft.backend.domain.audit.repository;

import com.mlsoft.backend.domain.audit.entity.AdminAction;
import com.mlsoft.backend.domain.audit.entity.AdminAuditLog;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

/**
 * 관리자 조작 감사 로그 저장소 (리뷰 S-3).
 *
 * <p>목록이 행마다 처리자·대상 사원 이름을 노출하므로 {@code @EntityGraph}로 함께 적재한다 —
 * LAZY로 두면 페이지 크기만큼 추가 쿼리가 붙는다 (리뷰 D-2).
 */
public interface AdminAuditLogRepository extends JpaRepository<AdminAuditLog, Long> {

    /**
     * 감사 로그 검색 — action·대상 사원 선택 필터.
     * 필터가 2개라 조합별 파생 메서드(4개) 대신 {@code @Query}로 묶었다
     * ({@code UserRepository.search}와 같은 방식).
     */
    @EntityGraph(attributePaths = {"actor", "targetUser"})
    @Query("select l from AdminAuditLog l "
            + "where (:action is null or l.action = :action) "
            + "and (:targetUserId is null or l.targetUser.id = :targetUserId)")
    Page<AdminAuditLog> search(@Param("action") AdminAction action,
                               @Param("targetUserId") Long targetUserId,
                               Pageable pageable);
}
