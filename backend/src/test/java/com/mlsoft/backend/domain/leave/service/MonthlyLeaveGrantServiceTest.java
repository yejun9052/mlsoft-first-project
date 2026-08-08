package com.mlsoft.backend.domain.leave.service;

import com.mlsoft.backend.domain.policy.entity.PolicyConfigKey;
import com.mlsoft.backend.domain.policy.service.PolicyConfigReader;
import com.mlsoft.backend.domain.user.entity.Role;
import com.mlsoft.backend.domain.user.entity.User;
import com.mlsoft.backend.domain.user.repository.UserRepository;
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
import static org.mockito.BDDMockito.given;

/**
 * 월차 적립 잡 (docs/09 §2·§6, 갭분석 B-1) — 필수 시나리오 5·10·11.
 */
@ExtendWith(MockitoExtension.class)
class MonthlyLeaveGrantServiceTest {

    private static final Long USER_ID = 1L;

    @Mock
    private UserRepository userRepository;
    @Mock
    private PolicyConfigReader policyConfigReader;

    @InjectMocks
    private MonthlyLeaveGrantService monthlyLeaveGrantService;

    @Test
    @DisplayName("3개월 밀린 월차 — 한 번의 실행에서 3회분을 몰아서 적립한다")
    void grant_3개월밀림_3회반복적립() {
        // 2026-01-01 입사, 오늘 2026-04-01 → 2/1·3/1·4/1 세 회차가 도래했다.
        // 서버가 꺼져 있던 기간을 다음 실행이 따라잡는다 (§6 catch-up)
        User user = user(LocalDate.of(2026, 1, 1), "0.0", 0);
        given(userRepository.findById(USER_ID)).willReturn(Optional.of(user));
        given(policyConfigReader.getInt(PolicyConfigKey.MONTHLY_LEAVE_MAX_DAYS)).willReturn(11);

        int granted = monthlyLeaveGrantService.grant(USER_ID, LocalDate.of(2026, 4, 1));

        assertEquals(3, granted);
        assertEquals(3, user.getMonthlyGrantedCount());
        assertBigDecimal("3.0", user.getBaseDays());
    }

    @Test
    @DisplayName("상한 도달 — 11회에서 멈추고 12회차는 지급하지 않는다")
    void grant_상한도달_11회에서중단() {
        // 이미 9회 받은 사원이 2026-12-31에 걸리면 10·11회차만 받는다.
        // 12회차 지급일은 1주년(2027-01-01)이라 어차피 대상 밖이다
        User user = user(LocalDate.of(2026, 1, 1), "9.0", 9);
        given(userRepository.findById(USER_ID)).willReturn(Optional.of(user));
        given(policyConfigReader.getInt(PolicyConfigKey.MONTHLY_LEAVE_MAX_DAYS)).willReturn(11);

        int granted = monthlyLeaveGrantService.grant(USER_ID, LocalDate.of(2026, 12, 31));

        assertEquals(2, granted);
        assertEquals(11, user.getMonthlyGrantedCount());
        assertBigDecimal("11.0", user.getBaseDays());
    }

    @Test
    @DisplayName("말일 클램프 — 1/31 입사자의 2회차는 3/28이 아니라 3/31에 지급한다")
    void grant_1월31일입사_2회차는3월31일() {
        // 직전 지급일(2/28)에서 한 달을 더하면 3/28이 되어 지급일이 영구히 앞당겨진다.
        // hire_date(1/31)에서 매번 다시 더하면 2/28 → 3/31 → 4/30으로 제자리를 찾는다.
        // monthly_granted_count를 쓰는 이유가 바로 이것이다 (docs/09 §3 정정)
        User user = user(LocalDate.of(2026, 1, 31), "1.0", 1);
        given(userRepository.findById(USER_ID)).willReturn(Optional.of(user));
        given(policyConfigReader.getInt(PolicyConfigKey.MONTHLY_LEAVE_MAX_DAYS)).willReturn(11);

        // 3/28에는 아직 도래하지 않았다
        assertEquals(0, monthlyLeaveGrantService.grant(USER_ID, LocalDate.of(2026, 3, 28)));
        assertEquals(1, user.getMonthlyGrantedCount());

        // 3/31에 도래한다
        assertEquals(1, monthlyLeaveGrantService.grant(USER_ID, LocalDate.of(2026, 3, 31)));
        assertEquals(2, user.getMonthlyGrantedCount());
        assertBigDecimal("2.0", user.getBaseDays());
    }

    @Test
    @DisplayName("온보딩 소급분 — 이미 지급된 개월분을 스케줄러가 다시 적립하지 않는다")
    void grant_온보딩소급분_이중적립없음() {
        // 5개월 전 입사자가 오늘 온보딩하면 base=5.0·count=5가 된다.
        // count를 기록하지 않으면(markMonthlyGranted 누락) 같은 5개월분이 여기서 또 적립된다
        LocalDate today = LocalDate.of(2026, 6, 1);
        User user = user(LocalDate.of(2026, 1, 1), "5.0", 5);
        given(userRepository.findById(USER_ID)).willReturn(Optional.of(user));
        given(policyConfigReader.getInt(PolicyConfigKey.MONTHLY_LEAVE_MAX_DAYS)).willReturn(11);

        // 6회차 지급일은 7/1이므로 6/1에는 추가 적립이 없다
        assertEquals(0, monthlyLeaveGrantService.grant(USER_ID, today));
        assertEquals(5, user.getMonthlyGrantedCount());
        assertBigDecimal("5.0", user.getBaseDays());
    }

    @Test
    @DisplayName("1주년이 지났으면 대상이 아니다 — 남은 월차는 소멸한다")
    void grant_1주년경과_적립하지않음() {
        // 갭분석 B-2. 이 시점에는 리셋 잡이 먼저 돌아 정책 연차를 부여한 상태다 (docs/09 §1)
        User user = user(LocalDate.of(2025, 1, 1), "3.0", 3);
        given(userRepository.findById(USER_ID)).willReturn(Optional.of(user));

        assertEquals(0, monthlyLeaveGrantService.grant(USER_ID, LocalDate.of(2026, 1, 1)));
        assertEquals(3, user.getMonthlyGrantedCount());
    }

    // ============================ 헬퍼 ============================

    private User user(LocalDate hireDate, String baseDays, int monthlyGrantedCount) {
        return User.builder()
                .id(USER_ID)
                .name("테스트 사원")
                .email("user1@mlsoft.com")
                .role(Role.EMPLOYEE)
                .hireDate(hireDate)
                .lastResetDate(hireDate)
                .baseDays(new BigDecimal(baseDays))
                .useDays(BigDecimal.ZERO)
                .bonusDays(BigDecimal.ZERO)
                .advanceDays(BigDecimal.ZERO)
                .monthlyGrantedCount(monthlyGrantedCount)
                .isActive(true)
                .build();
    }

    private void assertBigDecimal(String expected, BigDecimal actual) {
        assertEquals(0, new BigDecimal(expected).compareTo(actual),
                "기대 " + expected + " 이지만 실제 " + actual);
    }
}
