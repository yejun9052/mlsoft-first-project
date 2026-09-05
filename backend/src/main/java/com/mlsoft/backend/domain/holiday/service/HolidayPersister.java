package com.mlsoft.backend.domain.holiday.service;

import com.mlsoft.backend.domain.holiday.client.HolidayApiClient;
import com.mlsoft.backend.domain.holiday.entity.Holiday;
import com.mlsoft.backend.domain.holiday.repository.HolidayRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

/**
 * 공휴일 캐시 저장 경계.
 *
 * <p>외부 API 호출을 포함하는 {@link HolidayService}와 분리해, 이 빈의 메서드만
 * 짧은 데이터베이스 트랜잭션으로 실행한다. 같은 서비스의 자기 호출은 Spring
 * 트랜잭션 프록시를 통과하지 않으므로 별도 빈으로 두었다.</p>
 */
@Service
@RequiredArgsConstructor
public class HolidayPersister {

    private final HolidayRepository holidayRepository;

    /**
     * 한 연도의 캐시를 새 응답으로 교체한다.
     *
     * <p>삭제와 삽입은 하나의 트랜잭션이라 삽입에 실패하면 삭제도 함께 롤백된다.</p>
     */
    @Transactional
    public int replaceYear(int year, List<HolidayApiClient.HolidayItem> items) {
        holidayRepository.deleteAllByYear(year);
        List<Holiday> holidays = items.stream()
                .map(item -> Holiday.create(item.date(), item.name()))
                .toList();
        holidayRepository.saveAll(holidays);
        return holidays.size();
    }
}
