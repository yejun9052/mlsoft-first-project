package com.mlsoft.backend.config;

import lombok.RequiredArgsConstructor;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;

/**
 * local·test 프로필이 아닌데 app.cookie-secure(COOKIE_SECURE)가 꺼져 있으면 기동을 막는다.
 * 기본값이 false(fail-open)라 배포 시 COOKIE_SECURE=true 설정을 빠뜨리면
 * JWT 쿠키가 Secure 플래그 없이 평문(HTTP) 채널로도 전송될 수 있다.
 */
@Component
@Profile("!local & !test")
@RequiredArgsConstructor
public class CookieSecurityCheck implements ApplicationRunner {

    private final AppProperties appProperties;

    @Override
    public void run(ApplicationArguments args) {
        if (!appProperties.cookieSecure()) {
            throw new IllegalStateException(
                    "COOKIE_SECURE=true 환경변수가 필요합니다 (local/test 프로필이 아닌 환경에서 " +
                            "JWT 쿠키에 Secure 플래그가 빠지면 안 됩니다).");
        }
    }
}
