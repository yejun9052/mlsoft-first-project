package com.mlsoft.backend.domain.policy.service;

import com.mlsoft.backend.domain.policy.entity.LeavePolicy;
import com.mlsoft.backend.domain.policy.repository.LeavePolicyRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;

/**
 * 연차 정책 계산 전담 (docs/02 3-10, 근로기준법 §60).
 * 온보딩(base_days 초기 산정)과 기산일 리셋 스케줄러가 공유한다.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class LeavePolicyService {

    /** 정책 테이블 최대 년차 — 21년차(25일)를 그 이상 근속에도 적용 */
    private static final int MAX_POLICY_YEARS = 21;
    /** 1년 미만 신입 월차 상한 (갭분석 B-1) */
    private static final int MONTHLY_LEAVE_MAX_DAYS = 11;
    /** 법정 기본 연차 */
    private static final int BASE_ANNUAL_DAYS = 15;
    /** 법정 연차 상한 */
    private static final int MAX_ANNUAL_DAYS = 25;

    private final LeavePolicyRepository leavePolicyRepository;

    /**
     * 근속년수(N년차)별 연차 일수 계산.
     * - **년차(N) = 만 근속년수** (만 1~2년 15일, 만 3년 16일, 만 21년 이상 25일)
     * - DB 정책(leave_policy) 우선 조회, 없거나 비활성이면 법정 공식 MIN(15 + (N-1)/2, 25) fallback
     * - 21년차 초과는 21년차 정책으로 상한 처리
     */
    @Transactional(readOnly = true)
    public BigDecimal calculateAnnualLeaveDays(int yearsOfService) {
        int lookupYears = Math.min(Math.max(yearsOfService, 1), MAX_POLICY_YEARS);
        return leavePolicyRepository.findByYearsOfService(lookupYears)
                .filter(LeavePolicy::isActive)
                .map(LeavePolicy::getAnnualLeaveDays)
                .orElseGet(() -> {
                    log.warn("[LeavePolicy] {}년차 정책 미존재 — 법정 공식으로 계산", lookupYears);
                    return fallbackAnnualLeaveDays(lookupYears);
                });
    }

    /**
     * 1년 미만 신입 월차 소급 계산 (갭분석 B-1).
     * - 입사 후 경과한 "만 개월 수"만큼 적립 (매월 입사일 동일 날짜 기준), 최대 11일
     * - 온보딩 시점 소급분 산정용 — 이후 매월 적립은 스케줄러 담당
     */
    public BigDecimal calculateRetroactiveMonthlyDays(LocalDate hireDate, LocalDate baseDate) {
        long elapsedMonths = ChronoUnit.MONTHS.between(hireDate, baseDate);
        long cappedMonths = Math.min(Math.max(elapsedMonths, 0), MONTHLY_LEAVE_MAX_DAYS);
        return BigDecimal.valueOf(cappedMonths).setScale(1);
    }

    // 법정 공식: MIN(15 + (년차-1)/2, 25) — 1~2년차 15일, 21년차 이상 25일
    private BigDecimal fallbackAnnualLeaveDays(int yearsOfService) {
        int days = Math.min(BASE_ANNUAL_DAYS + (yearsOfService - 1) / 2, MAX_ANNUAL_DAYS);
        return BigDecimal.valueOf(days).setScale(1);
    }
}
