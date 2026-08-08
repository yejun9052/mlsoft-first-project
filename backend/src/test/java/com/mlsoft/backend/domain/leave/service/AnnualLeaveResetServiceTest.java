package com.mlsoft.backend.domain.leave.service;

import com.mlsoft.backend.domain.common.RequestStatus;
import com.mlsoft.backend.domain.leave.entity.LeaveResetHistory;
import com.mlsoft.backend.domain.leave.repository.LeaveRequestRepository;
import com.mlsoft.backend.domain.leave.repository.LeaveResetHistoryRepository;
import com.mlsoft.backend.domain.policy.entity.PolicyConfigKey;
import com.mlsoft.backend.domain.policy.service.LeavePolicyService;
import com.mlsoft.backend.domain.policy.service.PolicyConfigReader;
import com.mlsoft.backend.domain.user.entity.OnboardingStatus;
import com.mlsoft.backend.domain.user.entity.Role;
import com.mlsoft.backend.domain.user.entity.User;
import com.mlsoft.backend.domain.user.repository.UserRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.Collection;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

/**
 * 기산일 리셋 잡 (docs/09 §4·§5·§6) — 필수 시나리오 1·2·7·8.
 *
 * <p>이 잡은 잔액 4필드를 한꺼번에 갈아 끼우므로 <b>기대값을 전부 숫자로 검산</b>해 둔다.
 * 각 테스트의 주석에 그 검산이 있다.
 */
@ExtendWith(MockitoExtension.class)
class AnnualLeaveResetServiceTest {

    private static final Long USER_ID = 1L;

    @Mock
    private UserRepository userRepository;
    @Mock
    private LeaveRequestRepository leaveRequestRepository;
    @Mock
    private LeaveResetHistoryRepository leaveResetHistoryRepository;
    @Mock
    private LeavePolicyService leavePolicyService;
    @Mock
    private PolicyConfigReader policyConfigReader;

    @InjectMocks
    private AnnualLeaveResetService annualLeaveResetService;

    @Test
    @DisplayName("2년 밀린 리셋 — 회차마다 그 회차 기산일로 이월분을 다시 집계하고 이력을 2건 남긴다")
    void reset_2년밀림_회차별기산일로재집계하고이력2건() {
        // 2022-03-01 입사, 마지막 기산일 2024-03-01, 오늘 2026-03-01 → 2회 밀렸다.
        // 선차감 5일의 내역: 2024년도분 2일 + [2025-03-01, 2026-03-01) 1일 + 2026-03-01 이후 2일
        //   → carriedUse(2025-03-01) = 3.0,  carriedUse(2026-03-01) = 2.0
        //   (2차 기산일 이후 날짜는 1차 이후에도 포함되므로 1차 ≥ 2차여야 한다)
        LocalDate today = LocalDate.of(2026, 3, 1);
        User user = user("15.0", "5.0", "0.0", LocalDate.of(2022, 3, 1), LocalDate.of(2024, 3, 1));

        given(userRepository.findById(USER_ID)).willReturn(Optional.of(user));
        given(policyConfigReader.getBoolean(PolicyConfigKey.BONUS_CARRY_OVER_ENABLED)).willReturn(false);
        given(leavePolicyService.calculateAnnualLeaveDays(3)).willReturn(new BigDecimal("16.0"));
        given(leavePolicyService.calculateAnnualLeaveDays(4)).willReturn(new BigDecimal("16.0"));
        givenCarriedUse(user, LocalDate.of(2025, 3, 1), "3.0");
        givenCarriedUse(user, LocalDate.of(2026, 3, 1), "2.0");

        int rounds = annualLeaveResetService.reset(USER_ID, today);

        // 1차: oldYearUse = 5−3 = 2, 채무 = max(0, 2−15−0) = 0 → base 16, use 3
        // 2차: oldYearUse = 3−2 = 1, 채무 = max(0, 1−16−0) = 0 → base 16, use 2
        assertEquals(2, rounds);
        assertEquals(LocalDate.of(2026, 3, 1), user.getLastResetDate());
        assertBigDecimal("16.0", user.getBaseDays());
        assertBigDecimal("2.0", user.getUseDays());
        assertBigDecimal("0.0", user.getAdvanceDays());

        // 최근 기산일로 한 번에 점프하지 않는다 — 중간 연도 이력이 비면 사후 검증이 불가능해진다 (§6)
        ArgumentCaptor<LeaveResetHistory> captor = ArgumentCaptor.forClass(LeaveResetHistory.class);
        verify(leaveResetHistoryRepository, times(2)).save(captor.capture());
        List<LeaveResetHistory> histories = captor.getAllValues();
        assertEquals(LocalDate.of(2025, 3, 1), histories.get(0).getResetDate());
        assertEquals(LocalDate.of(2026, 3, 1), histories.get(1).getResetDate());
        // 1차 소멸 = base 15 + bonus 0 − oldYearUse 2 − 이월보너스 0 = 13
        assertBigDecimal("13.0", histories.get(0).getExpiredDays());
        // 2차 소멸 = base 16 − oldYearUse 1 = 15
        assertBigDecimal("15.0", histories.get(1).getExpiredDays());

        // 회차마다 "그 회차의" 기산일로 집계했는지를 호출 인자로 직접 고정한다
        verify(leaveRequestRepository).sumPreDeductedDaysOnOrAfter(
                eq(user), eq(LocalDate.of(2025, 3, 1)), eq(preDeductedStatuses()));
        verify(leaveRequestRepository).sumPreDeductedDaysOnOrAfter(
                eq(user), eq(LocalDate.of(2026, 3, 1)), eq(preDeductedStatuses()));
    }

    @Test
    @DisplayName("기산일을 걸친 신청 — 기산일 이후 날짜 2일만 새 사용 연차로 재차감한다")
    void reset_기산일걸친신청_기산일이후2일만재차감() {
        // 2/28·3/1·3/2 3일 신청, 기산일 3/1 → 새 use는 3.0이 아니라 2.0이어야 한다.
        // 신청 단위로 세면 이전 연도에 이미 쓴 2/28까지 새 연도에 또 차감돼 사원이 손해를 본다 (§5)
        LocalDate resetDate = LocalDate.of(2026, 3, 1);
        User user = user("15.0", "3.0", "0.0", LocalDate.of(2025, 3, 1), LocalDate.of(2025, 3, 1));

        given(userRepository.findById(USER_ID)).willReturn(Optional.of(user));
        given(policyConfigReader.getBoolean(PolicyConfigKey.BONUS_CARRY_OVER_ENABLED)).willReturn(false);
        given(leavePolicyService.calculateAnnualLeaveDays(1)).willReturn(new BigDecimal("15.0"));
        givenCarriedUse(user, resetDate, "2.0");

        int rounds = annualLeaveResetService.reset(USER_ID, resetDate);

        // oldYearUse = 3−2 = 1, 채무 = max(0, 1−15−0) = 0 → base 15, use 2, advance 0
        assertEquals(1, rounds);
        assertBigDecimal("15.0", user.getBaseDays());
        assertBigDecimal("2.0", user.getUseDays());
        assertBigDecimal("0.0", user.getAdvanceDays());
    }

    @Test
    @DisplayName("I-11 — 사용분 전체가 이월되면 이력의 새 연차가 실제 전이와 같다 (예전 식은 5일 어긋났다)")
    void reset_전체이월_이력이실제전이와일치() {
        // base 15 · use 20 · advance 5인데 20일 전부가 새 연도 날짜인 경우.
        // advance를 그대로 빼던 예전 이력 식은 newBase 10 · advanceSettled 5를 기록했지만
        // 실제 엔티티는 base 15가 된다 — 감사 기록이 5일 틀렸다는 뜻이다.
        LocalDate resetDate = LocalDate.of(2026, 3, 1);
        User user = user("15.0", "20.0", "0.0", LocalDate.of(2025, 3, 1), LocalDate.of(2025, 3, 1));
        assertBigDecimal("5.0", user.getAdvanceDays()); // 전제 확인

        given(userRepository.findById(USER_ID)).willReturn(Optional.of(user));
        given(policyConfigReader.getBoolean(PolicyConfigKey.BONUS_CARRY_OVER_ENABLED)).willReturn(false);
        given(leavePolicyService.calculateAnnualLeaveDays(1)).willReturn(new BigDecimal("15.0"));
        givenCarriedUse(user, resetDate, "20.0");

        annualLeaveResetService.reset(USER_ID, resetDate);

        ArgumentCaptor<LeaveResetHistory> captor = ArgumentCaptor.forClass(LeaveResetHistory.class);
        verify(leaveResetHistoryRepository).save(captor.capture());
        LeaveResetHistory history = captor.getValue();

        // oldYearUse = 20−20 = 0 → 채무 0 → newBase = 15 − 0 = 15
        assertBigDecimal("15.0", history.getNewBaseDays());
        assertBigDecimal("0.0", history.getAdvanceSettled());
        // 이전 연도 base 15일을 하나도 쓰지 않았으므로 15일이 통째로 소멸한다
        assertBigDecimal("15.0", history.getExpiredDays());
        // 이력과 실제 엔티티가 반드시 같아야 한다 — 이게 이 테스트의 요지다
        assertBigDecimal("15.0", user.getBaseDays());
        assertBigDecimal("20.0", user.getUseDays());
        assertBigDecimal("5.0", user.getAdvanceDays());
    }

    @Test
    @DisplayName("보너스 이월 ON — 이미 써버린 보너스는 부활하지 않는다")
    void reset_보너스이월ON_사용한보너스는제외() {
        // base 15 · bonus 5 · use 17 → base를 2일 초과했으므로 그 2일은 보너스에서 쓴 것이다.
        // 보정 없이 bonus 5를 그대로 넘기면 이미 써버린 2일이 되살아난다 (docs/02 메모 10)
        LocalDate resetDate = LocalDate.of(2026, 3, 1);
        User user = user("15.0", "17.0", "5.0", LocalDate.of(2025, 3, 1), LocalDate.of(2025, 3, 1));

        given(userRepository.findById(USER_ID)).willReturn(Optional.of(user));
        given(policyConfigReader.getBoolean(PolicyConfigKey.BONUS_CARRY_OVER_ENABLED)).willReturn(true);
        given(leavePolicyService.calculateAnnualLeaveDays(1)).willReturn(new BigDecimal("15.0"));
        givenCarriedUse(user, resetDate, "0.0");

        annualLeaveResetService.reset(USER_ID, resetDate);

        // 이월분 = max(0, 5 − max(0, 17−15)) = 3
        assertBigDecimal("3.0", user.getBonusDays());
        assertBigDecimal("0.0", user.getUseDays());
        // 채무 = max(0, 17−15−5) = 0 → base는 정책 연차 그대로
        assertBigDecimal("15.0", user.getBaseDays());
    }

    @Test
    @DisplayName("보너스 이월 OFF — 남은 보너스가 있어도 이월하지 않는다")
    void reset_보너스이월OFF_이월하지않음() {
        LocalDate resetDate = LocalDate.of(2026, 3, 1);
        User user = user("15.0", "17.0", "5.0", LocalDate.of(2025, 3, 1), LocalDate.of(2025, 3, 1));

        given(userRepository.findById(USER_ID)).willReturn(Optional.of(user));
        given(policyConfigReader.getBoolean(PolicyConfigKey.BONUS_CARRY_OVER_ENABLED)).willReturn(false);
        given(leavePolicyService.calculateAnnualLeaveDays(1)).willReturn(new BigDecimal("15.0"));
        givenCarriedUse(user, resetDate, "0.0");

        annualLeaveResetService.reset(USER_ID, resetDate);

        // 설정이 꺼져 있으면 이월 공식과 무관하게 새 bonus는 0이다
        assertBigDecimal("0.0", user.getBonusDays());
        // 소멸 = base 15 + bonus 5 − oldYearUse 17 − 이월 0 = 3
        ArgumentCaptor<LeaveResetHistory> captor = ArgumentCaptor.forClass(LeaveResetHistory.class);
        verify(leaveResetHistoryRepository).save(captor.capture());
        assertBigDecimal("3.0", captor.getValue().getExpiredDays());
    }

    @Test
    @DisplayName("퇴직자·온보딩 미완료자는 트랜잭션 안에서 다시 걸러진다")
    void reset_퇴직자와온보딩미완료_처리하지않음() {
        // 대상 조회(readOnly)와 실제 처리(REQUIRES_NEW)가 다른 트랜잭션이라
        // 그 사이에 상태가 바뀔 수 있다 — 가드를 처리 시점에 다시 본다
        User retired = user("15.0", "0.0", "0.0", LocalDate.of(2020, 3, 1), LocalDate.of(2024, 3, 1));
        retired.retire(LocalDate.of(2026, 1, 1));
        given(userRepository.findById(USER_ID)).willReturn(Optional.of(retired));

        assertEquals(0, annualLeaveResetService.reset(USER_ID, LocalDate.of(2026, 3, 1)));
        verify(leaveResetHistoryRepository, never()).save(any());
    }

    // ============================ 헬퍼 ============================

    /** advance_days는 파생값이라 잔액 3필드에서 계산해 넣는다 — 불가능한 조합을 픽스처로 만들지 않기 위함 */
    private User user(String baseDays, String useDays, String bonusDays,
                      LocalDate hireDate, LocalDate lastResetDate) {
        BigDecimal base = new BigDecimal(baseDays);
        BigDecimal use = new BigDecimal(useDays);
        BigDecimal bonus = new BigDecimal(bonusDays);
        return User.builder()
                .id(USER_ID)
                .name("테스트 사원")
                .email("user1@mlsoft.com")
                .role(Role.EMPLOYEE)
                // 스케줄러 3잡은 온보딩이 확정된 사원만 대상으로 삼는다 (리뷰 S-1)
                .onboardingStatus(OnboardingStatus.COMPLETED)
                .hireDate(hireDate)
                .lastResetDate(lastResetDate)
                .baseDays(base)
                .useDays(use)
                .bonusDays(bonus)
                .advanceDays(use.subtract(base).subtract(bonus).max(BigDecimal.ZERO))
                .isActive(true)
                .build();
    }

    private void givenCarriedUse(User user, LocalDate resetDate, String days) {
        given(leaveRequestRepository.sumPreDeductedDaysOnOrAfter(
                eq(user), eq(resetDate), eq(preDeductedStatuses())))
                .willReturn(new BigDecimal(days));
    }

    private Collection<RequestStatus> preDeductedStatuses() {
        return List.of(RequestStatus.APPROVED, RequestStatus.PENDING, RequestStatus.CANCEL_PENDING);
    }

    private void assertBigDecimal(String expected, BigDecimal actual) {
        assertEquals(0, new BigDecimal(expected).compareTo(actual),
                "기대 " + expected + " 이지만 실제 " + actual);
    }
}
