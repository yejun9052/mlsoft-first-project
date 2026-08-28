package com.mlsoft.backend.domain.holiday.credential;

import lombok.RequiredArgsConstructor;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

/** 앱 기동 시 환경변수 키를 암호화해 credential 테이블에 seed한다. */
@Component
@Order(20)
@RequiredArgsConstructor
public class HolidayApiCredentialInitializer implements ApplicationRunner {

    private final HolidayApiCredentialService credentialService;

    @Override
    public void run(ApplicationArguments args) {
        credentialService.seedFromEnvironment();
    }
}
