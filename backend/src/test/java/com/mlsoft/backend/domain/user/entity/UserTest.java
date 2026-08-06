package com.mlsoft.backend.domain.user.entity;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.LocalDate;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * User 도메인의 잔액 불변식 테스트 (리뷰 I-1·I-2·I-8).
 *
 * <pre>advance_days = max(0, use_days − base_days − bonus_days)</pre>
 *
 * base/bonus/use를 바꾸는 도메인 메서드는 <b>resetAnnualLeave를 포함해 전부</b> 이 식을 유지해야 한다.
 * 이전에는 각 메서드가 advance_days를 독립적으로 더하고 빼서, 취소 순서·보너스 가산·
 * 관리자 연차 변경에서 식이 깨졌다. 여기서 그 경로들을 고정한다.
 *
 * 리셋도 재계산 대상인 이유는 아래 리셋 테스트 5건에 검산으로 남겼다 —
 * 재계산을 빼면 다음 기산일에 사원의 초과 사용분이 면제된다 (docs/09 §5 정정).
 */
class UserTest {

    // ==================== I-1 복구 순서 의존 ====================

    @Test
    @DisplayName("복구 — 먼저 신청한 건을 먼저 취소해도 전체 사용량 기준으로 당겨쓰기를 재계산한다")
    void restoreLeave_firstAppliedFirstCancelled_recalculatesAdvanceDays() {
        User user = userWithBalance("10.0", "0.0", "0.0", "0.0");

        // A 8일: 잔여 10으로 충당 → 당겨쓰기 없음
        assertEquals(0, BigDecimal.ZERO.compareTo(user.deductLeave(new BigDecimal("8.0"), true)));
        // B 5일: 잔여 2뿐이라 3일이 당겨쓰기
        assertEquals(0, new BigDecimal("3.0").compareTo(user.deductLeave(new BigDecimal("5.0"), true)));
        assertEquals(0, new BigDecimal("13.0").compareTo(user.getUseDays()));
        assertEquals(0, new BigDecimal("3.0").compareTo(user.getAdvanceDays()));

        user.restoreLeave(new BigDecimal("8.0")); // A 취소 — B는 잔여 10으로 충당되므로 당겨쓰기 소멸

        assertEquals(0, new BigDecimal("5.0").compareTo(user.getUseDays()));
        assertEquals(0, BigDecimal.ZERO.compareTo(user.getAdvanceDays()));

        user.restoreLeave(new BigDecimal("5.0"));

        assertEquals(0, BigDecimal.ZERO.compareTo(user.getUseDays()));
        assertEquals(0, BigDecimal.ZERO.compareTo(user.getAdvanceDays()));
    }

    @Test
    @DisplayName("복구 — 나중에 신청한 건을 먼저 취소해도 같은 잔액에 도달한다 (순서 무관)")
    void restoreLeave_lastAppliedFirstCancelled_reachesSameBalance() {
        User user = userWithBalance("10.0", "0.0", "0.0", "0.0");
        user.deductLeave(new BigDecimal("8.0"), true);
        user.deductLeave(new BigDecimal("5.0"), true);

        user.restoreLeave(new BigDecimal("5.0")); // B 먼저 취소

        assertEquals(0, new BigDecimal("8.0").compareTo(user.getUseDays()));
        assertEquals(0, BigDecimal.ZERO.compareTo(user.getAdvanceDays()));

        user.restoreLeave(new BigDecimal("8.0"));

        assertEquals(0, BigDecimal.ZERO.compareTo(user.getUseDays()));
        assertEquals(0, BigDecimal.ZERO.compareTo(user.getAdvanceDays()));
    }

    @Test
    @DisplayName("차감 — 잔여가 이미 음수인 상태의 추가 신청은 증가분만 당겨쓰기로 반환한다")
    void deductLeave_alreadyNegativeRemaining_returnsOnlyIncrement() {
        User user = userWithBalance("10.0", "13.0", "0.0", "3.0"); // 이미 3일 당겨쓴 상태

        BigDecimal advanceUsed = user.deductLeave(new BigDecimal("2.0"), true);

        assertEquals(0, new BigDecimal("2.0").compareTo(advanceUsed)); // 누적 5가 아니라 증가분 2
        assertEquals(0, new BigDecimal("15.0").compareTo(user.getUseDays()));
        assertEquals(0, new BigDecimal("5.0").compareTo(user.getAdvanceDays()));
    }

    // ==================== I-2 보너스 가산 ====================

    @Test
    @DisplayName("보너스 가산 — 초과분보다 많이 가산하면 당겨쓰기가 0으로 정산된다")
    void addBonusDays_sufficientBonus_settlesAdvanceDaysToZero() {
        User user = userWithBalance("10.0", "13.0", "0.0", "3.0");

        user.addBonusDays(new BigDecimal("5.0"));

        assertEquals(0, new BigDecimal("5.0").compareTo(user.getBonusDays()));
        assertEquals(0, BigDecimal.ZERO.compareTo(user.getAdvanceDays()));
    }

    @Test
    @DisplayName("보너스 가산 — 초과분보다 적게 가산하면 남은 부족분만 당겨쓰기로 남는다")
    void addBonusDays_insufficientBonus_keepsRemainingAdvanceDays() {
        User user = userWithBalance("10.0", "13.0", "0.0", "3.0");

        user.addBonusDays(new BigDecimal("1.0"));

        assertEquals(0, new BigDecimal("1.0").compareTo(user.getBonusDays()));
        assertEquals(0, new BigDecimal("2.0").compareTo(user.getAdvanceDays()));
    }

    // ==================== I-8 관리자 연차 직접 설정 ====================

    @Test
    @DisplayName("연차 직접 설정 — 사용량보다 크게 늘리면 당겨쓰기가 0으로 정산된다")
    void updateBaseDays_increasedAboveUseDays_settlesAdvanceDaysToZero() {
        User user = userWithBalance("10.0", "13.0", "0.0", "3.0");

        user.updateBaseDays(new BigDecimal("20.0"));

        assertEquals(0, new BigDecimal("20.0").compareTo(user.getBaseDays()));
        assertEquals(0, BigDecimal.ZERO.compareTo(user.getAdvanceDays()));
    }

    @Test
    @DisplayName("연차 직접 설정 — 부족분이 남으면 그만큼만 당겨쓰기로 남는다")
    void updateBaseDays_increasedButStillInsufficient_keepsRemainingAdvanceDays() {
        User user = userWithBalance("10.0", "13.0", "0.0", "3.0");

        user.updateBaseDays(new BigDecimal("11.0"));

        assertEquals(0, new BigDecimal("11.0").compareTo(user.getBaseDays()));
        assertEquals(0, new BigDecimal("2.0").compareTo(user.getAdvanceDays()));
    }

    @Test
    @DisplayName("연차 직접 설정 — 사용량보다 작게 줄이면 새 초과분이 당겨쓰기로 잡힌다")
    void updateBaseDays_decreasedBelowUseDays_createsAdvanceDays() {
        User user = userWithBalance("15.0", "3.0", "0.0", "0.0");

        user.updateBaseDays(new BigDecimal("2.0"));

        assertEquals(0, new BigDecimal("2.0").compareTo(user.getBaseDays()));
        assertEquals(0, new BigDecimal("1.0").compareTo(user.getAdvanceDays()));
    }

    // ==================== 월차 적립 / 멱등성 ====================

    @Test
    @DisplayName("월차 적립 — 늘어난 기본 연차만큼 당겨쓰기가 정산된다")
    void addMonthlyLeave_existingAdvance_settlesByOneDay() {
        User user = userWithBalance("10.0", "13.0", "0.0", "3.0");

        user.addMonthlyLeave();

        assertEquals(0, new BigDecimal("11.0").compareTo(user.getBaseDays()));
        assertEquals(0, new BigDecimal("2.0").compareTo(user.getAdvanceDays()));
    }

    @Test
    @DisplayName("재계산 멱등성 — 잔액이 그대로면 몇 번 재계산해도 당겨쓰기가 변하지 않는다")
    void syncAdvanceDays_repeated_isIdempotent() {
        User user = userWithBalance("10.0", "13.0", "0.0", "3.0");

        user.addBonusDays(BigDecimal.ZERO);
        assertEquals(0, new BigDecimal("3.0").compareTo(user.getAdvanceDays()));

        user.addBonusDays(BigDecimal.ZERO);
        assertEquals(0, new BigDecimal("3.0").compareTo(user.getAdvanceDays()));
    }

    // ==================== 리셋 ====================

    @Test
    @DisplayName("리셋 — 당겨쓴 연차를 새 기본 연차에서 차감하고 0으로 정산한다")
    void resetAnnualLeave_advanceWithinNewBase_settlesAdvanceDays() {
        User user = userWithBalance("15.0", "0.0", "0.0", "3.0");
        LocalDate resetDate = LocalDate.of(2026, 8, 6);

        user.resetAnnualLeave(new BigDecimal("15.0"), resetDate);

        assertEquals(0, new BigDecimal("12.0").compareTo(user.getBaseDays()));
        assertEquals(0, BigDecimal.ZERO.compareTo(user.getUseDays()));
        assertEquals(0, BigDecimal.ZERO.compareTo(user.getBonusDays()));
        assertEquals(0, BigDecimal.ZERO.compareTo(user.getAdvanceDays()));
        assertEquals(resetDate, user.getLastResetDate());
    }

    @Test
    @DisplayName("리셋 — 빚이 새 기본 연차보다 크면 음수 base로 남기고 남은 빚을 당겨쓰기로 이어받는다")
    void resetAnnualLeave_advanceExceedsNewBase_carriesRemainingDebt() {
        User user = userWithBalance("15.0", "0.0", "0.0", "20.0");
        LocalDate resetDate = LocalDate.of(2026, 8, 6);

        user.resetAnnualLeave(new BigDecimal("15.0"), resetDate);

        // 다음 리셋은 base를 덮어쓰므로(base = newBase − advance) 음수 base는 버려진다.
        // 남은 빚 5일을 advance로 옮겨 담지 않으면 다음 기산일에 빚이 면제된다.
        assertEquals(0, new BigDecimal("-5.0").compareTo(user.getBaseDays()));
        assertEquals(0, new BigDecimal("5.0").compareTo(user.getAdvanceDays()));
        assertEquals(resetDate, user.getLastResetDate());
    }

    @Test
    @DisplayName("리셋 2회 — 활동이 없어도 남은 빚이 다음 기산일에 정확히 한 번 정산된다")
    void resetAnnualLeave_twiceWithoutActivity_settlesDebtExactlyOnce() {
        // Y1: 부여 15 · 사용 35 → 빚 20
        User user = userWithBalance("15.0", "35.0", "0.0", "20.0");

        user.resetAnnualLeave(new BigDecimal("15.0"), LocalDate.of(2026, 8, 6)); // Y2 시작
        user.resetAnnualLeave(new BigDecimal("15.0"), LocalDate.of(2027, 8, 6)); // Y3 시작

        // Σ부여 45 − Σ사용 35 = 10. 리셋에서 재계산을 빼면 15가 되어 5일이 공짜로 생긴다.
        assertEquals(0, new BigDecimal("10.0").compareTo(user.getBaseDays()));
        assertEquals(0, BigDecimal.ZERO.compareTo(user.getAdvanceDays()));
    }

    @Test
    @DisplayName("리셋 2회 — 중간에 월차가 적립돼도 활동 없는 경우와 같은 규칙으로 정산된다")
    void resetAnnualLeave_withMonthlyGrantBetween_settlesConsistently() {
        User user = userWithBalance("15.0", "35.0", "0.0", "20.0");

        user.resetAnnualLeave(new BigDecimal("15.0"), LocalDate.of(2026, 8, 6));
        user.addMonthlyLeave(); // base −5 → −4, 빚 5 → 4
        user.resetAnnualLeave(new BigDecimal("15.0"), LocalDate.of(2027, 8, 6));

        // Σ부여 46(15+15+1+15) − Σ사용 35 = 11
        assertEquals(0, new BigDecimal("11.0").compareTo(user.getBaseDays()));
        assertEquals(0, BigDecimal.ZERO.compareTo(user.getAdvanceDays()));
    }

    @Test
    @DisplayName("리셋 반복 — 빚이 정책 연차의 몇 배여도 매년 줄어들어 종료한다 (무한 정산 없음)")
    void resetAnnualLeave_debtLargerThanSeveralYears_terminates() {
        User user = userWithBalance("15.0", "50.0", "0.0", "50.0");
        BigDecimal annual = new BigDecimal("15.0");

        user.resetAnnualLeave(annual, LocalDate.of(2026, 8, 6));
        assertEquals(0, new BigDecimal("35.0").compareTo(user.getAdvanceDays()));

        user.resetAnnualLeave(annual, LocalDate.of(2027, 8, 6));
        assertEquals(0, new BigDecimal("20.0").compareTo(user.getAdvanceDays()));

        user.resetAnnualLeave(annual, LocalDate.of(2028, 8, 6));
        assertEquals(0, new BigDecimal("5.0").compareTo(user.getAdvanceDays()));

        user.resetAnnualLeave(annual, LocalDate.of(2029, 8, 6));
        assertEquals(0, BigDecimal.ZERO.compareTo(user.getAdvanceDays()));
        assertEquals(0, new BigDecimal("10.0").compareTo(user.getBaseDays()));
    }

    // ============================ 헬퍼 ============================

    private User userWithBalance(String baseDays, String useDays, String bonusDays, String advanceDays) {
        return User.builder()
                .id(1L)
                .name("테스트 사원")
                .email("user1@mlsoft.com")
                .role(Role.EMPLOYEE)
                .baseDays(new BigDecimal(baseDays))
                .useDays(new BigDecimal(useDays))
                .bonusDays(new BigDecimal(bonusDays))
                .advanceDays(new BigDecimal(advanceDays))
                .isActive(true)
                .build();
    }
}
