package com.mlsoft.backend.config;

import com.mlsoft.backend.security.OnboardingCheckInterceptor;
import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

/**
 * MVC 설정 — 유저 상태 검증 인터셉터 등록 (검증 Y-2).
 * /api/** 전체에 적용한다: 퇴직자 차단·권한 갱신은 /api/auth/* 포함 전 경로,
 * 온보딩 미완료 차단만 인터셉터 내부에서 /api/auth/* 경로를 제외한다.
 */
@Configuration
@RequiredArgsConstructor
public class WebConfig implements WebMvcConfigurer {

    private final OnboardingCheckInterceptor onboardingCheckInterceptor;

    @Override
    public void addInterceptors(InterceptorRegistry registry) {
        registry.addInterceptor(onboardingCheckInterceptor)
                .addPathPatterns("/api/**");
    }
}
