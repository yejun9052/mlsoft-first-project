package com.mlsoft.backend.config;

import com.mlsoft.backend.security.OnboardingCheckInterceptor;
import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.io.ClassPathResource;
import org.springframework.core.io.Resource;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.ResourceHandlerRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;
import org.springframework.web.servlet.resource.PathResourceResolver;

import java.io.IOException;

/**
 * MVC 설정 — 유저 상태 검증 인터셉터 등록 (검증 Y-2) + 홈서버 데모 배포용 SPA 폴백.
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

    /**
     * SPA 폴백 — 배포용 Dockerfile이 프론트 빌드 산출물을 static 리소스로 함께 패키징하는데,
     * react-router BrowserRouter(경로 기반)라 /dashboard 같은 딥링크를 새로고침하면 실제 파일이
     * 없어 기본적으로 404가 난다. static 리소스로 못 찾는 경로는 index.html로 넘겨 클라이언트
     * 라우팅이 처리하게 한다. /api/**(RequestMappingHandlerMapping, order=0)와
     * /oauth2/**·/login/oauth2/**(Spring Security 필터, 디스패치 이전 단계)는 이 리소스
     * 핸들러보다 먼저 매칭되므로 건드리지 않는다 — 실제 JS/CSS 자산(/assets/**)이 존재하면
     * 그 파일을 그대로 서빙하고, 존재하지 않을 때만 index.html로 대체한다.
     */
    @Override
    public void addResourceHandlers(ResourceHandlerRegistry registry) {
        registry.addResourceHandler("/**")
                .addResourceLocations("classpath:/static/")
                .resourceChain(true)
                .addResolver(new PathResourceResolver() {
                    @Override
                    protected Resource getResource(String resourcePath, Resource location) throws IOException {
                        Resource requested = location.createRelative(resourcePath);
                        return requested.exists() && requested.isReadable()
                                ? requested
                                : new ClassPathResource("/static/index.html");
                    }
                });
    }
}
