package com.mlsoft.backend.domain.policy.service;

import com.mlsoft.backend.domain.policy.dto.LeavePolicyResponse;
import com.mlsoft.backend.domain.policy.dto.LeavePolicyUpdateRequest;
import com.mlsoft.backend.domain.policy.entity.LeavePolicy;
import com.mlsoft.backend.domain.policy.repository.LeavePolicyRepository;
import com.mlsoft.backend.global.exception.BusinessException;
import com.mlsoft.backend.global.exception.ErrorCode;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

/**
 * 근속년수별 연차 정책 관리자 CRUD (GET/PATCH /api/admin/leave-policies, SA — docs/03 시스템 설정).
 * 근속년수별 연차 산정 계산 로직(LeavePolicyService)과는 책임을 분리한다 — 이 서비스는
 * 관리자 화면의 조회·일수 수정만 담당하고, 스케줄러·온보딩이 쓰는 계산 로직은 건드리지 않는다.
 */
@Service
@RequiredArgsConstructor
public class LeavePolicyAdminService {

    private final LeavePolicyRepository leavePolicyRepository;

    /** 정책 목록 (GET /api/admin/leave-policies, SA) — 근속년수 오름차순 21건 */
    @Transactional(readOnly = true)
    public List<LeavePolicyResponse> getAll() {
        return leavePolicyRepository.findAllByOrderByYearsOfServiceAsc().stream()
                .map(LeavePolicyResponse::of)
                .toList();
    }

    /** 정책 일수 수정 (PATCH /api/admin/leave-policies/{id}, SA) */
    @Transactional
    public LeavePolicyResponse update(Long id, LeavePolicyUpdateRequest request) {
        LeavePolicy policy = leavePolicyRepository.findById(id)
                .orElseThrow(() -> new BusinessException(ErrorCode.LEAVE_POLICY_NOT_FOUND));
        policy.updateAnnualLeaveDays(request.annualLeaveDays(), request.description());
        return LeavePolicyResponse.of(policy);
    }
}
