package com.mlsoft.backend.domain.holiday.controller;

import com.mlsoft.backend.domain.holiday.dto.HolidayResponse;
import com.mlsoft.backend.domain.holiday.service.HolidayService;
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
import java.util.Map;

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
     * 이미 있는 날짜는 건너뛰므로 여러 번 눌러도 안전하다.
     */
    @PostMapping("/sync")
    @PreAuthorize("hasRole('SYSTEM_ADMIN')")
    public ResponseEntity<CommonResponse<Map<String, Integer>>> sync(
            @RequestParam(required = false) Integer year
    ) {
        int target = (year != null) ? year : LocalDate.now(KST).getYear();
        int saved = holidayService.syncYear(target);
        return ResponseEntity.ok(CommonResponse.success(
                ResponseMessage.HOLIDAY_SYNCED, Map.of("year", target, "saved", saved)));
    }
}
