package com.mlsoft.backend.domain.leave.service;

import com.mlsoft.backend.domain.user.entity.OnboardingStatus;
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
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.BDDMockito.given;

/**
 * 생일 반차 잡 (docs/09 §2, docs/01 요구사항 11) — 필수 시나리오 3·6.
 */
@ExtendWith(MockitoExtension.class)
class BirthdayLeaveGrantServiceTest {

    private static final Long USER_ID = 1L;

    @Mock
    private UserRepository userRepository;

    @InjectMocks
    private BirthdayLeaveGrantService birthdayLeaveGrantService;

    @Test
    @DisplayName("같은 날 두 번 실행해도 생일 반차는 한 번만 지급한다")
    void grant_같은날두번실행_멱등() {
        // last_birthday_grant_year가 멱등성의 근거다. 이게 없으면 재기동·수동 실행마다 0.5일이 쌓인다
        LocalDate today = LocalDate.of(2026, 4, 10);
        User user = user(LocalDate.of(2025, 1, 1), LocalDate.of(1995, 4, 10));
        given(userRepository.findById(USER_ID)).willReturn(Optional.of(user));

        assertTrue(birthdayLeaveGrantService.grant(USER_ID, today));
        assertFalse(birthdayLeaveGrantService.grant(USER_ID, today));

        assertBigDecimal("0.5", user.getBonusDays());
        assertEquals(2026, user.getLastBirthdayGrantYear().intValue());
    }

    @Test
    @DisplayName("입사 전에 지나간 올해 생일은 소급 지급하지 않는다")
    void grant_입사전생일_소급차단() {
        // 이 조건이 없으면 3월 생일·7월 입사자가 입사 당일 0.5일을 받는다 (docs/09 §2)
        User user = user(LocalDate.of(2026, 7, 1), LocalDate.of(1995, 3, 10));
        given(userRepository.findById(USER_ID)).willReturn(Optional.of(user));

        assertFalse(birthdayLeaveGrantService.grant(USER_ID, LocalDate.of(2026, 7, 1)));
        assertBigDecimal("0.0", user.getBonusDays());
        assertNull(user.getLastBirthdayGrantYear());
    }

    @Test
    @DisplayName("생일 == 입사일 == 오늘이면 지급한다 — 경계는 포함이다")
    void grant_생일과입사일이오늘_지급() {
        // birthdayThisYear.isBefore(hireDate)가 거짓이므로 통과해야 한다.
        // 부등호를 <= 로 잘못 쓰면 입사 당일이 생일인 사원이 영영 못 받는다
        User user = user(LocalDate.of(2026, 5, 20), LocalDate.of(1990, 5, 20));
        given(userRepository.findById(USER_ID)).willReturn(Optional.of(user));

        assertTrue(birthdayLeaveGrantService.grant(USER_ID, LocalDate.of(2026, 5, 20)));
        assertBigDecimal("0.5", user.getBonusDays());
    }

    @Test
    @DisplayName("아직 생일 전이면 지급하지 않는다")
    void grant_생일이후_아직안됨() {
        User user = user(LocalDate.of(2025, 1, 1), LocalDate.of(1995, 12, 25));
        given(userRepository.findById(USER_ID)).willReturn(Optional.of(user));

        assertFalse(birthdayLeaveGrantService.grant(USER_ID, LocalDate.of(2026, 12, 24)));
        assertNull(user.getLastBirthdayGrantYear());
    }

    @Test
    @DisplayName("2/29 생일은 평년에 2/28로 보정해 지급한다")
    void grant_윤년생일_평년에는2월28일() {
        // LocalDate.withYear가 말일로 보정한다. 보정이 없으면 2/29 생일자는 4년에 한 번만 받는다
        User user = user(LocalDate.of(2020, 1, 1), LocalDate.of(2000, 2, 29));
        given(userRepository.findById(USER_ID)).willReturn(Optional.of(user));

        assertTrue(birthdayLeaveGrantService.grant(USER_ID, LocalDate.of(2026, 2, 28)));
        assertBigDecimal("0.5", user.getBonusDays());
        assertEquals(2026, user.getLastBirthdayGrantYear().intValue());
    }

    // ============================ 헬퍼 ============================

    private User user(LocalDate hireDate, LocalDate birthDay) {
        return User.builder()
                .id(USER_ID)
                .name("테스트 사원")
                .email("user1@mlsoft.com")
                .role(Role.EMPLOYEE)
                // 스케줄러 3잡은 온보딩이 확정된 사원만 대상으로 삼는다 (리뷰 S-1)
                .onboardingStatus(OnboardingStatus.COMPLETED)
                .hireDate(hireDate)
                .birthDay(birthDay)
                .lastResetDate(hireDate)
                .baseDays(new BigDecimal("15.0"))
                .useDays(BigDecimal.ZERO)
                .bonusDays(BigDecimal.ZERO)
                .advanceDays(BigDecimal.ZERO)
                .isActive(true)
                .build();
    }

    private void assertBigDecimal(String expected, BigDecimal actual) {
        assertEquals(0, new BigDecimal(expected).compareTo(actual),
                "기대 " + expected + " 이지만 실제 " + actual);
    }
}
