package com.mlsoft.backend.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.util.List;

/**
 * 앱 공통 설정 (application.yml의 app.* — 인증·CORS·쿠키).
 *
 * @param allowedDomain 로그인 허용 이메일 도메인 (기본 mlsoft.com, 개발 중 환경변수로 대체 가능)
 * @param adminEmails   첫 로그인 시 SYSTEM_ADMIN을 부여할 이메일 목록 (검증 R-3, 콤마 구분)
 * @param frontendUrl   프론트엔드 오리진 (CORS 허용 + OAuth 리다이렉트 대상)
 * @param cookieSecure  JWT 쿠키 Secure 플래그 (운영 HTTPS에서 true)
 */
@ConfigurationProperties(prefix = "app")
public record AppProperties(
        String allowedDomain,
        List<String> adminEmails,
        String frontendUrl,
        boolean cookieSecure
) {

    /** admin-emails 정규화 — null 방어 + 공백 제거 + 소문자 통일 */
    public AppProperties {
        adminEmails = adminEmails == null ? List.of()
                : adminEmails.stream()
                        .map(String::trim)
                        .filter(email -> !email.isEmpty())
                        .map(String::toLowerCase)
                        .toList();
    }

    /** 최초 관리자 부트스트랩 대상 여부 (검증 R-3) */
    public boolean isAdminEmail(String email) {
        return email != null && adminEmails.contains(email.toLowerCase());
    }
}
