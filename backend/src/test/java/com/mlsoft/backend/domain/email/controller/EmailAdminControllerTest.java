package com.mlsoft.backend.domain.email.controller;

import com.mlsoft.backend.domain.email.dto.EmailBulkRequest;
import com.mlsoft.backend.domain.email.dto.EmailBulkResponse;
import com.mlsoft.backend.domain.email.dto.EmailHistoryResponse;
import com.mlsoft.backend.domain.email.dto.EmailPreviewResponse;
import com.mlsoft.backend.domain.email.dto.EmailTemplateResponse;
import com.mlsoft.backend.domain.email.dto.EmailTemplateUpdateRequest;
import com.mlsoft.backend.domain.email.dto.HolidayCredentialUpdateRequest;
import com.mlsoft.backend.domain.email.dto.HolidayIntegrationResponse;
import com.mlsoft.backend.domain.email.dto.HolidayVerifyResponse;
import com.mlsoft.backend.domain.email.dto.IntegrationResponse;
import com.mlsoft.backend.domain.email.dto.MailCredentialUpdateRequest;
import com.mlsoft.backend.domain.email.dto.MailIntegrationResponse;
import com.mlsoft.backend.domain.email.dto.ReminderTarget;
import com.mlsoft.backend.domain.email.entity.EmailStatus;
import com.mlsoft.backend.domain.email.entity.EmailType;
import com.mlsoft.backend.domain.email.service.EmailAdminService;
import com.mlsoft.backend.domain.email.service.EmailTemplateAdminService;
import com.mlsoft.backend.domain.email.service.IntegrationAdminService;
import com.mlsoft.backend.domain.user.entity.Role;
import com.mlsoft.backend.global.exception.GlobalExceptionHandler;
import com.mlsoft.backend.security.AuthUser;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableHandlerMethodArgumentResolver;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.web.method.support.HandlerMethodArgumentResolver;
import org.springframework.core.MethodParameter;
import org.springframework.web.context.request.NativeWebRequest;
import org.springframework.web.bind.support.WebDataBinderFactory;
import org.springframework.web.method.support.ModelAndViewContainer;

import java.math.BigDecimal;
import java.util.List;

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

/** W4a 관리자 API 11개 경로의 계약·JSON 필드 회귀 테스트. */
@ExtendWith(MockitoExtension.class)
class EmailAdminControllerTest {

    @Mock
    private EmailAdminService emailAdminService;
    @Mock
    private EmailTemplateAdminService emailTemplateAdminService;
    @Mock
    private IntegrationAdminService integrationAdminService;

    private MockMvc emailMvc;
    private MockMvc templateMvc;
    private MockMvc integrationMvc;

    @BeforeEach
    void setUp() {
        HandlerMethodArgumentResolver authResolver = new HandlerMethodArgumentResolver() {
            @Override
            public boolean supportsParameter(MethodParameter parameter) {
                return parameter.hasParameterAnnotation(
                        org.springframework.security.core.annotation.AuthenticationPrincipal.class);
            }

            @Override
            public Object resolveArgument(MethodParameter parameter, ModelAndViewContainer mavContainer,
                                          NativeWebRequest webRequest, WebDataBinderFactory binderFactory) {
                return new AuthUser(99L, "admin@mlsoft.com", Role.SYSTEM_ADMIN);
            }
        };
        emailMvc = standaloneSetup(new EmailAdminController(emailAdminService))
                .setCustomArgumentResolvers(new PageableHandlerMethodArgumentResolver(), authResolver)
                .setControllerAdvice(new GlobalExceptionHandler())
                .build();
        templateMvc = standaloneSetup(new EmailTemplateAdminController(emailTemplateAdminService))
                .setCustomArgumentResolvers(authResolver)
                .setControllerAdvice(new GlobalExceptionHandler())
                .build();
        integrationMvc = standaloneSetup(new IntegrationAdminController(integrationAdminService))
                .setCustomArgumentResolvers(authResolver)
                .setControllerAdvice(new GlobalExceptionHandler())
                .build();
    }

    @Test
    @DisplayName("리마인더 대상 목록 계약")
    void reminderTargets() throws Exception {
        given(emailAdminService.findReminderTargets()).willReturn(List.of(new ReminderTarget(
                1L, "홍길동", "개발팀", new BigDecimal("5.0"),
                java.time.LocalDate.of(2026, 9, 30), 20, true)));

        emailMvc.perform(get("/api/emails/reminder-targets"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data[0].userId").value(1))
                .andExpect(jsonPath("$.data[0].nextResetDate").value("2026-09-30"));
    }

    @Test
    @DisplayName("일괄 발송 계약")
    void bulk() throws Exception {
        given(emailAdminService.bulk(any(EmailBulkRequest.class), eq(99L)))
                .willReturn(new EmailBulkResponse(1, 1, 0));

        emailMvc.perform(post("/api/emails/bulk")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"userIds\":[1],\"title\":\"공지\",\"content\":\"내용\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.requested").value(1))
                .andExpect(jsonPath("$.data.queued").value(1));
    }

    @Test
    @DisplayName("이력 조회 계약과 수신자 필터 전달")
    void histories() throws Exception {
        given(emailAdminService.findHistories(eq("NOTICE"), eq("FAILED"), any(Pageable.class)))
                .willReturn(new PageImpl<>(List.of(new EmailHistoryResponse(
                        1L, "홍길동", "ho***@mlsoft.com", EmailType.NOTICE,
                        EmailStatus.FAILED, "제목", 1, "실패", null, null))));

        var response = new EmailAdminController(emailAdminService)
                .histories("NOTICE", "FAILED", PageRequest.of(0, 20));
        org.junit.jupiter.api.Assertions.assertEquals(
                "ho***@mlsoft.com",
                response.getBody().data().getContent().getFirst().recipientEmailMasked());
    }

    @Test
    @DisplayName("실패 이력 재발송 계약")
    void resend() throws Exception {
        given(emailAdminService.resend(1L, 99L)).willReturn(new EmailHistoryResponse(
                1L, "홍길동", "ho***@mlsoft.com", EmailType.NOTICE,
                EmailStatus.PENDING, "제목", 0, null, null, null));

        emailMvc.perform(post("/api/emails/1/resend"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.status").value("PENDING"));
        verify(emailAdminService).resend(1L, 99L);
    }

    @Test
    @DisplayName("양식 조회·수정·미리보기 계약")
    void templates() throws Exception {
        EmailTemplateResponse item = new EmailTemplateResponse(
                "LEAVE_BALANCE_REMINDER", "제목", "본문", 1,
                null, "관리자", List.of("{name}"));
        given(emailTemplateAdminService.findAll()).willReturn(List.of(item));
        given(emailTemplateAdminService.update(eq("LEAVE_BALANCE_REMINDER"), any(EmailTemplateUpdateRequest.class), eq(99L)))
                .willReturn(item);
        given(emailTemplateAdminService.preview(eq("LEAVE_BALANCE_REMINDER"), any(EmailTemplateUpdateRequest.class)))
                .willReturn(new EmailPreviewResponse("제목", "<html>본문</html>"));

        templateMvc.perform(get("/api/admin/email-templates"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data[0].templateKey").value("LEAVE_BALANCE_REMINDER"));
        String body = "{\"subjectTemplate\":\"제목\",\"bodyTemplate\":\"본문 {name}\"}";
        templateMvc.perform(put("/api/admin/email-templates/LEAVE_BALANCE_REMINDER")
                        .contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isOk());
        templateMvc.perform(post("/api/admin/email-templates/LEAVE_BALANCE_REMINDER/preview")
                        .contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.html").value("<html>본문</html>"));
    }

    @Test
    @DisplayName("연동 조회·메일 저장·공휴일 저장·테스트·검증 계약")
    void integrations() throws Exception {
        given(integrationAdminService.getIntegrations()).willReturn(new IntegrationResponse(
                new MailIntegrationResponse("SMTP", "sender@mlsoft.com", "••••word", true),
                new HolidayIntegrationResponse("DATA_GO_KR", "••••1234", true), true));
        given(integrationAdminService.saveMail(any(MailCredentialUpdateRequest.class), eq(99L)))
                .willReturn(new MailIntegrationResponse("SMTP", "sender@mlsoft.com", "••••word", true));
        given(integrationAdminService.saveHoliday(any(HolidayCredentialUpdateRequest.class), eq(99L)))
                .willReturn(new HolidayIntegrationResponse("DATA_GO_KR", "••••1234", true));
        given(integrationAdminService.verifyHoliday(any())).willReturn(new HolidayVerifyResponse(true, 22));

        integrationMvc.perform(get("/api/admin/integrations"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.mail.maskedSecret").value("••••word"))
                .andExpect(jsonPath("$.data.encryptionConfigured").value(true));
        integrationMvc.perform(put("/api/admin/integrations/mail")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"username\":\"sender@mlsoft.com\",\"secret\":\"secret\"}"))
                .andExpect(status().isOk());
        integrationMvc.perform(put("/api/admin/integrations/holiday")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"apiKey\":\"key\"}"))
                .andExpect(status().isOk());
        integrationMvc.perform(post("/api/admin/integrations/mail/test"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.message").value("테스트 메일을 큐에 넣었습니다"));
        integrationMvc.perform(post("/api/admin/integrations/holiday/verify")
                        .contentType(MediaType.APPLICATION_JSON).content("{}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.valid").value(true))
                .andExpect(jsonPath("$.data.count").value(22));
    }

}
