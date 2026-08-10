package com.mlsoft.backend.domain.leave.repository;

import com.mlsoft.backend.domain.common.RequestAction;
import com.mlsoft.backend.domain.leave.entity.LeaveActionHistory;
import com.mlsoft.backend.domain.leave.entity.LeaveRequest;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;

/**
 * 연차 처리 이력 저장소.
 *
 * <p>로그 목록(GET /api/leave-histories)은 한 행마다 처리자·신청자·신청 정보를 함께 노출하므로
 * LAZY 연관을 그대로 두면 페이지 크기만큼 추가 쿼리가 발생한다. 목록 조회 메서드는 전부
 * {@code @EntityGraph}로 필요한 연관을 함께 적재한다 (리뷰 D-2 N+1).
 */
public interface LeaveActionHistoryRepository extends JpaRepository<LeaveActionHistory, Long> {

    /**
     * 해당 신청의 처리 이력 — 발생 순서(오름차순) (GET /api/leaves/{id}/histories).
     * 페이징 목록과 달리 여기엔 그래프가 없어 {@code actor} 이름을 행마다 조회했다 (리뷰 D-2).
     */
    @EntityGraph(attributePaths = {"actor"})
    List<LeaveActionHistory> findByLeaveRequestOrderByCreatedAtAsc(LeaveRequest leaveRequest);

    /** 전체 처리 로그 (GET /api/leave-histories, SA) */
    @Override
    @EntityGraph(attributePaths = {"actor", "user", "user.department", "leaveRequest"})
    Page<LeaveActionHistory> findAll(Pageable pageable);

    /** 전체 처리 로그 — action 필터 (GET /api/leave-histories?action=, SA) */
    @EntityGraph(attributePaths = {"actor", "user", "user.department", "leaveRequest"})
    Page<LeaveActionHistory> findByAction(RequestAction action, Pageable pageable);

    /**
     * 내가 결재자인 신청의 이력 (GET /api/leave-histories/my-approvals, TL·SA — 리뷰 S-6).
     *
     * <p><b>신청자 소속 부서가 아니라 승인자 지정 기준이다.</b> 부서로 묶으면 실제 결재 권한과
     * 화면이 어긋난다 — 부서를 옮긴 사원의 과거 이력이 새 팀장에게 보이고, 퇴직 이관으로
     * 결재를 넘겨받은 건은 정작 안 보인다.
     *
     * <p>action이 {@code null}이면 조건이 무력화된다 — 필터 조합별 파생 메서드를 늘리지 않는다.
     */
    @EntityGraph(attributePaths = {"actor", "user", "user.department", "leaveRequest"})
    @Query("select h from LeaveActionHistory h "
            + "where (h.leaveRequest.primaryApprover.id = :approverId "
            + "or h.leaveRequest.subApprover.id = :approverId) "
            + "and (:action is null or h.action = :action)")
    Page<LeaveActionHistory> findByApprover(@Param("approverId") Long approverId,
                                            @Param("action") RequestAction action,
                                            Pageable pageable);

    /** 내가 처리한 로그 (GET /api/leave-histories/my-actions) — actor 기준이라 부서 스코프와 무관하다 */
    @EntityGraph(attributePaths = {"actor", "user", "user.department", "leaveRequest"})
    Page<LeaveActionHistory> findByActorId(Long actorId, Pageable pageable);

    /** 내가 처리한 로그 — action 필터 (GET /api/leave-histories/my-actions?action=) */
    @EntityGraph(attributePaths = {"actor", "user", "user.department", "leaveRequest"})
    Page<LeaveActionHistory> findByActorIdAndAction(Long actorId, RequestAction action, Pageable pageable);
}
