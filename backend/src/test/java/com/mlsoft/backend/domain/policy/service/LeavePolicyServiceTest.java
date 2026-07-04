package com.mlsoft.backend.domain.policy.service;

import com.mlsoft.backend.domain.policy.entity.LeavePolicy;
import com.mlsoft.backend.domain.policy.repository.LeavePolicyRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.verify;

/**
 * 연차 정책 계산 단위 테스트 (근로기준법 §60 경계값).
 */
@ExtendWith(MockitoExtension.class)
class LeavePolicyServiceTest {

    @Mock
    private LeavePolicyRepository leavePolicyRepository;

    @InjectMocks
    private LeavePolicyService leavePolicyService;

    @Test
    @DisplayName("DB 정책이 있으면 그 값을 사용한다")
    void calculateAnnualLeaveDays_usesDbPolicy() {
        // given: 3년차 정책 16.0일
        given(leavePolicyRepository.findByYearsOfService(3))
                .willReturn(Optional.of(LeavePolicy.create(3, new BigDecimal("16.0"), "3년차 16일")));

        // when
        BigDecimal days = leavePolicyService.calculateAnnualLeaveDays(3);

        // then
        assertBigDecimalEquals("16.0", days);
    }

    @Test
    @DisplayName("DB 정책이 없으면 법정 공식 fallback — 1년차 15 / 3년차 16 / 21년차 25")
    void calculateAnnualLeaveDays_fallbackFormulaBoundaries() {
        // given: 정책 미존재
        given(leavePolicyRepository.findByYearsOfService(anyInt())).willReturn(Optional.empty());

        // then: MIN(15 + (년차-1)/2, 25)
        assertBigDecimalEquals("15.0", leavePolicyService.calculateAnnualLeaveDays(1));
        assertBigDecimalEquals("16.0", leavePolicyService.calculateAnnualLeaveDays(3));
        assertBigDecimalEquals("25.0", leavePolicyService.calculateAnnualLeaveDays(21));
    }

    @Test
    @DisplayName("21년차 초과는 21년차 정책으로 상한 조회한다")
    void calculateAnnualLeaveDays_capsLookupAtMaxPolicyYears() {
        // given
        given(leavePolicyRepository.findByYearsOfService(21)).willReturn(Optional.empty());

        // when: 30년차 요청 → 21년차로 조회 + 상한 25일
        BigDecimal days = leavePolicyService.calculateAnnualLeaveDays(30);

        // then
        verify(leavePolicyRepository).findByYearsOfService(21);
        assertBigDecimalEquals("25.0", days);
    }

    @Test
    @DisplayName("1년 미만 신입 월차 소급 — 경과 개월 수만큼, 최대 11일")
    void calculateRetroactiveMonthlyDays() {
        // 입사 5개월 경과 → 5일 (매월 입사일 동일 날짜 기준)
        assertBigDecimalEquals("5.0", leavePolicyService.calculateRetroactiveMonthlyDays(
                LocalDate.of(2026, 1, 15), LocalDate.of(2026, 7, 4)));

        // 입사 당일 → 0일
        assertBigDecimalEquals("0.0", leavePolicyService.calculateRetroactiveMonthlyDays(
                LocalDate.of(2026, 7, 4), LocalDate.of(2026, 7, 4)));

        // 11개월 경과 → 상한 11일 유지
        assertBigDecimalEquals("11.0", leavePolicyService.calculateRetroactiveMonthlyDays(
                LocalDate.of(2025, 8, 1), LocalDate.of(2026, 7, 4)));
    }

    // BigDecimal 비교는 compareTo (docs/04 — 스케일 차이로 인한 equals 오판 방지)
    private void assertBigDecimalEquals(String expected, BigDecimal actual) {
        assertEquals(0, new BigDecimal(expected).compareTo(actual),
                () -> "expected=" + expected + ", actual=" + actual);
    }
}
