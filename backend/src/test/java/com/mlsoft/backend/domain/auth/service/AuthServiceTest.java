package com.mlsoft.backend.domain.auth.service;

import com.mlsoft.backend.domain.auth.dto.OnboardingRequest;
import com.mlsoft.backend.domain.policy.service.LeavePolicyService;
import com.mlsoft.backend.domain.user.entity.Role;
import com.mlsoft.backend.domain.user.entity.User;
import com.mlsoft.backend.domain.user.repository.UserRepository;
import com.mlsoft.backend.global.exception.BusinessException;
import com.mlsoft.backend.global.exception.ErrorCode;
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
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.BDDMockito.given;

/**
 * 온보딩 base_days 자동 계산 분기 단위 테스트 (docs/01 2-1, 갭분석 B-1·C-1).
 */
@ExtendWith(MockitoExtension.class)
class AuthServiceTest {

    private static final LocalDate BIRTH_DAY = LocalDate.of(1995, 4, 1);

    @Mock
    private UserRepository userRepository;

    @Mock
    private LeavePolicyService leavePolicyService;

    @InjectMocks
    private AuthService authService;

    @Test
    @DisplayName("1년 미만 신입은 월차 소급 적립 + 기산일은 입사일")
    void completeOnboarding_underOneYear_accruesMonthlyDays() {
        // given: 입사 5개월차 신입
        User user = givenUser(1L);
        LocalDate hireDate = LocalDate.now().minusMonths(5);
        given(leavePolicyService.calculateRetroactiveMonthlyDays(hireDate, LocalDate.now()))
                .willReturn(new BigDecimal("5.0"));

        // when
        authService.completeOnboarding(1L, new OnboardingRequest(BIRTH_DAY, hireDate));

        // then: base_days 5.0 + 기산일 = 입사일 (1주년 도래 시 스케줄러가 정책 연차로 전환)
        assertEquals(0, new BigDecimal("5.0").compareTo(user.getBaseDays()));
        assertEquals(hireDate, user.getLastResetDate());
        assertTrue(user.isOnboardingCompleted());
    }

    @Test
    @DisplayName("만 1년 당일 — 1년차(15일) 정책 + 기산일은 오늘(1주년 기념일)")
    void completeOnboarding_exactlyOneYear_appliesFirstYearPolicy() {
        // given: 오늘이 정확히 1주년 — 년차 = 만 근속년수 = 1
        User user = givenUser(2L);
        LocalDate hireDate = LocalDate.now().minusYears(1);
        given(leavePolicyService.calculateAnnualLeaveDays(1)).willReturn(new BigDecimal("15.0"));

        // when
        authService.completeOnboarding(2L, new OnboardingRequest(BIRTH_DAY, hireDate));

        // then: 기산일 = 1주년 기념일(오늘) — 스케줄러 즉시 재리셋 방지 (검증 Y-1)
        assertEquals(0, new BigDecimal("15.0").compareTo(user.getBaseDays()));
        assertEquals(LocalDate.now(), user.getLastResetDate());
    }

    @Test
    @DisplayName("만 2년 재직자 — 2년차(15일) 정책")
    void completeOnboarding_twoYears_appliesSecondYearPolicy() {
        // given: 만 2년 재직 — 년차 = 2 (만 1~2년은 법정 15일)
        User user = givenUser(3L);
        LocalDate hireDate = LocalDate.now().minusYears(2);
        given(leavePolicyService.calculateAnnualLeaveDays(2)).willReturn(new BigDecimal("15.0"));

        // when
        authService.completeOnboarding(3L, new OnboardingRequest(BIRTH_DAY, hireDate));

        // then
        assertEquals(0, new BigDecimal("15.0").compareTo(user.getBaseDays()));
        assertEquals(hireDate.plusYears(2), user.getLastResetDate());
    }

    @Test
    @DisplayName("만 3년 재직자 — 3년차(16일) 정책 + 기산일은 최근 입사기념일")
    void completeOnboarding_threeYears_appliesThirdYearPolicy() {
        // given: 만 3년 재직 — 년차 = 3 (법정 16일 최초 가산 구간)
        User user = givenUser(4L);
        LocalDate hireDate = LocalDate.now().minusYears(3);
        given(leavePolicyService.calculateAnnualLeaveDays(3)).willReturn(new BigDecimal("16.0"));

        // when
        authService.completeOnboarding(4L, new OnboardingRequest(BIRTH_DAY, hireDate));

        // then
        assertEquals(0, new BigDecimal("16.0").compareTo(user.getBaseDays()));
        assertEquals(hireDate.plusYears(3), user.getLastResetDate());
    }

    @Test
    @DisplayName("이미 온보딩된 유저는 ALREADY_ONBOARDED")
    void completeOnboarding_alreadyOnboarded_throws() {
        // given: hire_date가 이미 존재
        User user = givenUser(5L);
        user.completeOnboarding(LocalDate.now().minusYears(1), BIRTH_DAY);

        // when & then
        BusinessException exception = assertThrows(BusinessException.class,
                () -> authService.completeOnboarding(5L, new OnboardingRequest(BIRTH_DAY, LocalDate.now())));
        assertEquals(ErrorCode.ALREADY_ONBOARDED, exception.getErrorCode());
    }

    // 온보딩 전 신규 가입 유저 목킹 헬퍼
    private User givenUser(Long userId) {
        User user = User.create("테스트", "test@mlsoft.com", Role.EMPLOYEE);
        given(userRepository.findById(userId)).willReturn(Optional.of(user));
        return user;
    }
}
