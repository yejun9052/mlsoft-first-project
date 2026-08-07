package com.mlsoft.backend.domain.holiday.service;

import com.mlsoft.backend.domain.holiday.client.HolidayApiClient;
import com.mlsoft.backend.domain.holiday.dto.HolidayResponse;
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

    /**
     * 연도별 공휴일 (GET /api/holidays?year=).
     * 캐시가 비어 있으면 <b>1회만</b> 외부 조회를 시도한다. 실패해도 빈 목록을 돌려준다.
     */
    @Transactional
    public List<HolidayResponse> getByYear(int year) {
        List<Holiday> cached = holidayRepository.findAllByYearOrderByDateAsc(year);
        if (!cached.isEmpty()) {
            return cached.stream().map(HolidayResponse::of).toList();
        }
        // 캐시 미스 — 최초 1회 적재. 여기서 실패하면 빈 목록이고, 다음 요청에서 다시 시도한다.
        syncYear(year);
        return holidayRepository.findAllByYearOrderByDateAsc(year).stream()
                .map(HolidayResponse::of)
                .toList();
    }

    /**
     * 해당 연도를 외부 API에서 받아 저장한다 — 저장된 건수를 돌려준다.
     * <p>
     * 이미 있는 날짜는 건너뛴다. 재동기화해도 행이 쌓이지 않고, 동시에 두 번 호출돼도
     * DB UNIQUE(uk_holidays_date)가 마지막 방어선이 된다 (리뷰 D-3).
     */
    @Transactional
    public int syncYear(int year) {
        List<HolidayApiClient.HolidayItem> fetched = holidayApiClient.fetchByYear(year);
        if (fetched.isEmpty()) {
            log.warn("[공휴일] {}년 동기화 결과가 0건입니다 — 그 해 공휴일 검증이 동작하지 않습니다", year);
            return 0;
        }

        Set<LocalDate> existing = holidayRepository.findAllByYearOrderByDateAsc(year).stream()
                .map(Holiday::getDate)
                .collect(Collectors.toSet());

        List<Holiday> toSave = fetched.stream()
                .filter(item -> !existing.contains(item.date()))
                .map(item -> Holiday.create(item.date(), item.name()))
                .toList();

        if (toSave.isEmpty()) {
            return 0;
        }
        try {
            holidayRepository.saveAll(toSave);
        } catch (DataIntegrityViolationException e) {
            // 동시 동기화로 UNIQUE에 걸린 경우 — 다른 쪽이 이미 넣었다는 뜻이라 실패가 아니다
            log.info("[공휴일] {}년 동시 동기화 감지 — 이미 적재됨", year);
            return 0;
        }
        log.info("[공휴일] {}년 {}건 적재", year, toSave.size());
        return toSave.size();
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
