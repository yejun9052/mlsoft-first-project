package com.mlsoft.backend.domain.policy.controller;

import com.mlsoft.backend.domain.policy.dto.LeavePolicyResponse;
import com.mlsoft.backend.domain.policy.dto.LeavePolicyUpdateRequest;
import com.mlsoft.backend.domain.policy.service.LeavePolicyAdminService;
import com.mlsoft.backend.global.response.CommonResponse;
import com.mlsoft.backend.global.response.ResponseMessage;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * 근속년수별 연차 정책 관리자 API (docs/03 시스템 설정).
 * 조회·수정 모두 SYSTEM_ADMIN 전용 — 근속년수별 부여 일수는 회사 정책이라 노출을 관리자 화면으로 한정한다.
 */
@RestController
@RequestMapping("/api/admin/leave-policies")
@RequiredArgsConstructor
public class LeavePolicyController {

    private final LeavePolicyAdminService leavePolicyAdminService;

    /** 정책 목록 (근속년수 오름차순) */
    @GetMapping
    @PreAuthorize("hasRole('SYSTEM_ADMIN')")
    public ResponseEntity<CommonResponse<List<LeavePolicyResponse>>> getAll() {
        List<LeavePolicyResponse> response = leavePolicyAdminService.getAll();
        return ResponseEntity.ok(CommonResponse.success(ResponseMessage.LEAVE_POLICY_FETCHED, response));
    }

    /** 정책 일수 수정 */
    @PatchMapping("/{id}")
    @PreAuthorize("hasRole('SYSTEM_ADMIN')")
    public ResponseEntity<CommonResponse<LeavePolicyResponse>> update(
            @PathVariable Long id,
            @Valid @RequestBody LeavePolicyUpdateRequest request
    ) {
        LeavePolicyResponse response = leavePolicyAdminService.update(id, request);
        return ResponseEntity.ok(CommonResponse.success(ResponseMessage.LEAVE_POLICY_UPDATED, response));
    }
}
