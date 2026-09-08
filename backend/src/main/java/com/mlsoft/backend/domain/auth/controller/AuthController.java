package com.mlsoft.backend.domain.auth.controller;

import com.mlsoft.backend.domain.auth.dto.OnboardingRequest;
import com.mlsoft.backend.domain.auth.dto.UserMeResponse;
import com.mlsoft.backend.domain.auth.service.AuthService;
import com.mlsoft.backend.global.response.CommonResponse;
import com.mlsoft.backend.global.response.ResponseMessage;
import com.mlsoft.backend.security.AuthUser;
import com.mlsoft.backend.security.TokenCookieFactory;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 인증 API (docs/03 인증 섹션).
 * 본인 식별은 Authentication(AuthUser)에서 추출 — 요청 body의 사용자 ID 신뢰 금지 (docs/04).
 */
@RestController
@RequestMapping("/api/auth")
@RequiredArgsConstructor
public class AuthController {

    private final AuthService authService;
    private final TokenCookieFactory tokenCookieFactory;

    /**
     * 내 정보 조회.
     * - onboarded=false면 프론트가 온보딩 페이지로 유도
     */
    @GetMapping("/me")
    public ResponseEntity<CommonResponse<UserMeResponse>> getMe(@AuthenticationPrincipal AuthUser authUser) {
        UserMeResponse response = authService.getMe(authUser.id());
        return ResponseEntity.ok(CommonResponse.success(ResponseMessage.USER_INFO_FETCHED, response));
    }

    /**
     * 최초 온보딩 — 생일·입사일 입력과 선택 직급, base_days는 정책 자동 계산.
     */
    @PostMapping("/onboarding")
    public ResponseEntity<CommonResponse<UserMeResponse>> completeOnboarding(
            @AuthenticationPrincipal AuthUser authUser,
            @Valid @RequestBody OnboardingRequest request
    ) {
        UserMeResponse response = authService.completeOnboarding(authUser.id(), request);
        return ResponseEntity.ok(CommonResponse.success(ResponseMessage.ONBOARDING_COMPLETED, response));
    }

    /**
     * 승인 대기 중 온보딩 수정 — 기존 요청 자원을 한 번 변경하므로 PATCH를 사용한다.
     */
    @PatchMapping("/onboarding")
    public ResponseEntity<CommonResponse<UserMeResponse>> reviseOnboarding(
            @AuthenticationPrincipal AuthUser authUser,
            @Valid @RequestBody OnboardingRequest request
    ) {
        UserMeResponse response = authService.reviseOnboarding(authUser.id(), request);
        return ResponseEntity.ok(CommonResponse.success(ResponseMessage.ONBOARDING_REVISED, response));
    }

    /**
     * 로그아웃 — JWT 쿠키 즉시 만료.
     */
    @PostMapping("/logout")
    public ResponseEntity<CommonResponse<Void>> logout() {
        return ResponseEntity.ok()
                .header(HttpHeaders.SET_COOKIE, tokenCookieFactory.expire().toString())
                .body(CommonResponse.success(ResponseMessage.LOGOUT_COMPLETED));
    }
}
