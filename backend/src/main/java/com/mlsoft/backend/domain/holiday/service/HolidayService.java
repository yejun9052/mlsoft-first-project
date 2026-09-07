package com.mlsoft.backend.domain.holiday.service;

import com.mlsoft.backend.domain.holiday.client.HolidayApiClient;
import com.mlsoft.backend.domain.holiday.dto.HolidayResponse;
import com.mlsoft.backend.domain.holiday.dto.HolidaySyncResult;
import com.mlsoft.backend.domain.holiday.entity.Holiday;
import com.mlsoft.backend.domain.holiday.repository.HolidayRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.util.Collection;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * 공휴일 조회·동기화.
 * <p>
 * 외부 API(data.go.kr)는 <b>연 1회 조회 + DB 캐시</b>로 쓴다 (검증 Y-6). 요청마다 외부를 부르면
 * 그쪽 장애가 곧 우리 장애가 되고, 공휴일은 연중 바뀌지 않는 데이터라 캐시가 자연스럽다.
 * <p>
 * <b>조회 실패는 예외로 만들지 않는다.</b> 공휴일을 못 받아도 캘린더는 그려져야 하고 연차 신청도
 * 되어야 한다. 대신 그 해 공휴일 검증이 느슨해지므로 WARN을 남긴다.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class HolidayService {

    private final HolidayRepository holidayRepository;
    private final HolidayApiClient holidayApiClient;
    private final HolidayPersister holidayPersister;

    /**
     * 연도별 공휴일 (GET /api/holidays?year=).
     * 캐시가 비어 있으면 <b>1회만</b> 외부 조회를 시도한다. 실패해도 빈 목록을 돌려준다.
     */
    public List<HolidayResponse> getByYear(int year) {
        List<Holiday> cached = holidayRepository.findAllByYearOrderByDateAsc(year);
        if (!cached.isEmpty()) {
            return cached.stream().map(HolidayResponse::of).toList();
        }
        // 캐시 미스 — 최초 1회 적재. 외부 조회와 저장의 트랜잭션 경계는 HolidayPersister가 맡는다.
        // 여기서 실패하면 빈 목록이고, 다음 요청에서 다시 시도한다.
        try {
            syncYear(year);
        } catch (RuntimeException e) {
            // 자동 조회는 외부·저장 장애가 캘린더 전체를 막지 않게 조용히 degrade한다.
            log.warn("[공휴일] {}년 자동 동기화 실패 — 캐시 없이 계속합니다 (exception={})",
                    year, e.getClass().getSimpleName());
        }
        return holidayRepository.findAllByYearOrderByDateAsc(year).stream()
                .map(HolidayResponse::of)
                .toList();
    }

    /**
     * 해당 연도를 외부 API에서 받아 저장한다 — 조회 결과 구분과 적재 건수를 돌려준다.
     * <p>
     * 외부 조회는 트랜잭션 밖에서 실행하고, 성공한 결과만 별도 빈의 저장 메서드로 넘긴다.
     * 따라서 외부 API가 실패해도 기존 캐시 행은 삭제되지 않는다. 저장 메서드를 이 서비스에
     * 두고 자기 호출하면 Spring 프록시를 타지 않으므로 별도 빈으로 분리했다.
     */
    public HolidaySyncResult syncYear(int year) {
        HolidayApiClient.HolidayFetchResult fetched;
        try {
            fetched = holidayApiClient.fetchByYear(year);
        } catch (RuntimeException e) {
            // 클라이언트는 예외를 삼키지만, 대체 구현·목에서도 자동 경계를 지킨다.
            log.warn("[공휴일] {}년 외부 조회 실패 (exception={})",
                    year, e.getClass().getSimpleName());
            return new HolidaySyncResult(HolidayApiClient.Outcome.CALL_FAILED, 0);
        }
        if (fetched == null) {
            log.warn("[공휴일] {}년 외부 조회 결과가 비어 있습니다", year);
            return new HolidaySyncResult(HolidayApiClient.Outcome.BAD_RESPONSE, 0);
        }
        if (fetched.outcome() != HolidayApiClient.Outcome.OK) {
            log.warn("[공휴일] {}년 동기화 실패 — outcome={}", year, fetched.outcome());
            return new HolidaySyncResult(fetched.outcome(), 0);
        }

        List<HolidayApiClient.HolidayItem> yearItems = fetched.items().stream()
                .filter(item -> item.date() != null && item.date().getYear() == year)
                .toList();
        if (yearItems.isEmpty()) {
            log.warn("[공휴일] {}년 정상 응답이지만 공휴일이 0건입니다 — 기존 캐시를 유지합니다", year);
            return new HolidaySyncResult(HolidayApiClient.Outcome.OK, 0);
        }

        try {
            int saved = holidayPersister.replaceYear(year, yearItems);
            log.info("[공휴일] {}년 {}건 적재", year, saved);
            return new HolidaySyncResult(HolidayApiClient.Outcome.OK, saved);
        } catch (DataIntegrityViolationException e) {
            // 동시 동기화로 UNIQUE에 걸린 경우 — 실패한 트랜잭션은 롤백되어 기존 캐시가 보존된다
            log.info("[공휴일] {}년 동시 동기화 감지 — 이미 적재됨", year);
            return new HolidaySyncResult(HolidayApiClient.Outcome.OK, 0);
        }
    }

    /**
     * 주어진 날짜들 중 공휴일인 날짜 (연차 신청 검증용).
     * 날짜 수만큼 개별 조회하지 않고 IN 한 번으로 끝낸다.
     */
    @Transactional(readOnly = true)
    public Set<LocalDate> findHolidayDates(Collection<LocalDate> dates) {
        if (dates == null || dates.isEmpty()) {
            return Set.of();
        }
        return holidayRepository.findAllByDateIn(dates).stream()
                .map(Holiday::getDate)
                .collect(Collectors.toSet());
    }
}
