package com.mlsoft.backend.config;

import com.mlsoft.backend.security.OnboardingCheckInterceptor;
import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.io.ClassPathResource;
import org.springframework.core.io.Resource;
import org.springframework.data.domain.Sort;
import org.springframework.data.web.config.EnableSpringDataWebSupport;
import org.springframework.data.web.config.SortHandlerMethodArgumentResolverCustomizer;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.ResourceHandlerRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;
import org.springframework.web.servlet.resource.PathResourceResolver;

import java.io.IOException;

import static org.springframework.data.web.config.EnableSpringDataWebSupport.PageSerializationMode.VIA_DTO;

/**
 * MVC 설정 — 유저 상태 검증 인터셉터 등록 (검증 Y-2) + 홈서버 데모 배포용 SPA 폴백.
 * /api/** 전체에 적용한다: 퇴직자 차단·권한 갱신은 /api/auth/* 포함 전 경로,
 * 온보딩 미완료 차단만 인터셉터 내부에서 /api/auth/* 경로를 제외한다.
 *
 * <h3>페이지 응답을 {@code PagedModel}로 직렬화한다 (2026-08-17)</h3>
 * 기본값({@code DIRECT})은 {@code PageImpl}을 그대로 내보내 페이지 메타가
 * {@code totalPages}·{@code totalElements}처럼 <b>최상위</b>에 흩어진다. Spring도 그 형태는
 * 안정성을 보장하지 않는다고 기동 때마다 WARN을 남긴다.
 *
 * <p>프론트는 처음부터 {@code data.page.totalPages} 형태({@code PagedModel})로 짜여 있었는데
 * 이 모드를 켜지 않아 <b>그 값이 전부 {@code undefined}였다.</b> 예외가 나지 않고 조용히 사라져서
 * 화면에는 이렇게 나타났다:
 * <ul>
 *   <li>페이지 넘김 바가 아예 안 그려짐 — {@code totalPages}가 없으면 1페이지로 판단한다</li>
 *   <li>사이드바 결재 대기 배지가 항상 0</li>
 *   <li>구성원 관리 탭 배지(재직·퇴직·온보딩)에 건수가 안 뜸</li>
 *   <li>결재 관리 상단 전사 현황 숫자가 항상 0</li>
 * </ul>
 * 프론트 테스트는 응답을 목으로 만들어 이 형태를 가정하고 있었으므로 전부 통과했다 —
 * 실제 응답 모양을 검사하는 테스트가 없던 것이 원인이다. 그래서 {@code WebConfigPageSerializationTest}를 함께 둔다.
 */
@Configuration
@RequiredArgsConstructor
@EnableSpringDataWebSupport(pageSerializationMode = VIA_DTO)
public class WebConfig implements WebMvcConfigurer {

    private final OnboardingCheckInterceptor onboardingCheckInterceptor;
    private final PageSizeLimitInterceptor pageSizeLimitInterceptor;

    /**
     * 정렬을 지정하지 않은 페이지 조회의 기본 정렬 — {@code id} 오름차순.
     *
     * <p><b>정렬 없는 페이징은 페이지 사이에서 행이 중복되거나 빠질 수 있다.</b> 순서를 정하지
     * 않으면 DB가 매 쿼리마다 편한 순서로 돌려주므로, 1페이지에서 본 사람이 2페이지에 또 나오거나
     * 아무 페이지에도 안 나올 수 있다. 그사이 다른 관리자가 행을 추가·삭제하면 확률이 올라간다.
     * 목록 대부분이 정렬 없이 페이징되고 있었다 (2026-08-17 감사).
     *
     * <p>내림차순이 아니라 <b>오름차순</b>인 것은 지금 보이는 순서를 바꾸지 않기 위해서다 —
     * MySQL이 단순 조회에서 대체로 PK 순으로 돌려주고 있었다. 결정성만 보장하고 화면은 그대로 둔다.
     *
     * <p>메서드 이름에 {@code OrderBy}가 있는 조회(온보딩 대기의 {@code updateAt} 등)는 그 정렬이
     * 먼저 적용되고 이 값이 <b>동률 보조 키</b>로 뒤에 붙는다. 화면이 정렬을 직접 보내면 그쪽이 이긴다.
     */
    @Bean
    SortHandlerMethodArgumentResolverCustomizer stableSortCustomizer() {
        return resolver -> resolver.setFallbackSort(Sort.by(Sort.Direction.ASC, "id"));
    }

    @Override
    public void addInterceptors(InterceptorRegistry registry) {
        // 페이지 크기 검사를 먼저 — 거절할 요청 때문에 DB(유저 상태 조회)를 볼 이유가 없다 (리뷰 S-4)
        registry.addInterceptor(pageSizeLimitInterceptor)
                .addPathPatterns("/api/**")
                .order(0);
        registry.addInterceptor(onboardingCheckInterceptor)
                .addPathPatterns("/api/**")
                .order(1);
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
