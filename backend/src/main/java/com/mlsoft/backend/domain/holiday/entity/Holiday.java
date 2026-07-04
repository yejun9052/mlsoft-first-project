package com.mlsoft.backend.domain.holiday.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.LocalDate;

/**
 * 공휴일 (docs/02 3-9 holidays).
 * 공공데이터포털(data.go.kr) 특일 정보 API로 채운다 — 연 1회 선조회 + DB 캐시 (검증 Y-6).
 * created_at 없는 독립 테이블이므로 BaseTimeEntity 미상속.
 */
@Entity
@Table(name = "holidays")
@Getter
@Builder
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor(access = AccessLevel.PRIVATE)
public class Holiday {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /** 날짜 */
    @Column(nullable = false)
    private LocalDate date;

    /** 공휴일 이름 */
    @Column(nullable = false)
    private String name;

    /** 년도 */
    @Column(nullable = false)
    private int year;

    /** 공휴일 생성 (API 응답 저장용) */
    public static Holiday create(LocalDate date, String name) {
        return Holiday.builder()
                .date(date)
                .name(name)
                .year(date.getYear())
                .build();
    }
}
