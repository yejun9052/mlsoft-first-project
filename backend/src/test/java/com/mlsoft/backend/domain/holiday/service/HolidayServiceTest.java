package com.mlsoft.backend.domain.holiday.service;

import com.mlsoft.backend.domain.holiday.client.HolidayApiClient;
import com.mlsoft.backend.domain.holiday.dto.HolidayResponse;
import com.mlsoft.backend.domain.holiday.entity.Holiday;
import com.mlsoft.backend.domain.holiday.repository.HolidayRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.dao.DataIntegrityViolationException;

import java.time.LocalDate;
import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

/**
 * 공휴일 서비스 단위 테스트 (리뷰 I-6).
 * <p>
 * 이 도메인에서 지켜야 할 성질은 <b>외부 API 장애가 우리 장애가 되지 않는다</b>는 것이다.
 * 조회 실패·키 부재·중복 적재 어느 경우에도 예외를 던지지 않아야 한다 — 여기서 예외가 나가면
 * 캘린더가 안 그려지거나 사원의 연차 신청이 막힌다.
 */
@ExtendWith(MockitoExtension.class)
class HolidayServiceTest {

    @Mock
    private HolidayRepository holidayRepository;

    @Mock
    private HolidayApiClient holidayApiClient;

    @InjectMocks
    private HolidayService holidayService;

    private static final LocalDate 신정 = LocalDate.of(2026, 1, 1);
    private static final LocalDate 삼일절 = LocalDate.of(2026, 3, 1);

    @Test
    @DisplayName("조회 — 캐시가 있으면 외부 API를 부르지 않는다")
    void getByYear_캐시적중_외부호출없음() {
        given(holidayRepository.findAllByYearOrderByDateAsc(2026))
                .willReturn(List.of(Holiday.create(신정, "1월 1일")));

        List<HolidayResponse> result = holidayService.getByYear(2026);

        assertEquals(1, result.size());
        assertEquals(신정, result.get(0).date());
        verify(holidayApiClient, never()).fetchByYear(anyInt());
    }

    @Test
    @DisplayName("조회 — 캐시가 비면 1회 적재하고 다시 읽는다")
    void getByYear_캐시미스_적재후재조회() {
        given(holidayRepository.findAllByYearOrderByDateAsc(2026))
                .willReturn(List.of())                                        // 최초 조회
                .willReturn(List.of())                                        // syncYear 내부의 기존 날짜 조회
                .willReturn(List.of(Holiday.create(신정, "1월 1일")));         // 적재 후 재조회
        given(holidayApiClient.fetchByYear(2026))
                .willReturn(List.of(new HolidayApiClient.HolidayItem(신정, "1월 1일")));

        List<HolidayResponse> result = holidayService.getByYear(2026);

        assertEquals(1, result.size());
        verify(holidayApiClient).fetchByYear(2026);
    }

    @Test
    @DisplayName("조회 — 외부 API가 빈 목록을 주면 예외 없이 빈 결과 (화면·신청이 막히면 안 된다)")
    void getByYear_API실패_빈결과() {
        given(holidayRepository.findAllByYearOrderByDateAsc(2026)).willReturn(List.of());
        given(holidayApiClient.fetchByYear(2026)).willReturn(List.of());

        List<HolidayResponse> result = holidayService.getByYear(2026);

        assertTrue(result.isEmpty());
        verify(holidayRepository, never()).saveAll(anyList());
    }

    @Test
    @DisplayName("동기화 — 이미 있는 날짜는 건너뛴다 (재실행해도 행이 쌓이지 않음)")
    void syncYear_기존날짜_스킵() {
        given(holidayApiClient.fetchByYear(2026)).willReturn(List.of(
                new HolidayApiClient.HolidayItem(신정, "1월 1일"),
                new HolidayApiClient.HolidayItem(삼일절, "삼일절")));
        given(holidayRepository.findAllByYearOrderByDateAsc(2026))
                .willReturn(List.of(Holiday.create(신정, "1월 1일")));

        int saved = holidayService.syncYear(2026);

        assertEquals(1, saved); // 삼일절만 신규
    }

    @Test
    @DisplayName("동기화 — 전부 이미 있으면 저장을 시도하지 않는다")
    void syncYear_전부존재_저장없음() {
        given(holidayApiClient.fetchByYear(2026))
                .willReturn(List.of(new HolidayApiClient.HolidayItem(신정, "1월 1일")));
        given(holidayRepository.findAllByYearOrderByDateAsc(2026))
                .willReturn(List.of(Holiday.create(신정, "1월 1일")));

        assertEquals(0, holidayService.syncYear(2026));
        verify(holidayRepository, never()).saveAll(anyList());
    }

    @Test
    @DisplayName("동기화 — 동시 실행으로 UNIQUE 충돌이 나도 예외를 밖으로 내보내지 않는다")
    void syncYear_동시실행_충돌흡수() {
        given(holidayApiClient.fetchByYear(2026))
                .willReturn(List.of(new HolidayApiClient.HolidayItem(신정, "1월 1일")));
        given(holidayRepository.findAllByYearOrderByDateAsc(2026)).willReturn(List.of());
        given(holidayRepository.saveAll(anyList()))
                .willThrow(new DataIntegrityViolationException("uk_holidays_date"));

        assertEquals(0, holidayService.syncYear(2026));
    }

    @Test
    @DisplayName("신청 검증용 — 주어진 날짜 중 공휴일만 집합으로 돌려준다")
    void findHolidayDates_공휴일만_반환() {
        LocalDate 평일 = LocalDate.of(2026, 3, 2);
        given(holidayRepository.findAllByDateIn(any()))
                .willReturn(List.of(Holiday.create(삼일절, "삼일절")));

        Set<LocalDate> result = holidayService.findHolidayDates(List.of(삼일절, 평일));

        assertEquals(Set.of(삼일절), result);
    }

    @Test
    @DisplayName("신청 검증용 — 빈 입력은 DB를 조회하지 않는다")
    void findHolidayDates_빈입력_조회없음() {
        assertTrue(holidayService.findHolidayDates(List.of()).isEmpty());
        verify(holidayRepository, never()).findAllByDateIn(any());
    }
}
