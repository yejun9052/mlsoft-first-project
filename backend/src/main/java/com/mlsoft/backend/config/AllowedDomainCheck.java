package com.mlsoft.backend.config;

import lombok.RequiredArgsConstructor;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;

/**
 * local·test 프로필이 아닌데 app.allowed-domain(ALLOWED_DOMAIN)이 비어 있으면 기동을 막는다 (리뷰 O-1).
 * <p>
 * {@code CustomOAuth2UserService}의 도메인 검증은 값이 비면 검사를 건너뛴다(fail-open).
 * 개발 중 개인 계정으로 로그인하려고 둔 동작인데, 운영에서 값이 비면 <b>아무 Google 계정이나
 * 자동 가입</b>돼 전 직원 연차 일정·팀장 명부가 노출된다.
 * <p>
 * 실제로 밟기 쉬운 경로다 — {@code .env.prod.example}의 {@code ALLOWED_DOMAIN=}을 그대로 복사하면
 * 환경변수가 "빈 값으로 설정된" 상태가 되어 application.yml의 기본값(mlsoft.com)도 적용되지 않는다.
 * <p>
 * docs/08 R-2의 3중 방어 중 ①(OAuth 앱 Internal)·②({@code hd} claim)가 아직 없어
 * 이 검사가 유일하게 살아 있는 방어선이다. {@code COOKIE_SECURE}에는 {@link CookieSecurityCheck}가
 * 있는데 여기에만 없던 비대칭을 맞춘다.
 */
@Component
@Profile("!local & !test")
@RequiredArgsConstructor
public class AllowedDomainCheck implements ApplicationRunner {

    private final AppProperties appProperties;

    @Override
    public void run(ApplicationArguments args) {
        String allowedDomain = appProperties.allowedDomain();
        if (allowedDomain == null || allowedDomain.isBlank()) {
            throw new IllegalStateException(
                    "ALLOWED_DOMAIN 환경변수가 필요합니다 (local/test 프로필이 아닌 환경에서 값이 비면 " +
                            "도메인 검증이 통째로 꺼져 아무 Google 계정이나 자동 가입됩니다).");
        }
        if (allowedDomain.startsWith("@") || allowedDomain.contains(" ")) {
            throw new IllegalStateException(
                    "ALLOWED_DOMAIN 형식이 잘못됐습니다: '" + allowedDomain + "' " +
                            "(@ 없이 도메인만 적습니다. 예: mlsoft.com)");
        }
    }
}
