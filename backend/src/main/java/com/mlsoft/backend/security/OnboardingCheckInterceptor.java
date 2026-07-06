package com.mlsoft.backend.security;

import com.mlsoft.backend.domain.user.entity.User;
import com.mlsoft.backend.domain.user.repository.UserRepository;
import com.mlsoft.backend.global.exception.BusinessException;
import com.mlsoft.backend.global.exception.ErrorCode;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.HandlerInterceptor;

import java.util.List;

/**
 * 인증 유저 상태 검증 인터셉터 — /api/** 전체에 적용 (WebConfig 등록).
 * JWT는 발급 후 상태 변화를 반영하지 못하므로 매 요청 DB 기준으로 판별한다.
 * - ① 퇴직자(is_active=false): 모든 /api/** 경로 즉시 차단 (24h 토큰 잔존 창 봉쇄)
 * - ② 권한 신선도: DB role과 토큰 role이 다르면 SecurityContext 권한을 DB 기준으로 재구성
 *      (강등·승격이 다음 로그인까지 미뤄지지 않도록 — 401 처리보다 UX 우수)
 * - ③ 온보딩 미완료(hire_date null): /api/auth/* 외 접근 시 403 (검증 Y-2)
 * - 던진 예외는 HandlerExceptionResolver를 타고 GlobalExceptionHandler가 처리
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class OnboardingCheckInterceptor implements HandlerInterceptor {

    /** 온보딩 미완료 상태에서도 허용하는 경로 접두사 (내 정보·온보딩·로그아웃) */
    private static final String AUTH_PATH_PREFIX = "/api/auth/";

    private final UserRepository userRepository;

    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        // 미인증 요청은 Security(EntryPoint 401) 담당 — 여기선 통과
        if (authentication == null || !(authentication.getPrincipal() instanceof AuthUser authUser)) {
            return true;
        }

        User user = userRepository.findById(authUser.id())
                .orElseThrow(() -> new BusinessException(ErrorCode.USER_NOT_FOUND));

        // ① 토큰 발급 이후 퇴직 처리된 계정 — 전 경로 차단
        if (!user.isActive()) {
            throw new BusinessException(ErrorCode.RETIRED_USER);
        }

        // ② DB role이 토큰 role과 다르면 권한 재구성 (변경 즉시 반영)
        if (user.getRole() != authUser.role()) {
            log.debug("[권한 갱신] userId={}: 토큰 role {} → DB role {}", user.getId(), authUser.role(), user.getRole());
            AuthUser refreshed = new AuthUser(authUser.id(), authUser.email(), user.getRole());
            SecurityContextHolder.getContext().setAuthentication(new UsernamePasswordAuthenticationToken(
                    refreshed,
                    null,
                    List.of(new SimpleGrantedAuthority("ROLE_" + user.getRole().name()))
            ));
        }

        // ③ 온보딩(생일·입사일 입력) 미완료 → /api/auth/* 외 403
        // getRequestURI()는 context-path를 포함하므로 제거 후 판정 (context-path 도입 시 오차단 방지)
        String path = request.getRequestURI().substring(request.getContextPath().length());
        if (!path.startsWith(AUTH_PATH_PREFIX) && !user.isOnboardingCompleted()) {
            throw new BusinessException(ErrorCode.ONBOARDING_NOT_COMPLETED);
        }
        return true;
    }
}
