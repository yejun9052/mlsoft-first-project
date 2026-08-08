package com.mlsoft.backend.domain.auth.controller;

import com.mlsoft.backend.domain.auth.dto.OnboardingApprovalResponse;
import com.mlsoft.backend.domain.auth.service.OnboardingApprovalService;
import com.mlsoft.backend.global.response.CommonResponse;
import com.mlsoft.backend.global.response.ResponseMessage;
import com.mlsoft.backend.security.AuthUser;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 온보딩 승인 관리자 API (리뷰 S-1).
 *
 * <p>자동 승인 기간을 벗어난 입사일을 신고한 사원이 여기 쌓인다. 승인 전까지 그 계정은
 * 연차가 0이고 {@code /api/auth/*} 밖으로 나가지 못한다.
 */
@RestController
@RequestMapping("/api/admin/onboardings")
@RequiredArgsConstructor
public class OnboardingAdminController {

    private final OnboardingApprovalService onboardingApprovalService;

    /** 승인 대기 목록 — 오래 기다린 순 */
    @GetMapping
    @PreAuthorize("hasRole('SYSTEM_ADMIN')")
    public ResponseEntity<CommonResponse<Page<OnboardingApprovalResponse>>> getPending(
            @PageableDefault(size = 20) Pageable pageable
    ) {
        Page<OnboardingApprovalResponse> response = onboardingApprovalService.getPending(pageable);
        return ResponseEntity.ok(CommonResponse.success(ResponseMessage.ONBOARDING_PENDING_FETCHED, response));
    }

    /** 승인 — 신고한 입사일을 확정하고 그 시점에 연차를 부여한다 */
    @PostMapping("/{userId}/approval")
    @PreAuthorize("hasRole('SYSTEM_ADMIN')")
    public ResponseEntity<CommonResponse<Void>> approve(
            @AuthenticationPrincipal AuthUser authUser,
            @PathVariable Long userId
    ) {
        onboardingApprovalService.approve(userId, authUser.id());
        return ResponseEntity.ok(CommonResponse.success(ResponseMessage.ONBOARDING_APPROVED, null));
    }

    /** 반려 — 입력값을 지우고 사원이 다시 낼 수 있게 되돌린다 */
    @PostMapping("/{userId}/rejection")
    @PreAuthorize("hasRole('SYSTEM_ADMIN')")
    public ResponseEntity<CommonResponse<Void>> reject(
            @AuthenticationPrincipal AuthUser authUser,
            @PathVariable Long userId
    ) {
        onboardingApprovalService.reject(userId, authUser.id());
        return ResponseEntity.ok(CommonResponse.success(ResponseMessage.ONBOARDING_REJECTED, null));
    }
}
