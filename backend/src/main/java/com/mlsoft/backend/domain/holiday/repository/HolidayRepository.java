package com.mlsoft.backend.domain.holiday.repository;

import com.mlsoft.backend.domain.holiday.entity.Holiday;
import org.springframework.data.jpa.repository.JpaRepository;

import java.time.LocalDate;
import java.util.Collection;
import java.util.List;

/**
 * 공휴일 저장소.
 */
public interface HolidayRepository extends JpaRepository<Holiday, Long> {

    /** 연도별 공휴일 조회 (캘린더·API 캐시 판별) — 날짜 오름차순 */
    List<Holiday> findAllByYearOrderByDateAsc(int year);

    /** 해당 연도가 이미 적재됐는지 (동기화 스킵 판정) */
    boolean existsByYear(int year);

    /**
     * 주어진 날짜들 중 공휴일인 것 (연차 신청 검증).
     * 신청 날짜 수만큼 개별 조회하지 않도록 IN 한 번으로 끝낸다.
     */
    List<Holiday> findAllByDateIn(Collection<LocalDate> dates);

    /** 연도 전체 삭제 — 재동기화 시 갈아끼우기 위해 */
    void deleteAllByYear(int year);

    /** 이미 저장된 날짜인지 (중복 적재 방지 — DB UNIQUE의 앞단 검사) */
    boolean existsByDate(LocalDate date);
}
