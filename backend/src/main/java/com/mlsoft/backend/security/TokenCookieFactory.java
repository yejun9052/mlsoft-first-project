package com.mlsoft.backend.security;

import com.mlsoft.backend.config.AppProperties;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseCookie;
import org.springframework.stereotype.Component;

import java.time.Duration;

/**
 * JWT 쿠키 생성·만료 — OAuth2SuccessHandler(발급)와 AuthController(로그아웃)가 공유.
 * HttpOnly + SameSite=Lax + path=/ 고정, Secure는 app.cookie-secure(운영 HTTPS) 플래그 (docs/01 2-1).
 */
@Component
@RequiredArgsConstructor
public class TokenCookieFactory {

    private final AppProperties appProperties;
    private final JwtProvider jwtProvider;

    /** 로그인 성공 시 JWT 쿠키 생성 (maxAge = 토큰 만료와 동일) */
    public ResponseCookie create(String token) {
        return baseCookie(token, Duration.ofMillis(jwtProvider.getExpirationMs()));
    }

    /** 로그아웃용 즉시 만료 쿠키 */
    public ResponseCookie expire() {
        return baseCookie("", Duration.ZERO);
    }

    // 공통 속성 — 발급/만료가 동일 속성이어야 브라우저가 같은 쿠키로 덮어쓴다
    private ResponseCookie baseCookie(String value, Duration maxAge) {
        return ResponseCookie.from(JwtProvider.TOKEN_COOKIE_NAME, value)
                .httpOnly(true)
                .secure(appProperties.cookieSecure())
                .sameSite("Lax")
                .path("/")
                .maxAge(maxAge)
                .build();
    }
}
