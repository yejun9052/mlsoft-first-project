package com.mlsoft.backend.domain.email.controller;

import com.mlsoft.backend.domain.email.dto.EmailPreviewResponse;
import com.mlsoft.backend.domain.email.dto.EmailTemplateResponse;
import com.mlsoft.backend.domain.email.dto.EmailTemplateUpdateRequest;
import com.mlsoft.backend.domain.email.service.EmailTemplateAdminService;
import com.mlsoft.backend.domain.user.entity.Role;
import com.mlsoft.backend.global.exception.BusinessException;
import com.mlsoft.backend.global.exception.ErrorCode;
import com.mlsoft.backend.global.exception.GlobalExceptionHandler;
import com.mlsoft.backend.security.AuthUser;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.aop.framework.ProxyFactory;
import org.springframework.core.MethodParameter;
import org.springframework.http.MediaType;
import org.springframework.security.authentication.TestingAuthenticationToken;
import org.springframework.security.authorization.method.AuthorizationManagerBeforeMethodInterceptor;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.web.bind.support.WebDataBinderFactory;
import org.springframework.web.context.request.NativeWebRequest;
import org.springframework.web.method.support.HandlerMethodArgumentResolver;
import org.springframework.web.method.support.ModelAndViewContainer;

import java.util.List;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static org.springframework.test.web.servlet.setup.MockMvcBuilders.standaloneSetup;

/**
 * W4b {@code EmailTemplateAdminController} 계약·권한 게이트 테스트.
 *
 * <p>{@link EmailAdminControllerTest}와 같은 standaloneSetup 슬라이스 방식을 쓰되,
 * 그 패턴은 {@code @PreAuthorize}를 전혀 평가하지 않는다(컨트롤러를 {@code new}로 직접
 * 생성해 Spring AOP 프록시를 타지 않는다) — 그래서 이 클래스는 SYSTEM_ADMIN 고정
 * 인자 리졸버 대신 역할을 바꿔가며 로그인할 수 있는 리졸버를 쓰고, 컨트롤러를
 * {@link AuthorizationManagerBeforeMethodInterceptor#preAuthorize()} 어드바이저로 감싼
 * 프록시를 대상으로 MockMvc를 구성한다. {@code GlobalExceptionHandler}가 이미
 * {@code AccessDeniedException}을 403으로 변환하므로 그 경로를 그대로 검증한다.
 */
@ExtendWith(MockitoExtension.class)
class EmailTemplateAdminControllerTest {

    private static final String TEMPLATE_KEY = "LEAVE_BALANCE_REMINDER";

    @Mock
    private EmailTemplateAdminService emailTemplateAdminService;

    private MockMvc mvc;
    private AuthUser currentAuthUser;

    @BeforeEach
    void setUp() {
        HandlerMethodArgumentResolver authResolver = new HandlerMethodArgumentResolver() {
            @Override
            public boolean supportsParameter(MethodParameter parameter) {
                return parameter.hasParameterAnnotation(AuthenticationPrincipal.class);
            }

            @Override
            public Object resolveArgument(MethodParameter parameter, ModelAndViewContainer mavContainer,
                                          NativeWebRequest webRequest, WebDataBinderFactory binderFactory) {
                return currentAuthUser;
            }
        };
        mvc = standaloneSetup(securedController())
                .setCustomArgumentResolvers(authResolver)
                .setControllerAdvice(new GlobalExceptionHandler())
                .build();
        loginAs(Role.SYSTEM_ADMIN);
    }

    @AfterEach
    void tearDown() {
        SecurityContextHolder.clearContext();
    }

    /** {@code @PreAuthorize}가 실제로 평가되도록 컨트롤러를 메서드 시큐리티 어드바이저로 감싼다. */
    private EmailTemplateAdminController securedController() {
        ProxyFactory factory = new ProxyFactory(new EmailTemplateAdminController(emailTemplateAdminService));
        factory.setProxyTargetClass(true);
        factory.addAdvisor(AuthorizationManagerBeforeMethodInterceptor.preAuthorize());
        return (EmailTemplateAdminController) factory.getProxy();
    }

    private void loginAs(Role role) {
        currentAuthUser = new AuthUser(99L, "admin@mlsoft.com", role);
        SecurityContextHolder.getContext().setAuthentication(
                new TestingAuthenticationToken(currentAuthUser, null, "ROLE_" + role.name()));
    }

    @Test
    @DisplayName("양식 목록 조회 계약")
    void 양식_목록_조회_계약() throws Exception {
        given(emailTemplateAdminService.findAll()).willReturn(List.of(new EmailTemplateResponse(
                TEMPLATE_KEY, "제목", "본문", 1, null, "관리자", List.of("{name}"))));

        mvc.perform(get("/api/admin/email-templates"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data[0].templateKey").value(TEMPLATE_KEY));
    }

    @Test
    @DisplayName("양식 수정 계약")
    void 양식_수정_계약() throws Exception {
        given(emailTemplateAdminService.update(eq(TEMPLATE_KEY), any(EmailTemplateUpdateRequest.class), eq(99L)))
                .willReturn(new EmailTemplateResponse(
                        TEMPLATE_KEY, "새 제목", "새 본문", 2, null, "관리자", List.of("{name}")));

        mvc.perform(put("/api/admin/email-templates/{templateKey}", TEMPLATE_KEY)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"subjectTemplate\":\"새 제목\",\"bodyTemplate\":\"새 본문 {name}\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.version").value(2))
                .andExpect(jsonPath("$.data.subjectTemplate").value("새 제목"));
    }

    @Test
    @DisplayName("미리보기 응답에 치환된 본문이 담긴다")
    void 미리보기_치환된_본문_반환() throws Exception {
        given(emailTemplateAdminService.preview(eq(TEMPLATE_KEY), any(EmailTemplateUpdateRequest.class)))
                .willReturn(new EmailPreviewResponse("연차 안내", "<p>홍길동님, 잔여 5.0일 남았습니다.</p>"));

        mvc.perform(post("/api/admin/email-templates/{templateKey}/preview", TEMPLATE_KEY)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"subjectTemplate\":\"연차 안내\",\"bodyTemplate\":\"{name}님, 잔여 {days}일\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.html").value("<p>홍길동님, 잔여 5.0일 남았습니다.</p>"));
    }

    @Test
    @DisplayName("잘못된 templateKey는 EMAIL_TEMPLATE_NOT_FOUND로 떨어진다")
    void 잘못된_templateKey는_EMAIL_TEMPLATE_NOT_FOUND() throws Exception {
        given(emailTemplateAdminService.update(eq("UNKNOWN_KEY"), any(EmailTemplateUpdateRequest.class), eq(99L)))
                .willThrow(new BusinessException(ErrorCode.EMAIL_TEMPLATE_NOT_FOUND));

        mvc.perform(put("/api/admin/email-templates/{templateKey}", "UNKNOWN_KEY")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"subjectTemplate\":\"제목\",\"bodyTemplate\":\"본문\"}"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.message").value(ErrorCode.EMAIL_TEMPLATE_NOT_FOUND.getMessage()));
    }

    @Test
    @DisplayName("본문 길이 초과는 저장 전에 400으로 막힌다")
    void 본문_길이초과는_400() throws Exception {
        String tooLong = "a".repeat(20_001);

        mvc.perform(put("/api/admin/email-templates/{templateKey}", TEMPLATE_KEY)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"subjectTemplate\":\"제목\",\"bodyTemplate\":\"" + tooLong + "\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.message").value("메일 본문 양식은 20,000자 이내로 입력해주세요."));
    }

    @Test
    @DisplayName("EMPLOYEE·TEAM_LEADER는 403, SYSTEM_ADMIN만 통과한다")
    void 권한게이트_EMPLOYEE_TEAM_LEADER는_403() throws Exception {
        String body = "{\"subjectTemplate\":\"제목\",\"bodyTemplate\":\"본문\"}";

        for (Role denied : List.of(Role.EMPLOYEE, Role.TEAM_LEADER)) {
            loginAs(denied);
            mvc.perform(get("/api/admin/email-templates"))
                    .andExpect(status().isForbidden());
            mvc.perform(put("/api/admin/email-templates/{templateKey}", TEMPLATE_KEY)
                            .contentType(MediaType.APPLICATION_JSON).content(body))
                    .andExpect(status().isForbidden());
            mvc.perform(post("/api/admin/email-templates/{templateKey}/preview", TEMPLATE_KEY)
                            .contentType(MediaType.APPLICATION_JSON).content(body))
                    .andExpect(status().isForbidden());
        }

        loginAs(Role.SYSTEM_ADMIN);
        given(emailTemplateAdminService.findAll()).willReturn(List.of());
        mvc.perform(get("/api/admin/email-templates"))
                .andExpect(status().isOk());
    }
}
