package com.mlsoft.backend.domain.holiday.controller;

import com.mlsoft.backend.domain.holiday.dto.HolidaySyncResponse;
import com.mlsoft.backend.domain.holiday.service.HolidayService;
import com.mlsoft.backend.global.response.CommonResponse;
import com.mlsoft.backend.global.response.ResponseMessage;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.ResponseEntity;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
class HolidayControllerTest {

    @Mock
    private HolidayService holidayService;

    @InjectMocks
    private HolidayController holidayController;

    @Test
    @DisplayName("동기화 응답은 선택 연도와 적재 건수를 함께 반환한다")
    void sync_연도와건수응답() {
        given(holidayService.syncYear(2026)).willReturn(22);

        ResponseEntity<CommonResponse<HolidaySyncResponse>> response = holidayController.sync(2026);

        CommonResponse<HolidaySyncResponse> body = response.getBody();
        assertEquals(ResponseMessage.HOLIDAY_SYNCED, body.message());
        assertEquals(new HolidaySyncResponse(2026, 22), body.data());
        verify(holidayService).syncYear(2026);
    }
}
