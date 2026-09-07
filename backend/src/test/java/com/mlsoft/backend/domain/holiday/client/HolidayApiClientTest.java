package com.mlsoft.backend.domain.holiday.client;

import com.mlsoft.backend.domain.holiday.credential.HolidayApiCredentialService;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.ObjectMapper;

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.mock;

class HolidayApiClientTest {

    private final ObjectMapper objectMapper = new ObjectMapper();

    @Test
    void parsesOnlySuccessfulHolidayResponse() throws Exception {
        var root = objectMapper.readTree("""
                {
                  "response": {
                    "header": {"resultCode": "00", "resultMsg": "NORMAL SERVICE."},
                    "body": {
                      "items": {
                        "item": {
                          "dateName": "테스트 공휴일",
                          "isHoliday": "Y",
                          "locdate": "20260815"
                        }
                      }
                    }
                  }
                }
                """);

        HolidayApiClient.HolidayFetchResult result = HolidayApiClient.parse(root, 2026);

        assertEquals(HolidayApiClient.Outcome.OK, result.outcome());
        assertEquals(1, result.items().size());
        assertEquals("테스트 공휴일", result.items().get(0).name());
    }

    @Test
    void treatsAuthenticationOrQuotaErrorAsBadResponse() throws Exception {
        var root = objectMapper.readTree("""
                {
                  "response": {
                    "header": {"resultCode": "30", "resultMsg": "SERVICE KEY IS NOT REGISTERED ERROR."},
                    "body": {"items": {"item": []}}
                  }
                }
                """);

        HolidayApiClient.HolidayFetchResult result = HolidayApiClient.parse(root, 2026);

        assertEquals(HolidayApiClient.Outcome.BAD_RESPONSE, result.outcome());
        assertTrue(result.items().isEmpty());
    }

    @Test
    void treatsEmptyItemArrayAsSuccessfulZeroResult() throws Exception {
        var root = objectMapper.readTree("""
                {
                  "response": {
                    "header": {"resultCode": "00", "resultMsg": "NORMAL SERVICE."},
                    "body": {"items": {"item": []}}
                  }
                }
                """);

        HolidayApiClient.HolidayFetchResult result = HolidayApiClient.parse(root, 2026);

        assertEquals(HolidayApiClient.Outcome.OK, result.outcome());
        assertTrue(result.items().isEmpty());
    }

    @Test
    void treatsPortalEmptyItemsWithZeroTotalCountAsSuccessfulZeroResult() throws Exception {
        var root = objectMapper.readTree("""
                {
                  "response": {
                    "header": {"resultCode": "00", "resultMsg": "NORMAL SERVICE."},
                    "body": {"items": "", "numOfRows": "10", "pageNo": "1", "totalCount": "0"}
                  }
                }
                """);

        HolidayApiClient.HolidayFetchResult result = HolidayApiClient.parse(root, 2026);

        assertEquals(HolidayApiClient.Outcome.OK, result.outcome());
        assertTrue(result.items().isEmpty());
    }

    @Test
    void treatsMissingItemStructureAsBadResponse() throws Exception {
        var root = objectMapper.readTree("""
                {
                  "response": {
                    "header": {"resultCode": "00", "resultMsg": "NORMAL SERVICE."},
                    "body": {"items": {}}
                  }
                }
                """);

        HolidayApiClient.HolidayFetchResult result = HolidayApiClient.parse(root, 2026);

        assertEquals(HolidayApiClient.Outcome.BAD_RESPONSE, result.outcome());
        assertTrue(result.items().isEmpty());
    }

    @Test
    void classifiesMissingApiKeyWithoutThrowing() {
        HolidayApiCredentialService credentialService = mock(HolidayApiCredentialService.class);
        given(credentialService.resolveApiKey()).willReturn(Optional.empty());

        HolidayApiClient.HolidayFetchResult result = new HolidayApiClient(credentialService)
                .fetchByYear(2026);

        assertEquals(HolidayApiClient.Outcome.NO_KEY, result.outcome());
        assertTrue(result.items().isEmpty());
    }

    @Test
    void classifiesCallExceptionWithoutThrowing() {
        HolidayApiCredentialService credentialService = mock(HolidayApiCredentialService.class);

        HolidayApiClient.HolidayFetchResult result = new HolidayApiClient(credentialService)
                .fetchByYear(2026, "잘못된 키");

        assertEquals(HolidayApiClient.Outcome.CALL_FAILED, result.outcome());
        assertTrue(result.items().isEmpty());
    }
}
