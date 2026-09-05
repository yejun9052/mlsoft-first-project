package com.mlsoft.backend.domain.email.entity;

import com.mlsoft.backend.domain.user.entity.User;
import com.mlsoft.backend.global.entity.BaseTimeEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.springframework.data.annotation.LastModifiedDate;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

/**
 * 연차 소진 안내 자동 발송 업무 이력.
 *
 * <p>{@code (user, cycle, periodKey)} 유일 키가 스케줄러 인스턴스 간 중복 선점을
 * 막는다. 실제 메일 아웃박스는 {@link EmailHistory}에 두고 이 엔티티에는
 * 대상 판정 시점의 스냅샷과 연결된 이력을 기록한다.</p>
 */
@Entity
@Table(
        name = "leave_reminder_dispatch",
        uniqueConstraints = @UniqueConstraint(
                name = "uk_leave_reminder_dispatch_user_cycle_period",
                columnNames = {"user_id", "cycle", "period_key"}))
@Getter
@Builder
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor(access = AccessLevel.PRIVATE)
public class LeaveReminderDispatch extends BaseTimeEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /** 안내 대상 사원 */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

    /** 자동 발송 주기 */
    @Enumerated(EnumType.STRING)
    @Column(name = "cycle", nullable = false)
    private ReminderCycle cycle;

    /** 중복 방지 기간 키 (예: D30:2027-08-28) */
    @Column(name = "period_key", nullable = false, length = 100)
    private String periodKey;

    /** 대상 판정 기준일 */
    @Column(name = "reference_date", nullable = false)
    private LocalDate referenceDate;

    /** 대상 판정 당시의 다음 기산일 */
    @Column(name = "next_reset_date", nullable = false)
    private LocalDate nextResetDate;

    /** 발송 당시 잔여 연차 스냅샷 */
    @Column(name = "remaining_days_snapshot", nullable = false, precision = 4, scale = 1)
    private BigDecimal remainingDaysSnapshot;

    /** 자동 발송 업무 처리 결과 */
    @Enumerated(EnumType.STRING)
    @Column(name = "result", nullable = false)
    @Builder.Default
    private ReminderDispatchResult result = ReminderDispatchResult.QUEUED;

    /** 실제 발송 완료 시각. 큐에만 들어간 동안은 null이다 */
    @Column(name = "sent_at")
    private LocalDateTime sentAt;

    /** 연결된 이메일 아웃박스 이력 */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "email_history_id")
    private EmailHistory emailHistory;

    /** 마지막 수정 시각 */
    @LastModifiedDate
    @Column(name = "updated_at")
    private LocalDateTime updatedAt;

    /** 자동 발송 업무 이력 선점 행 생성 */
    public static LeaveReminderDispatch create(User user, ReminderCycle cycle, String periodKey) {
        LocalDate today = LocalDate.now();
        return create(user, cycle, periodKey, today, today, BigDecimal.ZERO);
    }

    /** 대상 판정 스냅샷을 포함한 자동 발송 업무 이력 생성 */
    public static LeaveReminderDispatch create(User user, ReminderCycle cycle, String periodKey,
                                               LocalDate referenceDate, LocalDate nextResetDate,
                                               BigDecimal remainingDaysSnapshot) {
        return LeaveReminderDispatch.builder()
                .user(user)
                .cycle(cycle)
                .periodKey(periodKey)
                .referenceDate(referenceDate)
                .nextResetDate(nextResetDate)
                .remainingDaysSnapshot(remainingDaysSnapshot)
                .result(ReminderDispatchResult.QUEUED)
                .build();
    }

    /** 생성된 이메일 아웃박스 이력을 연결한다 */
    public void attachEmailHistory(EmailHistory emailHistory) {
        this.emailHistory = emailHistory;
    }

    /** 메일 발송 완료 시각을 기록한다 */
    public void markSent() {
        this.sentAt = LocalDateTime.now();
    }

    /** 테스트·재처리에서 기준 시각을 명시해 발송 완료를 기록한다 */
    public void markSent(LocalDateTime sentAt) {
        this.sentAt = sentAt;
    }

    /** 수신자 이메일이 없어 발송하지 않은 결과를 기록한다 */
    public void markSkippedNoEmail() {
        this.result = ReminderDispatchResult.SKIPPED_NO_EMAIL;
    }
}
