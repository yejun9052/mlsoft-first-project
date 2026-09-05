package com.mlsoft.backend.domain.email.service;

import com.mlsoft.backend.domain.audit.service.AdminAuditService;
import com.mlsoft.backend.domain.credential.SecretCipher;
import com.mlsoft.backend.domain.email.dto.HolidayIntegrationResponse;
import com.mlsoft.backend.domain.email.dto.HolidayVerifyResponse;
import com.mlsoft.backend.domain.email.dto.IntegrationResponse;
import com.mlsoft.backend.domain.email.dto.MailIntegrationResponse;
import com.mlsoft.backend.domain.email.dto.MailCredentialUpdateRequest;
import com.mlsoft.backend.domain.email.dto.HolidayCredentialUpdateRequest;
import com.mlsoft.backend.domain.holiday.client.HolidayApiClient;
import com.mlsoft.backend.domain.holiday.credential.HolidayApiCredentialMaskDto;
import com.mlsoft.backend.domain.holiday.credential.HolidayApiCredentialService;
import com.mlsoft.backend.global.exception.ErrorCode;
import com.mlsoft.backend.global.exception.BusinessException;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.ZoneId;
import java.util.List;

/** 공휴일·SMTP 외부 연동의 관리자 업무 경계. */
@Service
@RequiredArgsConstructor
public class IntegrationAdminService {

    private static final ZoneId KST = ZoneId.of("Asia/Seoul");

    private final MailCredentialService mailCredentialService;
    private final HolidayApiCredentialService holidayApiCredentialService;
    private final SecretCipher secretCipher;
    private final HolidayApiClient holidayApiClient;
    private final AdminAuditService adminAuditService;

    /** 원문 없이 현재 연동 상태를 반환한다. */
    @Transactional(readOnly = true)
    public IntegrationResponse getIntegrations() {
        MailIntegrationResponse mail = mailCredentialService.findMaskedCredential()
                .map(value -> new MailIntegrationResponse(
                        value.provider(), value.username(), value.maskedSecret(), value.active()))
                .orElse(null);
        HolidayIntegrationResponse holiday = holidayApiCredentialService.findMaskedCredential()
                .map(this::toHolidayResponse)
                .orElse(null);
        return new IntegrationResponse(mail, holiday, secretCipher.isConfigured());
    }

    /** SMTP 계정 저장·회전. 감사에는 provider와 username만 남긴다. */
    @Transactional
    public MailIntegrationResponse saveMail(MailCredentialUpdateRequest request, Long actorId) {
        if (request == null) {
            throw new BusinessException(ErrorCode.INVALID_INPUT_VALUE);
        }
        var saved = mailCredentialService.saveOrRotate(
                MailCredentialService.SMTP_PROVIDER, request.username(), request.secret());
        adminAuditService.recordConfigChange(
                actorId,
                "mail",
                "provider=SMTP",
                "provider=SMTP, username=" + saved.username());
        return new MailIntegrationResponse(
                saved.provider(), saved.username(), saved.maskedSecret(), saved.active());
    }

    /** 공휴일 API 키 저장·회전. 원문 키는 감사에 남기지 않는다. */
    @Transactional
    public HolidayIntegrationResponse saveHoliday(HolidayCredentialUpdateRequest request, Long actorId) {
        if (request == null) {
            throw new BusinessException(ErrorCode.INVALID_INPUT_VALUE);
        }
        HolidayApiCredentialMaskDto saved = holidayApiCredentialService.saveOrRotate(request.apiKey());
        adminAuditService.recordConfigChange(
                actorId,
                "holiday",
                "provider=DATA_GO_KR",
                "provider=DATA_GO_KR");
        return toHolidayResponse(saved);
    }

    /** 인증 주체 본인에게만 테스트 메일을 아웃박스에 넣는다. */
    @Transactional
    public void sendTestMail(String authenticatedEmail, Long actorId) {
        mailCredentialService.sendTestMail(authenticatedEmail);
        adminAuditService.recordConfigChange(
                actorId,
                "mail/test",
                "provider=SMTP",
                "testMailQueued=true");
    }

    /** 올해 공휴일을 조회해 키가 유효한지 확인한다. 입력 키는 저장하지 않는다. */
    @Transactional(readOnly = true)
    public HolidayVerifyResponse verifyHoliday(String apiKey) {
        String key = apiKey == null || apiKey.isBlank()
                ? holidayApiCredentialService.resolveApiKey().orElse("")
                : apiKey.trim();
        if (key.isBlank()) {
            return new HolidayVerifyResponse(false, 0);
        }
        try {
            List<HolidayApiClient.HolidayItem> holidays = holidayApiClient.fetchByYear(
                    LocalDate.now(KST).getYear(), key);
            return new HolidayVerifyResponse(!holidays.isEmpty(), holidays.size());
        } catch (RuntimeException e) {
            return new HolidayVerifyResponse(false, 0);
        }
    }

    private HolidayIntegrationResponse toHolidayResponse(HolidayApiCredentialMaskDto value) {
        return new HolidayIntegrationResponse(value.provider(), value.maskedKey(), value.active());
    }
}
