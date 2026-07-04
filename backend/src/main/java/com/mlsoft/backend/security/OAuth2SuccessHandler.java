package com.mlsoft.backend.security;

import com.mlsoft.backend.config.AppProperties;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpHeaders;
import org.springframework.security.core.Authentication;
import org.springframework.security.web.authentication.SimpleUrlAuthenticationSuccessHandler;
import org.springframework.stereotype.Component;

import java.io.IOException;

/**
 * OAuth2 로그인 성공 처리.
 * - JWT 발급 → HttpOnly 쿠키 저장 → 프론트 /oauth-callback 리다이렉트 (docs/03 인증)
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class OAuth2SuccessHandler extends SimpleUrlAuthenticationSuccessHandler {

    private static final String CALLBACK_PATH = "/oauth-callback";

    private final JwtProvider jwtProvider;
    private final TokenCookieFactory tokenCookieFactory;
    private final AppProperties appProperties;

    @Override
    public void onAuthenticationSuccess(HttpServletRequest request,
                                        HttpServletResponse response,
                                        Authentication authentication) throws IOException {
        CustomOAuth2User oAuth2User = (CustomOAuth2User) authentication.getPrincipal();

        // JWT 발급 후 HttpOnly 쿠키로 전달 (XSS 토큰 탈취 방지)
        String token = jwtProvider.createToken(oAuth2User.getUserId(), oAuth2User.getEmail(), oAuth2User.getRole());
        response.addHeader(HttpHeaders.SET_COOKIE, tokenCookieFactory.create(token).toString());

        log.info("[OAuth2] 로그인 성공: {} (role={})", oAuth2User.getEmail(), oAuth2User.getRole());
        getRedirectStrategy().sendRedirect(request, response, appProperties.frontendUrl() + CALLBACK_PATH);
    }
}
