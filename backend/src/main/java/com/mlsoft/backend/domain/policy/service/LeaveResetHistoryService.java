package com.mlsoft.backend.domain.policy.service;

import com.mlsoft.backend.domain.leave.repository.LeaveResetHistoryRepository;
import com.mlsoft.backend.domain.policy.dto.LeaveResetHistoryResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 기산일 리셋·소멸 이력 조회 (GET /api/admin/reset-histories, SA — docs/03 시스템 설정, docs/02 3-11(b)).
 * 이력은 스케줄러가 기록만 하고(leave/service), 여기서는 관리자 화면용 조회만 담당한다.
 */
@Service
@RequiredArgsConstructor
public class LeaveResetHistoryService {

    private final LeaveResetHistoryRepository leaveResetHistoryRepository;

    /**
     * 리셋 이력 목록 (페이징, 리셋일 최신순 기본 정렬).
     * user는 LAZY 연관이므로 트랜잭션 내에서 응답 DTO로 변환한다 (UserResponse·DepartmentResponse와 동일 원칙).
     */
    @Transactional(readOnly = true)
    public Page<LeaveResetHistoryResponse> getResetHistories(Pageable pageable) {
        return leaveResetHistoryRepository.findAll(pageable).map(LeaveResetHistoryResponse::of);
    }
}
