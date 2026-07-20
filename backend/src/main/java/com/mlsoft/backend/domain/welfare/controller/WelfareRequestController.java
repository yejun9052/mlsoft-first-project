package com.mlsoft.backend.domain.welfare.controller;

import com.mlsoft.backend.domain.welfare.dto.WelfareApprovalRequest;
import com.mlsoft.backend.domain.welfare.dto.WelfareCreateRequest;
import com.mlsoft.backend.domain.welfare.dto.WelfareResponse;
import com.mlsoft.backend.domain.welfare.service.WelfareService;
import com.mlsoft.backend.global.response.CommonResponse;
import com.mlsoft.backend.global.response.ResponseMessage;
import com.mlsoft.backend.security.AuthUser;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 복리후생 신청 API (docs/03 복리후생 섹션).
 * - 본인 식별은 Authentication(AuthUser)에서 추출 — 요청 body의 사용자 ID 신뢰 금지 (docs/04)
 * - 온보딩/퇴직/권한 신선도는 OnboardingCheckInterceptor가 /api/** 전역 가드
 * - 소유/승인자 식별 검증은 서비스 계층에서 (@PreAuthorize는 역할 게이트만)
 */
@RestController
@RequestMapping("/api/welfare-requests")
@RequiredArgsConstructor
public class WelfareRequestController {

    private final WelfareService welfareService;

    /** 복리후생 신청 */
    @PostMapping
    public ResponseEntity<CommonResponse<WelfareResponse>> apply(
            @AuthenticationPrincipal AuthUser authUser,
            @Valid @RequestBody WelfareCreateRequest request
    ) {
        WelfareResponse response = welfareService.apply(authUser.id(), request);
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(CommonResponse.success(ResponseMessage.WELFARE_CREATED, response));
    }

    /** 내 신청 내역 (페이징) */
    @GetMapping("/me")
    public ResponseEntity<CommonResponse<Page<WelfareResponse>>> getMyRequests(
            @AuthenticationPrincipal AuthUser authUser,
            Pageable pageable
    ) {
        Page<WelfareResponse> response = welfareService.getMyRequests(authUser.id(), pageable);
        return ResponseEntity.ok(CommonResponse.success(ResponseMessage.WELFARE_FETCHED, response));
    }

    /** 내가 승인자인 대기 목록 */
    @GetMapping("/pending")
    @PreAuthorize("hasAnyRole('TEAM_LEADER','SYSTEM_ADMIN')")
    public ResponseEntity<CommonResponse<Page<WelfareResponse>>> getPending(
            @AuthenticationPrincipal AuthUser authUser,
            Pageable pageable
    ) {
        Page<WelfareResponse> response = welfareService.getPending(authUser.id(), pageable);
        return ResponseEntity.ok(CommonResponse.success(ResponseMessage.WELFARE_FETCHED, response));
    }

    /** 전체 신청 목록 (페이징) */
    @GetMapping
    @PreAuthorize("hasRole('SYSTEM_ADMIN')")
    public ResponseEntity<CommonResponse<Page<WelfareResponse>>> getAll(Pageable pageable) {
        Page<WelfareResponse> response = welfareService.getAllForAdmin(pageable);
        return ResponseEntity.ok(CommonResponse.success(ResponseMessage.WELFARE_FETCHED, response));
    }

    /** 승인/반려 — 조건부 갱신 (승인자만), 승인 시 bonus_days 가산 */
    @PostMapping("/{id}/approval")
    @PreAuthorize("hasAnyRole('TEAM_LEADER','SYSTEM_ADMIN')")
    public ResponseEntity<CommonResponse<Void>> processApproval(
            @AuthenticationPrincipal AuthUser authUser,
            @PathVariable Long id,
            @Valid @RequestBody WelfareApprovalRequest request
    ) {
        welfareService.processApproval(id, authUser.id(), request);
        String message = Boolean.TRUE.equals(request.approved())
                ? ResponseMessage.WELFARE_APPROVED : ResponseMessage.WELFARE_REJECTED;
        return ResponseEntity.ok(CommonResponse.success(message));
    }

    /** 취소 — PENDING만 (본인) */
    @PostMapping("/{id}/cancel")
    public ResponseEntity<CommonResponse<Void>> cancel(
            @AuthenticationPrincipal AuthUser authUser,
            @PathVariable Long id
    ) {
        welfareService.cancel(id, authUser.id());
        return ResponseEntity.ok(CommonResponse.success(ResponseMessage.WELFARE_CANCELLED));
    }
}
