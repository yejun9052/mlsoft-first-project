package com.mlsoft.backend.domain.policy.controller;

import com.mlsoft.backend.domain.policy.dto.LeavePolicyConfigResponse;
import com.mlsoft.backend.domain.policy.dto.LeavePolicyConfigUpdateRequest;
import com.mlsoft.backend.domain.policy.service.LeavePolicyConfigService;
import com.mlsoft.backend.global.response.CommonResponse;
import com.mlsoft.backend.global.response.ResponseMessage;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * 연차 시스템 설정 관리자 API (docs/03 시스템 설정, docs/02 3-11).
 * 조회·수정 모두 SYSTEM_ADMIN 전용. name 기준 단건 갱신 — 새 키 생성은 지원하지 않는다.
 */
@RestController
@RequestMapping("/api/admin/configs")
@RequiredArgsConstructor
public class LeavePolicyConfigController {

    private final LeavePolicyConfigService leavePolicyConfigService;

    /** 설정 전체 조회 */
    @GetMapping
    @PreAuthorize("hasRole('SYSTEM_ADMIN')")
    public ResponseEntity<CommonResponse<List<LeavePolicyConfigResponse>>> getAll() {
        List<LeavePolicyConfigResponse> response = leavePolicyConfigService.getAll();
        return ResponseEntity.ok(CommonResponse.success(ResponseMessage.LEAVE_POLICY_CONFIG_FETCHED, response));
    }

    /** 설정 변경 {name, value} */
    @PutMapping
    @PreAuthorize("hasRole('SYSTEM_ADMIN')")
    public ResponseEntity<CommonResponse<LeavePolicyConfigResponse>> update(
            @Valid @RequestBody LeavePolicyConfigUpdateRequest request
    ) {
        LeavePolicyConfigResponse response = leavePolicyConfigService.update(request);
        return ResponseEntity.ok(CommonResponse.success(ResponseMessage.LEAVE_POLICY_CONFIG_UPDATED, response));
    }
}
