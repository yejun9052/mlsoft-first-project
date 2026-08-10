package com.mlsoft.backend.domain.leave.repository;

import com.mlsoft.backend.domain.leave.entity.LeaveResetHistory;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;

/**
 * 기산일 리셋 이력 저장소.
 *
 * <p>스케줄러가 붙으면서 매년 사원당 1행씩 쌓인다. 관리자 목록이 행마다 사원 이름을 읽으므로
 * {@code @EntityGraph}로 함께 적재한다 (리뷰 D-2) — 없으면 페이지 크기만큼 추가 쿼리가 붙는다.
 */
public interface LeaveResetHistoryRepository extends JpaRepository<LeaveResetHistory, Long> {

    /** 리셋 이력 목록 (GET /api/admin/reset-histories, SA) — 리셋일 최신순은 컨트롤러의 PageableDefault */
    @Override
    @EntityGraph(attributePaths = {"user"})
    Page<LeaveResetHistory> findAll(Pageable pageable);
}
