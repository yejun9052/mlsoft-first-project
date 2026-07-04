package com.mlsoft.backend.domain.leave.repository;

import com.mlsoft.backend.domain.leave.entity.LeaveRequest;
import org.springframework.data.jpa.repository.JpaRepository;

/**
 * 연차 신청 저장소 — 조회 조건(승인자별 대기 목록, 캘린더 등)은 기능 구현 시 추가.
 */
public interface LeaveRequestRepository extends JpaRepository<LeaveRequest, Long> {
}
