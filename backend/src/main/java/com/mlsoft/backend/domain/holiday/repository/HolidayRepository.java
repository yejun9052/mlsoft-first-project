package com.mlsoft.backend.domain.holiday.repository;

import com.mlsoft.backend.domain.holiday.entity.Holiday;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

/**
 * 공휴일 저장소.
 */
public interface HolidayRepository extends JpaRepository<Holiday, Long> {

    /** 연도별 공휴일 조회 (캘린더·API 캐시 판별) */
    List<Holiday> findAllByYear(int year);
}
