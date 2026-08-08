package com.mlsoft.backend.domain.auth.service;

import com.mlsoft.backend.domain.auth.dto.OnboardingRequest;
import com.mlsoft.backend.domain.policy.entity.PolicyConfigKey;
import com.mlsoft.backend.domain.policy.service.LeavePolicyService;
import com.mlsoft.backend.domain.policy.service.PolicyConfigReader;
import com.mlsoft.backend.domain.user.entity.OnboardingStatus;
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
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
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

    @Mock
    private PolicyConfigReader policyConfigReader;

    @InjectMocks
    private AuthService authService;

    @Test
    @DisplayName("1년 미만 신입은 월차 소급 적립 + 기산일은 입사일")
    void completeOnboarding_underOneYear_accruesMonthlyDays() {
        // given: 입사 5개월차 신입
        User user = givenUser(1L);
        givenAutoApproveWindowOpen();
        LocalDate hireDate = LocalDate.now().minusMonths(5);
        given(leavePolicyService.calculateRetroactiveMonthlyDays(hireDate, LocalDate.now()))
                .willReturn(new BigDecimal("5.0"));
        given(policyConfigReader.getInt(PolicyConfigKey.MONTHLY_LEAVE_MAX_DAYS)).willReturn(11);

        // when
        authService.completeOnboarding(1L, new OnboardingRequest(BIRTH_DAY, hireDate));

        // then: base_days 5.0 + 기산일 = 입사일 (1주년 도래 시 스케줄러가 정책 연차로 전환)
        assertEquals(0, new BigDecimal("5.0").compareTo(user.getBaseDays()));
        assertEquals(hireDate, user.getLastResetDate());
        assertTrue(user.isOnboardingCompleted());
        // 소급으로 5회분을 줬다는 기록 — 이게 없으면 스케줄러가 같은 5개월분을 한 번 더 적립한다 (docs/09 §2)
        assertEquals(5, user.getMonthlyGrantedCount());
    }

    @Test
    @DisplayName("온보딩 소급분도 월차 상한 설정을 따른다 — 상한 5면 8개월차도 5일만 받는다")
    void completeOnboarding_소급분도상한적용() {
        // 여기만 법정 11일로 고정돼 있으면 관리자가 상한을 낮춰도 온보딩이 초과 지급하고,
        // 스케줄러는 이미 상한 이상이라 되돌리지 않는다
        User user = givenUser(7L);
        givenAutoApproveWindowOpen();
        LocalDate hireDate = LocalDate.now().minusMonths(8);
        given(leavePolicyService.calculateRetroactiveMonthlyDays(hireDate, LocalDate.now()))
                .willReturn(new BigDecimal("8.0"));
        given(policyConfigReader.getInt(PolicyConfigKey.MONTHLY_LEAVE_MAX_DAYS)).willReturn(5);

        authService.completeOnboarding(7L, new OnboardingRequest(BIRTH_DAY, hireDate));

        assertEquals(0, new BigDecimal("5.0").compareTo(user.getBaseDays()));
        assertEquals(5, user.getMonthlyGrantedCount());
    }

    @Test
    @DisplayName("만 1년 당일 — 1년차(15일) 정책 + 기산일은 오늘(1주년 기념일)")
    void completeOnboarding_exactlyOneYear_appliesFirstYearPolicy() {
        // given: 오늘이 정확히 1주년 — 년차 = 만 근속년수 = 1
        User user = givenUser(2L);
        givenAutoApproveWindowOpen();
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
        givenAutoApproveWindowOpen();
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
        givenAutoApproveWindowOpen();
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

    // ============ 입사일 자가 신고 차단 (리뷰 S-1) ============

    @Test
    @DisplayName("자동 승인 범위 밖 입사일은 승인 대기 — 연차가 부여되지 않는다")
    void completeOnboarding_범위밖입사일_승인대기() {
        // S-1의 실제 악용 시나리오. 예전에는 이 요청 하나로 MIN(15+35/2, 25) = 25일이 부여됐고,
        // 완료로 판정된 뒤에는 재입력 경로가 없어 관리자가 DB를 직접 고쳐야 했다
        User user = givenUser(10L);
        given(policyConfigReader.getInt(PolicyConfigKey.ONBOARDING_AUTO_APPROVE_DAYS)).willReturn(90);

        authService.completeOnboarding(10L, new OnboardingRequest(BIRTH_DAY, LocalDate.of(1990, 1, 1)));

        assertEquals(OnboardingStatus.PENDING_APPROVAL, user.getOnboardingStatus());
        assertEquals(0, BigDecimal.ZERO.compareTo(user.getBaseDays()));
        assertFalse(user.isOnboardingCompleted()); // 인터셉터가 계속 막는다
        assertNull(user.getLastResetDate());       // 기산일도 세우지 않는다 — 스케줄러 대상 밖
    }

    @Test
    @DisplayName("자동 승인 범위 경계 — 정확히 N일 전 입사일은 즉시 확정된다")
    void completeOnboarding_범위경계_즉시확정() {
        // 경계를 배타로 잘못 쓰면 정상 신입이 승인 대기에 걸린다
        User user = givenUser(11L);
        LocalDate hireDate = LocalDate.now().minusDays(90);
        given(policyConfigReader.getInt(PolicyConfigKey.ONBOARDING_AUTO_APPROVE_DAYS)).willReturn(90);
        given(policyConfigReader.getInt(PolicyConfigKey.MONTHLY_LEAVE_MAX_DAYS)).willReturn(11);
        given(leavePolicyService.calculateRetroactiveMonthlyDays(hireDate, LocalDate.now()))
                .willReturn(new BigDecimal("2.0"));

        authService.completeOnboarding(11L, new OnboardingRequest(BIRTH_DAY, hireDate));

        assertEquals(OnboardingStatus.COMPLETED, user.getOnboardingStatus());
        assertEquals(0, new BigDecimal("2.0").compareTo(user.getBaseDays()));
    }

    @Test
    @DisplayName("승인 대기 중 재신청은 ALREADY_ONBOARDED — 값을 바꿔 치고 승인받을 수 없다")
    void completeOnboarding_승인대기중재신청_거부() {
        User user = givenUser(12L);
        user.requestOnboardingApproval(LocalDate.of(1990, 1, 1), BIRTH_DAY);

        BusinessException exception = assertThrows(BusinessException.class,
                () -> authService.completeOnboarding(12L, new OnboardingRequest(BIRTH_DAY, LocalDate.now())));
        assertEquals(ErrorCode.ALREADY_ONBOARDED, exception.getErrorCode());
    }

    // 온보딩 전 신규 가입 유저 목킹 헬퍼
    private User givenUser(Long userId) {
        User user = User.create("테스트", "test@mlsoft.com", Role.EMPLOYEE);
        given(userRepository.findById(userId)).willReturn(Optional.of(user));
        return user;
    }

    /**
     * 자동 승인 창을 10년으로 열어 둔다 — 아래 테스트들의 관심사는 <b>연차 산정</b>이지
     * 승인 분기가 아니다. 분기 자체는 위 S-1 테스트들이 따로 고정한다.
     */
    private void givenAutoApproveWindowOpen() {
        given(policyConfigReader.getInt(PolicyConfigKey.ONBOARDING_AUTO_APPROVE_DAYS)).willReturn(3650);
    }
}

