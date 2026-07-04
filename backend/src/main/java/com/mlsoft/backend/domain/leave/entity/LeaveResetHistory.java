package com.mlsoft.backend.domain.leave.entity;

import com.mlsoft.backend.domain.user.entity.User;
import com.mlsoft.backend.global.entity.BaseTimeEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
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
@Table(name = "leave_reset_history")
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

    /** 소멸된 연차 (이월 없음) */
    @Column(nullable = false, precision = 4, scale = 1)
    private BigDecimal expiredDays;

    /** 정산된 당겨쓰기 */
    @Column(nullable = false, precision = 4, scale = 1)
    private BigDecimal advanceSettled;

    /** 리셋 후 총 연차 */
    @Column(nullable = false, precision = 4, scale = 1)
    private BigDecimal newBaseDays;

    /**
     * 리셋 이력 생성 — user.resetAnnualLeave 호출 "직전" 상태를 스냅샷으로 기록한다.
     */
    public static LeaveResetHistory create(User user, LocalDate resetDate, BigDecimal newBaseDays) {
        BigDecimal expired = user.getRemainingDays().max(BigDecimal.ZERO);
        return LeaveResetHistory.builder()
                .user(user)
                .resetDate(resetDate)
                .prevBaseDays(user.getBaseDays())
                .prevUseDays(user.getUseDays())
                .expiredDays(expired)
                .advanceSettled(user.getAdvanceDays())
                .newBaseDays(newBaseDays.subtract(user.getAdvanceDays()))
                .build();
    }
}
