package com.mlsoft.backend.domain.holiday.controller;

import com.mlsoft.backend.domain.holiday.dto.HolidayResponse;
import com.mlsoft.backend.domain.holiday.dto.HolidaySyncResponse;
import com.mlsoft.backend.domain.holiday.dto.HolidaySyncResult;
import com.mlsoft.backend.domain.holiday.client.HolidayApiClient;
import com.mlsoft.backend.domain.holiday.service.HolidayService;
import com.mlsoft.backend.global.exception.BusinessException;
import com.mlsoft.backend.global.exception.ErrorCode;
import com.mlsoft.backend.global.response.CommonResponse;
import com.mlsoft.backend.global.response.ResponseMessage;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDate;
import java.time.ZoneId;
import java.util.List;

/**
 * 공휴일 API (docs/03 공휴일).
 * 조회는 전체 공개(캘린더·대시보드가 쓴다), 강제 동기화는 SYSTEM_ADMIN만.
 */
@RestController
@RequestMapping("/api/holidays")
@RequiredArgsConstructor
public class HolidayController {

    private static final ZoneId KST = ZoneId.of("Asia/Seoul");

    private final HolidayService holidayService;

    /** 연도별 공휴일 — year 생략 시 올해(KST 기준) */
    @GetMapping
    public ResponseEntity<CommonResponse<List<HolidayResponse>>> getHolidays(
            @RequestParam(required = false) Integer year
    ) {
        int target = (year != null) ? year : LocalDate.now(KST).getYear();
        List<HolidayResponse> response = holidayService.getByYear(target);
        return ResponseEntity.ok(CommonResponse.success(ResponseMessage.HOLIDAY_FETCHED, response));
    }

    /**
     * 강제 재동기화 (SA) — 외부 API가 늦게 갱신됐거나 대체공휴일이 추가로 지정된 경우.
     * 해당 연도의 캐시를 외부 응답으로 교체하므로 여러 번 눌러도 최신 결과로 수렴한다.
     */
    @PostMapping("/sync")
    @PreAuthorize("hasRole('SYSTEM_ADMIN')")
    public ResponseEntity<CommonResponse<HolidaySyncResponse>> sync(
            @RequestParam(required = false) Integer year
    ) {
        int target = (year != null) ? year : LocalDate.now(KST).getYear();
        HolidaySyncResult result = holidayService.syncYear(target);
        throwIfFailed(result == null ? null : result.outcome());
        return ResponseEntity.ok(CommonResponse.success(
                ResponseMessage.HOLIDAY_SYNCED,
                new HolidaySyncResponse(target, result.count())));
    }

    private void throwIfFailed(HolidayApiClient.Outcome outcome) {
        if (outcome == null) {
            throw new BusinessException(ErrorCode.HOLIDAY_API_BAD_RESPONSE);
        }
        if (outcome == HolidayApiClient.Outcome.OK) {
            return;
        }
        ErrorCode errorCode = switch (outcome) {
            case NO_KEY -> ErrorCode.HOLIDAY_API_KEY_NOT_CONFIGURED;
            case CALL_FAILED -> ErrorCode.HOLIDAY_API_CALL_FAILED;
            case BAD_RESPONSE -> ErrorCode.HOLIDAY_API_BAD_RESPONSE;
            case OK -> ErrorCode.HOLIDAY_API_BAD_RESPONSE;
        };
        throw new BusinessException(errorCode);
    }
}
