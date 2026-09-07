package com.mlsoft.backend.domain.email.controller;

import com.mlsoft.backend.domain.email.dto.HolidayCredentialUpdateRequest;
import com.mlsoft.backend.domain.email.dto.HolidayIntegrationResponse;
import com.mlsoft.backend.domain.email.dto.HolidayVerifyResponse;
import com.mlsoft.backend.domain.email.dto.IntegrationResponse;
import com.mlsoft.backend.domain.email.dto.MailCredentialUpdateRequest;
import com.mlsoft.backend.domain.email.dto.MailIntegrationResponse;
import com.mlsoft.backend.domain.email.service.IntegrationAdminService;
import com.mlsoft.backend.domain.user.entity.Role;
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
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.web.bind.support.WebDataBinderFactory;
import org.springframework.web.context.request.NativeWebRequest;
import org.springframework.web.method.support.HandlerMethodArgumentResolver;
import org.springframework.web.method.support.ModelAndViewContainer;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.verify;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static org.springframework.test.web.servlet.setup.MockMvcBuilders.standaloneSetup;

/**
 * W4b {@code IntegrationAdminController} 계약·권한 게이트·시크릿 마스킹 테스트.
 *
 * <p>{@link EmailTemplateAdminControllerTest}와 같은 이유로 컨트롤러를
 * {@code @PreAuthorize} 어드바이저 프록시로 감싸 역할 게이트를 실제로 평가한다
 * (자세한 설명은 그쪽 클래스 주석 참고). 이 클래스는 추가로 <b>응답 본문 문자열에
 * 원문 시크릿이 절대 섞이지 않는지</b>를 확인한다 — 마스킹은 서비스 계층 책임이지만,
 * 컨트롤러가 요청 본문의 원문 필드를 실수로 그대로 되돌려주는 경로를 새로 만들지
 * 않았는지는 이 계층에서만 검증할 수 있다.
 */
@ExtendWith(MockitoExtension.class)
class IntegrationAdminControllerTest {

    /** 응답 본문 어디에도 나타나선 안 되는 원문 마커 — 마스킹 값과 확실히 구분되는 문자열을 쓴다. */
    private static final String RAW_MAIL_SECRET = "PLAIN_SMTP_SECRET_9981";
    private static final String RAW_HOLIDAY_KEY = "PLAIN_HOLIDAY_KEY_7742";

    @Mock
    private IntegrationAdminService integrationAdminService;

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

    private IntegrationAdminController securedController() {
        ProxyFactory factory = new ProxyFactory(new IntegrationAdminController(integrationAdminService));
        factory.setProxyTargetClass(true);
        factory.addAdvisor(AuthorizationManagerBeforeMethodInterceptor.preAuthorize());
        return (IntegrationAdminController) factory.getProxy();
    }

    private void loginAs(Role role) {
        currentAuthUser = new AuthUser(99L, "admin@mlsoft.com", role);
        SecurityContextHolder.getContext().setAuthentication(
                new TestingAuthenticationToken(currentAuthUser, null, "ROLE_" + role.name()));
    }

    @Test
    @DisplayName("연동 조회 계약 — 마스킹된 값과 encryptionConfigured만 담긴다")
    void 연동_조회_계약() throws Exception {
        given(integrationAdminService.getIntegrations()).willReturn(new IntegrationResponse(
                new MailIntegrationResponse("SMTP", "sender@mlsoft.com", "••••word", true),
                new HolidayIntegrationResponse("DATA_GO_KR", "••••1234", true), true));

        MvcResult result = mvc.perform(get("/api/admin/integrations"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.mail.maskedSecret").value("••••word"))
                .andExpect(jsonPath("$.data.holiday.maskedKey").value("••••1234"))
                .andExpect(jsonPath("$.data.encryptionConfigured").value(true))
                .andReturn();

        String body = result.getResponse().getContentAsString();
        assertThat(body).doesNotContain(RAW_MAIL_SECRET, RAW_HOLIDAY_KEY);
    }

    @Test
    @DisplayName("메일 계정 저장 응답에는 원문 비밀값이 없다")
    void 메일_저장_원문미노출() throws Exception {
        given(integrationAdminService.saveMail(any(MailCredentialUpdateRequest.class), eq(99L)))
                .willReturn(new MailIntegrationResponse("SMTP", "sender@mlsoft.com", "••••9981", true));

        MvcResult result = mvc.perform(put("/api/admin/integrations/mail")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"username\":\"sender@mlsoft.com\",\"secret\":\"" + RAW_MAIL_SECRET + "\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.maskedSecret").value("••••9981"))
                .andReturn();

        assertThat(result.getResponse().getContentAsString()).doesNotContain(RAW_MAIL_SECRET);
    }

    @Test
    @DisplayName("공휴일 API 키 저장 응답에는 원문 키가 없다")
    void 공휴일_저장_원문미노출() throws Exception {
        given(integrationAdminService.saveHoliday(any(HolidayCredentialUpdateRequest.class), eq(99L)))
                .willReturn(new HolidayIntegrationResponse("DATA_GO_KR", "••••7742", true));

        MvcResult result = mvc.perform(put("/api/admin/integrations/holiday")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"apiKey\":\"" + RAW_HOLIDAY_KEY + "\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.maskedKey").value("••••7742"))
                .andReturn();

        assertThat(result.getResponse().getContentAsString()).doesNotContain(RAW_HOLIDAY_KEY);
    }

    @Test
    @DisplayName("테스트 메일은 인증 주체 본인 이메일로 큐에 들어간다")
    void 테스트_메일_발송_계약() throws Exception {
        mvc.perform(post("/api/admin/integrations/mail/test"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.message").value("테스트 메일을 큐에 넣었습니다"));

        verify(integrationAdminService).sendTestMail("admin@mlsoft.com", 99L);
    }

    @Test
    @DisplayName("공휴일 API 키 검증 계약")
    void 공휴일_키_검증_계약() throws Exception {
        given(integrationAdminService.verifyHoliday(any())).willReturn(new HolidayVerifyResponse(true, 22));

        mvc.perform(post("/api/admin/integrations/holiday/verify")
                        .contentType(MediaType.APPLICATION_JSON).content("{}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.valid").value(true))
                .andExpect(jsonPath("$.data.count").value(22));
    }

    @Test
    @DisplayName("알 수 없는 provider는 EMAIL_PROVIDER_NOT_FOUND로 떨어진다")
    void 알수없는_provider는_EMAIL_PROVIDER_NOT_FOUND() throws Exception {
        mvc.perform(put("/api/admin/integrations/{provider}", "slack")
                        .contentType(MediaType.APPLICATION_JSON).content("{}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.message").value(ErrorCode.EMAIL_PROVIDER_NOT_FOUND.getMessage()));
    }

    @Test
    @DisplayName("EMPLOYEE·TEAM_LEADER는 403, SYSTEM_ADMIN만 통과한다")
    void 권한게이트_EMPLOYEE_TEAM_LEADER는_403() throws Exception {
        String mailBody = "{\"username\":\"sender@mlsoft.com\",\"secret\":\"secret\"}";
        String holidayBody = "{\"apiKey\":\"key\"}";

        for (Role denied : List.of(Role.EMPLOYEE, Role.TEAM_LEADER)) {
            loginAs(denied);
            mvc.perform(get("/api/admin/integrations"))
                    .andExpect(status().isForbidden());
            mvc.perform(put("/api/admin/integrations/mail")
                            .contentType(MediaType.APPLICATION_JSON).content(mailBody))
                    .andExpect(status().isForbidden());
            mvc.perform(put("/api/admin/integrations/holiday")
                            .contentType(MediaType.APPLICATION_JSON).content(holidayBody))
                    .andExpect(status().isForbidden());
            mvc.perform(post("/api/admin/integrations/mail/test"))
                    .andExpect(status().isForbidden());
            mvc.perform(post("/api/admin/integrations/holiday/verify")
                            .contentType(MediaType.APPLICATION_JSON).content("{}"))
                    .andExpect(status().isForbidden());
            mvc.perform(put("/api/admin/integrations/{provider}", "slack")
                            .contentType(MediaType.APPLICATION_JSON).content("{}"))
                    .andExpect(status().isForbidden());
        }

        loginAs(Role.SYSTEM_ADMIN);
        given(integrationAdminService.getIntegrations()).willReturn(new IntegrationResponse(null, null, false));
        mvc.perform(get("/api/admin/integrations"))
                .andExpect(status().isOk());
    }
}
