package com.mlsoft.backend.domain.email.controller;

import com.mlsoft.backend.domain.email.dto.HolidayCredentialUpdateRequest;
import com.mlsoft.backend.domain.email.dto.HolidayVerifyRequest;
import com.mlsoft.backend.domain.email.dto.HolidayVerifyResponse;
import com.mlsoft.backend.domain.email.dto.HolidayIntegrationResponse;
import com.mlsoft.backend.domain.email.dto.IntegrationResponse;
import com.mlsoft.backend.domain.email.dto.MailCredentialUpdateRequest;
import com.mlsoft.backend.domain.email.dto.MailIntegrationResponse;
import com.mlsoft.backend.domain.email.service.IntegrationAdminService;
import com.mlsoft.backend.global.response.CommonResponse;
import com.mlsoft.backend.global.response.ResponseMessage;
import com.mlsoft.backend.security.AuthUser;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** 공휴일·메일 외부 연동 관리자 API. */
@RestController
@RequestMapping("/api/admin/integrations")
@RequiredArgsConstructor
public class IntegrationAdminController {

    private final IntegrationAdminService integrationAdminService;

    @GetMapping
    @PreAuthorize("hasRole('SYSTEM_ADMIN')")
    public ResponseEntity<CommonResponse<IntegrationResponse>> get() {
        return ResponseEntity.ok(CommonResponse.success(
                ResponseMessage.INTEGRATIONS_FETCHED,
                integrationAdminService.getIntegrations()));
    }

    @PutMapping("/mail")
    @PreAuthorize("hasRole('SYSTEM_ADMIN')")
    public ResponseEntity<CommonResponse<MailIntegrationResponse>> saveMail(
            @Valid @RequestBody MailCredentialUpdateRequest request,
            @AuthenticationPrincipal AuthUser authUser
    ) {
        return ResponseEntity.ok(CommonResponse.success(
                ResponseMessage.INTEGRATION_UPDATED,
                integrationAdminService.saveMail(request, authUser.id())));
    }

    @PutMapping("/holiday")
    @PreAuthorize("hasRole('SYSTEM_ADMIN')")
    public ResponseEntity<CommonResponse<HolidayIntegrationResponse>> saveHoliday(
            @Valid @RequestBody HolidayCredentialUpdateRequest request,
            @AuthenticationPrincipal AuthUser authUser
    ) {
        return ResponseEntity.ok(CommonResponse.success(
                ResponseMessage.INTEGRATION_UPDATED,
                integrationAdminService.saveHoliday(request, authUser.id())));
    }

    @PostMapping("/mail/test")
    @PreAuthorize("hasRole('SYSTEM_ADMIN')")
    public ResponseEntity<CommonResponse<Void>> testMail(
            @AuthenticationPrincipal AuthUser authUser
    ) {
        integrationAdminService.sendTestMail(authUser.email(), authUser.id());
        return ResponseEntity.ok(CommonResponse.success(ResponseMessage.MAIL_TEST_QUEUED));
    }

    @PostMapping("/holiday/verify")
    @PreAuthorize("hasRole('SYSTEM_ADMIN')")
    public ResponseEntity<CommonResponse<HolidayVerifyResponse>> verifyHoliday(
            @RequestBody(required = false) HolidayVerifyRequest request
    ) {
        return ResponseEntity.ok(CommonResponse.success(
                ResponseMessage.HOLIDAY_CREDENTIAL_VERIFIED,
                integrationAdminService.verifyHoliday(request == null ? null : request.apiKey())));
    }

    /** 지원하지 않는 provider를 명시적인 비즈니스 오류로 돌려준다. */
    @PutMapping("/{provider}")
    @PreAuthorize("hasRole('SYSTEM_ADMIN')")
    public ResponseEntity<CommonResponse<Void>> unsupportedProvider(
            @org.springframework.web.bind.annotation.PathVariable String provider,
            @RequestBody(required = false) Object ignored
    ) {
        throw new com.mlsoft.backend.global.exception.BusinessException(
                com.mlsoft.backend.global.exception.ErrorCode.EMAIL_PROVIDER_NOT_FOUND);
    }
}
