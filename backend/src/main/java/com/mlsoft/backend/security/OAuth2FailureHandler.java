package com.mlsoft.backend.security;

import com.mlsoft.backend.config.AppProperties;
import com.mlsoft.backend.global.exception.ErrorCode;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.oauth2.core.OAuth2AuthenticationException;
import org.springframework.security.web.authentication.SimpleUrlAuthenticationFailureHandler;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;

/**
 * OAuth2 로그인 실패 처리.
 * - `${frontend-url}/login?error={code}&message={인코딩된 한글 메시지}` 리다이렉트 (docs/03 인증)
 * - 메시지는 ErrorCode enum에서 조회 (하드코딩 금지)
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class OAuth2FailureHandler extends SimpleUrlAuthenticationFailureHandler {

    private static final String DEFAULT_ERROR_CODE = "oauth_failed";

    private final AppProperties appProperties;

    @Override
    public void onAuthenticationFailure(HttpServletRequest request,
                                        HttpServletResponse response,
                                        AuthenticationException exception) throws IOException {
        String errorCode = resolveErrorCode(exception);
        String message = resolveMessage(errorCode);
        log.warn("[OAuth2] 로그인 실패: code={}, cause={}", errorCode, exception.getMessage());

        String redirectUrl = appProperties.frontendUrl() + "/login"
                + "?error=" + URLEncoder.encode(errorCode, StandardCharsets.UTF_8)
                + "&message=" + URLEncoder.encode(message, StandardCharsets.UTF_8);
        getRedirectStrategy().sendRedirect(request, response, redirectUrl);
    }

    // CustomOAuth2UserService가 던진 OAuth2Error 코드 추출 (그 외는 일반 실패)
    private String resolveErrorCode(AuthenticationException exception) {
        if (exception instanceof OAuth2AuthenticationException oAuth2Exception) {
            return oAuth2Exception.getError().getErrorCode();
        }
        return DEFAULT_ERROR_CODE;
    }

    // 실패 코드 → ErrorCode 메시지 매핑
    private String resolveMessage(String errorCode) {
        return switch (errorCode) {
            case CustomOAuth2UserService.ERROR_UNAUTHORIZED_DOMAIN -> ErrorCode.UNAUTHORIZED_DOMAIN.getMessage();
            case CustomOAuth2UserService.ERROR_RETIRED -> ErrorCode.RETIRED_USER.getMessage();
            default -> ErrorCode.OAUTH_LOGIN_FAILED.getMessage();
        };
    }
}
