package com.mlsoft.backend.domain.leave.entity;

import com.mlsoft.backend.domain.user.entity.User;
import com.mlsoft.backend.global.entity.BaseTimeEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDate;

/**
 * 기산일 리셋·소멸 감사 이력 (docs/02 3-11(b), 갭분석 B-2).
 * 리셋 직전 상태를 기록해 스케줄러 동작을 사후 검증할 수 있게 한다.
 */
@Entity
// 관리자 목록이 reset_date 내림차순으로 페이징한다 (리뷰 D-1).
// 스케줄러가 붙으면서 매년 사원당 1행씩 실제로 쌓이기 시작한 테이블이다.
@Table(name = "leave_reset_history", indexes =
        @Index(name = "idx_leave_reset_history_reset_date", columnList = "reset_date"))
@Getter
@Builder
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor(access = AccessLevel.PRIVATE)
public class LeaveResetHistory extends BaseTimeEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /** 대상 사원 */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

    /** 기산일 */
    @Column(nullable = false)
    private LocalDate resetDate;

    /** 리셋 전 총 연차 */
    @Column(nullable = false, precision = 4, scale = 1)
    private BigDecimal prevBaseDays;

    /** 리셋 전 사용 연차 */
    @Column(nullable = false, precision = 4, scale = 1)
    private BigDecimal prevUseDays;

    /** 소멸된 연차 (이월되지 않고 사라진 미사용분) */
    @Column(nullable = false, precision = 4, scale = 1)
    private BigDecimal expiredDays;

    /** 정산된 당겨쓰기 — 이전 연도 채무(User.carryOverDebt)와 같은 값 */
    @Column(nullable = false, precision = 4, scale = 1)
    private BigDecimal advanceSettled;

    /** 리셋 후 총 연차 */
    @Column(nullable = false, precision = 4, scale = 1)
    private BigDecimal newBaseDays;

    /** 다음 연도로 이월된 보너스 (docs/02 메모 10 — bonus_carry_over_enabled가 꺼져 있으면 0) */
    @Column(nullable = false, precision = 4, scale = 1)
    @Builder.Default
    private BigDecimal carriedBonusDays = BigDecimal.ZERO;

    /**
     * 리셋 이력 생성 — {@code user.resetAnnualLeave} 호출 <b>직전</b> 상태를 스냅샷으로 기록한다.
     *
     * <p><b>실제 전이와 같은 식을 쓴다.</b> 예전에는 이 팩토리가 {@code advance_days}를 직접 빼서
     * 새 연차를 계산했는데, {@code carriedUse > 0}이면 기록과 실제 엔티티 상태가 갈렸다 —
     * {@code base=15·use=20·advance=5}인 사원의 20일이 전부 이월되는 경우 실제 새 연차는 15인데
     * 이력에는 10이 남아 <b>감사 기록이 5일 틀렸다</b>. 채무 계산을 {@link User#carryOverDebt} 하나로
     * 모아 두 곳이 갈라질 수 없게 했다.
     *
     * <p>소멸분도 이월을 반영한다 — 이전 연도 실제 사용분은 {@code use − carriedUse}이고,
     * 이월되는 보너스는 사라지지 않는다.
     * <pre>expired = max(0, (base + bonus) − (use − carriedUse) − carriedBonus)</pre>
     * {@code carriedUse}·{@code carriedBonus}가 0이면 종전 식({@code max(0, 잔여)})과 완전히 같다.
     *
     * @param policyBaseDays 근속년수 정책이 정한 새 연차 (채무를 빼기 <b>전</b> 값)
     * @param carriedUse     기산일 이후 날짜의 선차감 유지분
     * @param carriedBonus   다음 연도로 이월되는 보너스
     */
    public static LeaveResetHistory create(User user, LocalDate resetDate, BigDecimal policyBaseDays,
                                           BigDecimal carriedUse, BigDecimal carriedBonus) {
        BigDecimal bonus = user.getBonusDays() != null ? user.getBonusDays() : BigDecimal.ZERO;
        BigDecimal oldYearUse = user.getUseDays().subtract(carriedUse);
        BigDecimal debt = user.carryOverDebt(carriedUse);
        BigDecimal expired = user.getBaseDays().add(bonus).subtract(oldYearUse).subtract(carriedBonus)
                .max(BigDecimal.ZERO);
        return LeaveResetHistory.builder()
                .user(user)
                .resetDate(resetDate)
                .prevBaseDays(user.getBaseDays())
                .prevUseDays(user.getUseDays())
                .expiredDays(expired)
                .advanceSettled(debt)
                .newBaseDays(policyBaseDays.subtract(debt))
                .carriedBonusDays(carriedBonus)
                .build();
    }
}
