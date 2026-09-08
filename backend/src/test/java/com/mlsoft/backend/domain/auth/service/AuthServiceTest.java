package com.mlsoft.backend.domain.auth.service;

import com.mlsoft.backend.domain.auth.dto.OnboardingRequest;
import com.mlsoft.backend.domain.auth.dto.UserMeResponse;
import com.mlsoft.backend.domain.email.service.EmailNotificationPublisher;
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
import java.time.ZoneId;
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

    /** 서비스와 같은 기준 시간대 — 날짜 경계 테스트가 서버 TZ에 흔들리지 않게 고정한다 (리뷰 I-7) */
    private static final ZoneId KST = ZoneId.of("Asia/Seoul");

    private static final LocalDate BIRTH_DAY = LocalDate.of(1995, 4, 1);

    @Mock
    private UserRepository userRepository;

    @Mock
    private LeavePolicyService leavePolicyService;

    @Mock
    private PolicyConfigReader policyConfigReader;

    @Mock
    private EmailNotificationPublisher emailNotificationPublisher;

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
    @DisplayName("온보딩 확정 — 직급을 저장하고 내 정보 응답에도 싣는다")
    void completeOnboarding_jobGrade_savedAndReturned() {
        User user = givenUser(6L);
        givenAutoApproveWindowOpen();
        LocalDate hireDate = LocalDate.now().minusMonths(2);
        given(leavePolicyService.calculateRetroactiveMonthlyDays(hireDate, LocalDate.now()))
                .willReturn(new BigDecimal("2.0"));
        given(policyConfigReader.getInt(PolicyConfigKey.MONTHLY_LEAVE_MAX_DAYS)).willReturn(11);

        UserMeResponse response = authService.completeOnboarding(
                6L, new OnboardingRequest(BIRTH_DAY, hireDate, "  선임연구원  "));

        assertEquals("선임연구원", user.getJobGrade());
        assertEquals("선임연구원", response.jobGrade());
    }

    @Test
    @DisplayName("온보딩 확정 — 공백 직급은 null로 정규화되고 응답에도 null이다")
    void completeOnboarding_blankJobGrade_normalizedToNull() {
        User user = givenUser(8L);
        givenAutoApproveWindowOpen();
        LocalDate hireDate = LocalDate.now();
        given(leavePolicyService.calculateRetroactiveMonthlyDays(hireDate, hireDate))
                .willReturn(BigDecimal.ZERO);
        given(policyConfigReader.getInt(PolicyConfigKey.MONTHLY_LEAVE_MAX_DAYS)).willReturn(11);

        UserMeResponse response = authService.completeOnboarding(
                8L, new OnboardingRequest(BIRTH_DAY, hireDate, "   "));

        assertNull(user.getJobGrade());
        assertNull(response.jobGrade());
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

    @Test
    @DisplayName("미래 입사일은 FUTURE_HIRE_DATE — 기산일이 미래가 되면 스케줄러 대상에서 빠진다 (리뷰 I-7)")
    void completeOnboarding_미래입사일_거부() {
        // DTO의 @PastOrPresent에 맡기면 JVM 기본 시간대로 판정된다 — 운영 컨테이너가 UTC면
        // KST 00~09시 사이에 "오늘 입사"가 미래로 오판돼 정상 신입이 400을 받았다.
        // 그래서 경계 판정을 서비스로 옮겼고, 여기서는 KST 기준이다.
        User user = givenUser(13L);

        BusinessException exception = assertThrows(BusinessException.class, () -> authService
                .completeOnboarding(13L, new OnboardingRequest(BIRTH_DAY, LocalDate.now(KST).plusDays(1))));

        assertEquals(ErrorCode.FUTURE_HIRE_DATE, exception.getErrorCode());
        assertEquals(OnboardingStatus.NOT_STARTED, user.getOnboardingStatus());
        assertNull(user.getLastResetDate());
    }

    @Test
    @DisplayName("오늘 입사는 통과한다 — 미래 차단의 경계")
    void completeOnboarding_오늘입사_통과() {
        User user = givenUser(14L);
        LocalDate today = LocalDate.now(KST);
        given(policyConfigReader.getInt(PolicyConfigKey.ONBOARDING_AUTO_APPROVE_DAYS)).willReturn(90);
        given(policyConfigReader.getInt(PolicyConfigKey.MONTHLY_LEAVE_MAX_DAYS)).willReturn(11);
        given(leavePolicyService.calculateRetroactiveMonthlyDays(today, today)).willReturn(BigDecimal.ZERO);

        authService.completeOnboarding(14L, new OnboardingRequest(BIRTH_DAY, today));

        assertEquals(OnboardingStatus.COMPLETED, user.getOnboardingStatus());
        assertEquals(today, user.getLastResetDate());
    }

    // ============ 입사일 자가 신고 차단 (리뷰 S-1) ============

    @Test
    @DisplayName("자동 승인 범위 밖 입사일은 승인 대기 — 연차가 부여되지 않는다")
    void completeOnboarding_범위밖입사일_승인대기() {
        // S-1의 실제 악용 시나리오. 예전에는 이 요청 하나로 MIN(15+35/2, 25) = 25일이 부여됐고,
        // 완료로 판정된 뒤에는 재입력 경로가 없어 관리자가 DB를 직접 고쳐야 했다
        User user = givenUser(10L);
        given(policyConfigReader.getInt(PolicyConfigKey.ONBOARDING_AUTO_APPROVE_DAYS)).willReturn(90);

        authService.completeOnboarding(10L,
                new OnboardingRequest(BIRTH_DAY, LocalDate.of(1990, 1, 1), "연구원"));

        assertEquals(OnboardingStatus.PENDING_APPROVAL, user.getOnboardingStatus());
        assertEquals("연구원", user.getJobGrade());
        assertEquals(0, BigDecimal.ZERO.compareTo(user.getBaseDays()));
        assertFalse(user.isOnboardingCompleted()); // 인터셉터가 계속 막는다
        assertNull(user.getLastResetDate());       // 기산일도 세우지 않는다 — 스케줄러 대상 밖
        org.mockito.Mockito.verify(emailNotificationPublisher).publishOnboardingPending(user);
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

    @Test
    @DisplayName("온보딩 미시작 사원이 수정 요청하면 ONBOARDING_NOT_PENDING")
    void reviseOnboarding_미시작_대기상태아님예외() {
        User user = givenUser(20L);

        BusinessException exception = assertThrows(BusinessException.class,
                () -> authService.reviseOnboarding(
                        20L,
                        new OnboardingRequest(BIRTH_DAY, LocalDate.now(KST).minusDays(10))));

        // reviseOnboarding의 PENDING_APPROVAL 상태 검사 줄을 지우면 이 테스트가 깨진다.
        assertEquals(ErrorCode.ONBOARDING_NOT_PENDING, exception.getErrorCode());
        assertEquals(OnboardingStatus.NOT_STARTED, user.getOnboardingStatus());
        assertFalse(user.isOnboardingRevised());
    }

    @Test
    @DisplayName("입사일 수정 설정이 꺼져 있으면 ONBOARDING_REVISION_DISABLED")
    void reviseOnboarding_설정꺼짐_수정비활성화예외() {
        User user = givenUser(21L);
        user.requestOnboardingApproval(LocalDate.of(1990, 1, 1), BIRTH_DAY);
        given(policyConfigReader.getBoolean(PolicyConfigKey.ONBOARDING_REVISION_ENABLED))
                .willReturn(false);

        BusinessException exception = assertThrows(BusinessException.class,
                () -> authService.reviseOnboarding(
                        21L,
                        new OnboardingRequest(BIRTH_DAY, LocalDate.now(KST).minusDays(10))));

        // ONBOARDING_REVISION_ENABLED 설정 검사 줄을 지우면 이 테스트가 깨진다.
        assertEquals(ErrorCode.ONBOARDING_REVISION_DISABLED, exception.getErrorCode());
        assertEquals(LocalDate.of(1990, 1, 1), user.getHireDate());
        assertFalse(user.isOnboardingRevised());
    }

    @Test
    @DisplayName("수정권을 이미 사용한 사원은 ONBOARDING_REVISION_EXHAUSTED")
    void reviseOnboarding_수정권사용완료_수정권소진예외() {
        User user = givenUser(22L);
        user.requestOnboardingApproval(LocalDate.of(1990, 1, 1), BIRTH_DAY);
        user.markOnboardingRevised();
        given(policyConfigReader.getBoolean(PolicyConfigKey.ONBOARDING_REVISION_ENABLED))
                .willReturn(true);

        BusinessException exception = assertThrows(BusinessException.class,
                () -> authService.reviseOnboarding(
                        22L,
                        new OnboardingRequest(BIRTH_DAY, LocalDate.now(KST).minusDays(10))));

        // onboardingRevised 수정권 검사 줄을 지우면 이 테스트가 깨진다.
        assertEquals(ErrorCode.ONBOARDING_REVISION_EXHAUSTED, exception.getErrorCode());
        assertEquals(LocalDate.of(1990, 1, 1), user.getHireDate());
    }

    @Test
    @DisplayName("미래 입사일 수정은 거부되고 수정권은 소진되지 않는다")
    void reviseOnboarding_미래입사일_수정권유지() {
        User user = givenUser(23L);
        LocalDate originalHireDate = LocalDate.of(1990, 1, 1);
        user.requestOnboardingApproval(originalHireDate, BIRTH_DAY);
        given(policyConfigReader.getBoolean(PolicyConfigKey.ONBOARDING_REVISION_ENABLED))
                .willReturn(true);

        BusinessException exception = assertThrows(BusinessException.class,
                () -> authService.reviseOnboarding(
                        23L,
                        new OnboardingRequest(BIRTH_DAY, LocalDate.now(KST).plusDays(1))));

        // 미래일 검증을 markOnboardingRevised 호출 뒤로 옮기면 이 테스트가 깨진다.
        assertEquals(ErrorCode.FUTURE_HIRE_DATE, exception.getErrorCode());
        assertFalse(user.isOnboardingRevised());
        assertEquals(originalHireDate, user.getHireDate());
        assertEquals(OnboardingStatus.PENDING_APPROVAL, user.getOnboardingStatus());
    }

    @Test
    @DisplayName("자동 승인 범위 밖으로 수정하면 새 입사일로 승인 대기를 유지한다")
    void reviseOnboarding_자동승인범위밖_승인대기유지() {
        User user = givenUser(24L);
        user.requestOnboardingApproval(LocalDate.of(1990, 1, 1), BIRTH_DAY, "기존 직급");
        LocalDate revisedHireDate = LocalDate.of(2000, 1, 1);
        given(policyConfigReader.getBoolean(PolicyConfigKey.ONBOARDING_REVISION_ENABLED))
                .willReturn(true);
        given(policyConfigReader.getInt(PolicyConfigKey.ONBOARDING_AUTO_APPROVE_DAYS))
                .willReturn(90);

        authService.reviseOnboarding(
                24L,
                new OnboardingRequest(BIRTH_DAY, revisedHireDate, "새 직급"));

        // 공유 판정의 requestOnboardingApproval 호출 줄을 지우면 새 입사일 단정이 깨진다.
        assertEquals(OnboardingStatus.PENDING_APPROVAL, user.getOnboardingStatus());
        assertEquals(0, BigDecimal.ZERO.compareTo(user.getBaseDays()));
        assertTrue(user.isOnboardingRevised());
        assertEquals(revisedHireDate, user.getHireDate());
        // 직급은 온보딩 수정권과 무관하므로 request의 값으로 바뀌지 않는다.
        assertEquals("기존 직급", user.getJobGrade());
        assertNull(user.getLastResetDate());
        org.mockito.Mockito.verify(emailNotificationPublisher).publishOnboardingRevised(user);
    }

    @Test
    @DisplayName("자동 승인 범위 안으로 수정하면 온보딩을 확정하고 연차를 부여한다")
    void reviseOnboarding_자동승인범위안_온보딩확정() {
        User user = givenUser(25L);
        user.requestOnboardingApproval(LocalDate.of(1990, 1, 1), BIRTH_DAY);
        LocalDate today = LocalDate.now(KST);
        LocalDate revisedHireDate = today.minusDays(30);
        given(policyConfigReader.getBoolean(PolicyConfigKey.ONBOARDING_REVISION_ENABLED))
                .willReturn(true);
        given(policyConfigReader.getInt(PolicyConfigKey.ONBOARDING_AUTO_APPROVE_DAYS))
                .willReturn(90);
        given(policyConfigReader.getInt(PolicyConfigKey.MONTHLY_LEAVE_MAX_DAYS))
                .willReturn(11);
        given(leavePolicyService.calculateRetroactiveMonthlyDays(revisedHireDate, today))
                .willReturn(new BigDecimal("1.0"));

        authService.reviseOnboarding(
                25L,
                new OnboardingRequest(BIRTH_DAY, revisedHireDate));

        // 공유 판정의 grantInitialLeave 호출 줄을 지우면 완료 상태와 연차 단정이 깨진다.
        assertEquals(OnboardingStatus.COMPLETED, user.getOnboardingStatus());
        assertEquals(0, new BigDecimal("1.0").compareTo(user.getBaseDays()));
        assertEquals(revisedHireDate, user.getLastResetDate());
        assertEquals(revisedHireDate, user.getHireDate());
        assertTrue(user.isOnboardingRevised());
        org.mockito.Mockito.verify(emailNotificationPublisher).publishOnboardingRevised(user);
    }

    @Test
    @DisplayName("같은 사원이 연속 두 번 수정하면 두 번째 요청은 거부된다")
    void reviseOnboarding_연속두번수정_두번째수정권소진예외() {
        User user = givenUser(26L);
        user.requestOnboardingApproval(LocalDate.of(1990, 1, 1), BIRTH_DAY);
        LocalDate firstRevisedHireDate = LocalDate.of(2000, 1, 1);
        given(policyConfigReader.getBoolean(PolicyConfigKey.ONBOARDING_REVISION_ENABLED))
                .willReturn(true);
        given(policyConfigReader.getInt(PolicyConfigKey.ONBOARDING_AUTO_APPROVE_DAYS))
                .willReturn(90);

        authService.reviseOnboarding(
                26L,
                new OnboardingRequest(BIRTH_DAY, firstRevisedHireDate));

        BusinessException exception = assertThrows(BusinessException.class,
                () -> authService.reviseOnboarding(
                        26L,
                        new OnboardingRequest(BIRTH_DAY, LocalDate.of(2001, 1, 1))));

        // 첫 수정의 markOnboardingRevised 호출 줄을 지우면 두 번째 요청이 거부되지 않아 이 테스트가 깨진다.
        assertEquals(ErrorCode.ONBOARDING_REVISION_EXHAUSTED, exception.getErrorCode());
        assertEquals(firstRevisedHireDate, user.getHireDate());
        assertTrue(user.isOnboardingRevised());
    }

    @Test
    @DisplayName("승인 대기이며 설정이 켜지고 수정권을 쓰지 않았으면 수정 가능하다")
    void getMe_대기설정켜짐수정권미사용_수정가능() {
        User user = givenUser(27L);
        user.requestOnboardingApproval(LocalDate.of(2000, 1, 1), BIRTH_DAY);
        given(policyConfigReader.getBoolean(PolicyConfigKey.ONBOARDING_REVISION_ENABLED))
                .willReturn(true);

        UserMeResponse response = authService.getMe(27L);

        // UserMeResponse.from의 PENDING_APPROVAL·설정 ON·미사용 AND 판정 줄을 지우면 이 테스트가 깨진다.
        assertTrue(response.onboardingRevisable());
        assertFalse(response.onboardingRevised());
    }

    @Test
    @DisplayName("승인 대기 중이어도 설정이 꺼져 있으면 수정할 수 없다")
    void getMe_대기설정꺼짐_수정불가() {
        User user = givenUser(28L);
        user.requestOnboardingApproval(LocalDate.of(2000, 1, 1), BIRTH_DAY);
        given(policyConfigReader.getBoolean(PolicyConfigKey.ONBOARDING_REVISION_ENABLED))
                .willReturn(false);

        UserMeResponse response = authService.getMe(28L);

        // UserMeResponse.from의 revisionEnabled 조건 줄을 지우면 이 테스트가 깨진다.
        assertFalse(response.onboardingRevisable());
        assertFalse(response.onboardingRevised());
    }

    @Test
    @DisplayName("승인 대기 중 수정권을 이미 사용했으면 수정할 수 없다")
    void getMe_대기수정권사용완료_수정불가() {
        User user = givenUser(29L);
        user.requestOnboardingApproval(LocalDate.of(2000, 1, 1), BIRTH_DAY);
        user.markOnboardingRevised();
        given(policyConfigReader.getBoolean(PolicyConfigKey.ONBOARDING_REVISION_ENABLED))
                .willReturn(true);

        UserMeResponse response = authService.getMe(29L);

        // UserMeResponse.from의 !user.isOnboardingRevised 조건 줄을 지우면 이 테스트가 깨진다.
        assertFalse(response.onboardingRevisable());
        assertTrue(response.onboardingRevised());
    }

    @Test
    @DisplayName("온보딩 완료 사원은 설정이 켜져 있어도 수정할 수 없다")
    void getMe_온보딩완료_수정불가() {
        User user = givenUser(30L);
        LocalDate hireDate = LocalDate.now(KST).minusYears(1);
        user.completeOnboarding(hireDate, BIRTH_DAY);
        given(policyConfigReader.getBoolean(PolicyConfigKey.ONBOARDING_REVISION_ENABLED))
                .willReturn(true);

        UserMeResponse response = authService.getMe(30L);

        // UserMeResponse.from의 PENDING_APPROVAL 상태 조건 줄을 지우면 이 테스트가 깨진다.
        assertFalse(response.onboardingRevisable());
        assertFalse(response.onboardingRevised());
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

