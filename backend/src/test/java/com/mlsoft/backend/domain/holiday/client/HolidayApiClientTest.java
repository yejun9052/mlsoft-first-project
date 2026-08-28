package com.mlsoft.backend.domain.holiday.client;

import org.junit.jupiter.api.Test;
import tools.jackson.databind.ObjectMapper;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

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

        List<HolidayApiClient.HolidayItem> result = HolidayApiClient.parse(root, 2026);

        assertEquals(1, result.size());
        assertEquals("테스트 공휴일", result.get(0).name());
    }

    @Test
    void treatsAuthenticationOrQuotaErrorAsEmptyResult() throws Exception {
        var root = objectMapper.readTree("""
                {
                  "response": {
                    "header": {"resultCode": "30", "resultMsg": "SERVICE KEY IS NOT REGISTERED ERROR."},
                    "body": {"items": {"item": []}}
                  }
                }
                """);

        assertTrue(HolidayApiClient.parse(root, 2026).isEmpty());
    }
}
