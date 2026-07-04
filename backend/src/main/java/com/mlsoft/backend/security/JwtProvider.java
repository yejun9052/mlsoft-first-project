package com.mlsoft.backend.security;

import com.mlsoft.backend.domain.user.entity.Role;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;
import java.util.Date;

/**
 * JWT 발급·검증 (jjwt 0.12).
 * - subject: userId / claim: email, role
 * - 서명 키는 환경변수 JWT_SECRET (32byte 이상, 하드코딩 금지)
 */
@Component
public class JwtProvider {

    /** JWT 쿠키 이름 */
    public static final String TOKEN_COOKIE_NAME = "token";

    private static final String CLAIM_EMAIL = "email";
    private static final String CLAIM_ROLE = "role";

    private final SecretKey secretKey;
    private final long expirationMs;

    public JwtProvider(
            @Value("${jwt.secret}") String secret,
            @Value("${jwt.expiration-ms}") long expirationMs
    ) {
        this.secretKey = Keys.hmacShaKeyFor(secret.getBytes(StandardCharsets.UTF_8));
        this.expirationMs = expirationMs;
    }

    /**
     * 액세스 토큰 발급.
     * - 만료: jwt.expiration-ms (기본 24h)
     */
    public String createToken(Long userId, String email, Role role) {
        Date now = new Date();
        return Jwts.builder()
                .subject(String.valueOf(userId))
                .claim(CLAIM_EMAIL, email)
                .claim(CLAIM_ROLE, role.name())
                .issuedAt(now)
                .expiration(new Date(now.getTime() + expirationMs))
                .signWith(secretKey)
                .compact();
    }

    /**
     * 토큰 검증 + 인증 주체 복원.
     *
     * @throws io.jsonwebtoken.JwtException 서명 불일치·만료·형식 오류 시
     */
    public AuthUser parseToken(String token) {
        Claims claims = Jwts.parser()
                .verifyWith(secretKey)
                .build()
                .parseSignedClaims(token)
                .getPayload();
        return new AuthUser(
                Long.valueOf(claims.getSubject()),
                claims.get(CLAIM_EMAIL, String.class),
                Role.valueOf(claims.get(CLAIM_ROLE, String.class))
        );
    }

    /** 토큰 만료 시간(ms) — 쿠키 maxAge 산정용 */
    public long getExpirationMs() {
        return expirationMs;
    }
}
