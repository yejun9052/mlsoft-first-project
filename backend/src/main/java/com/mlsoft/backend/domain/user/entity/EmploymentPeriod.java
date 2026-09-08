package com.mlsoft.backend.domain.user.entity;

import com.mlsoft.backend.global.entity.BaseTimeEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.LocalDate;

/**
 * 종료된 근속 구간 (설계-초안/재입사자-처리-설계-2026-09-07 §5).
 *
 * <p>이 행은 재입사 처리 시점에만 생성한다. 지나간 근속만 이 테이블에 남기고 현재 근속은 {@link User}의
 * {@code hireDate}·{@code retiredAt}에 둔다. 같은 사실을 두 곳에 저장하면 현재 구간과 이력 구간이
 * 어긋나는 이중 출처 문제가 생기므로, 기존 사원은 재입사 전까지 행이 0개이고 데이터 backfill도 필요 없다.
 */
@Entity
@Table(name = "employment_periods",
        uniqueConstraints = @UniqueConstraint(name = "uk_employment_periods_user_seq",
                columnNames = {"user_id", "seq"}))
@Getter
@Builder
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor(access = AccessLevel.PRIVATE)
public class EmploymentPeriod extends BaseTimeEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /** 해당 사원 */
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

    /** 근속 구간 번호 — 사원별 1부터 증가 */
    @Column(nullable = false)
    private int seq;

    /** 해당 구간의 입사일 */
    @Column(nullable = false)
    private LocalDate hireDate;

    /** 해당 구간의 퇴직일 */
    @Column(nullable = false)
    private LocalDate retiredAt;

    /** 종료된 근속 구간 생성 */
    public static EmploymentPeriod create(User user, int seq, LocalDate hireDate, LocalDate retiredAt) {
        return EmploymentPeriod.builder()
                .user(user)
                .seq(seq)
                .hireDate(hireDate)
                .retiredAt(retiredAt)
                .build();
    }
}
