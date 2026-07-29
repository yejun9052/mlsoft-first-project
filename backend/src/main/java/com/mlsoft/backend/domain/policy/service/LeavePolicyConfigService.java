package com.mlsoft.backend.domain.policy.service;

import com.mlsoft.backend.domain.policy.dto.LeavePolicyConfigResponse;
import com.mlsoft.backend.domain.policy.dto.LeavePolicyConfigUpdateRequest;
import com.mlsoft.backend.domain.policy.entity.LeavePolicyConfig;
import com.mlsoft.backend.domain.policy.repository.LeavePolicyConfigRepository;
import com.mlsoft.backend.global.exception.BusinessException;
import com.mlsoft.backend.global.exception.ErrorCode;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

/**
 * 연차 시스템 설정 관리 (GET/PUT /api/admin/configs, SA — docs/03 시스템 설정, docs/02 3-11).
 * 키(name) 기준으로 값만 갱신 — 세 키(advance_leave_enabled/reminder_list_days/reminder_auto_cycle)는
 * DataInitializer가 시드하며, 이 서비스는 새 키를 생성하지 않는다(존재하지 않으면 404).
 */
@Service
@RequiredArgsConstructor
public class LeavePolicyConfigService {

    private final LeavePolicyConfigRepository leavePolicyConfigRepository;

    /** 설정 목록 (GET /api/admin/configs, SA) */
    @Transactional(readOnly = true)
    public List<LeavePolicyConfigResponse> getAll() {
        return leavePolicyConfigRepository.findAllByOrderByIdAsc().stream()
                .map(LeavePolicyConfigResponse::of)
                .toList();
    }

    /** 설정 값 변경 (PUT /api/admin/configs, SA) — name으로 조회 후 value 갱신 */
    @Transactional
    public LeavePolicyConfigResponse update(LeavePolicyConfigUpdateRequest request) {
        LeavePolicyConfig config = leavePolicyConfigRepository.findByName(request.name())
                .orElseThrow(() -> new BusinessException(ErrorCode.LEAVE_POLICY_CONFIG_NOT_FOUND));
        config.updateValue(request.value().trim());
        return LeavePolicyConfigResponse.of(config);
    }
}
