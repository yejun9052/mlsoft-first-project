package com.mlsoft.backend.domain.email.service;

import com.mlsoft.backend.domain.audit.service.AdminAuditService;
import com.mlsoft.backend.domain.credential.SecretCipher;
import com.mlsoft.backend.domain.email.dto.HolidayVerifyResponse;
import com.mlsoft.backend.domain.holiday.client.HolidayApiClient;
import com.mlsoft.backend.domain.holiday.credential.HolidayApiCredentialService;
import com.mlsoft.backend.global.exception.BusinessException;
import com.mlsoft.backend.global.exception.ErrorCode;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;

@ExtendWith(MockitoExtension.class)
class IntegrationAdminServiceTest {

    @Mock
    private MailCredentialService mailCredentialService;
    @Mock
    private HolidayApiCredentialService holidayApiCredentialService;
    @Mock
    private SecretCipher secretCipher;
    @Mock
    private HolidayApiClient holidayApiClient;
    @Mock
    private AdminAuditService adminAuditService;

    @InjectMocks
    private IntegrationAdminService integrationAdminService;

    @Test
    void 키가없으면_검증실패를예외로표면화한다() {
        given(holidayApiCredentialService.resolveApiKey()).willReturn(Optional.empty());

        BusinessException exception = assertThrows(
                BusinessException.class, () -> integrationAdminService.verifyHoliday(null));

        assertEquals(ErrorCode.HOLIDAY_API_KEY_NOT_CONFIGURED, exception.getErrorCode());
    }

    @Test
    void 정상응답_공휴일이없어도_성공으로반환한다() {
        given(holidayApiCredentialService.resolveApiKey()).willReturn(Optional.of("safe-key"));
        given(holidayApiClient.fetchByYear(anyInt(), eq("safe-key"))).willReturn(
                new HolidayApiClient.HolidayFetchResult(HolidayApiClient.Outcome.OK, List.of()));

        HolidayVerifyResponse response = integrationAdminService.verifyHoliday(null);

        assertEquals(new HolidayVerifyResponse(true, 0), response);
    }

    @Test
    void 외부호출실패는_검증오류로표면화한다() {
        given(holidayApiClient.fetchByYear(anyInt(), eq("safe-key"))).willReturn(
                new HolidayApiClient.HolidayFetchResult(HolidayApiClient.Outcome.CALL_FAILED, List.of()));

        BusinessException exception = assertThrows(
                BusinessException.class, () -> integrationAdminService.verifyHoliday("safe-key"));

        assertEquals(ErrorCode.HOLIDAY_API_CALL_FAILED, exception.getErrorCode());
    }
}
