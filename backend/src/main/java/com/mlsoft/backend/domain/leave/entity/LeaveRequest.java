package com.mlsoft.backend.domain.leave.entity;

import com.mlsoft.backend.domain.common.RequestStatus;
import com.mlsoft.backend.domain.user.entity.User;
import com.mlsoft.backend.global.entity.BaseTimeEntity;
import com.mlsoft.backend.global.exception.BusinessException;
import com.mlsoft.backend.global.exception.ErrorCode;
import jakarta.persistence.CollectionTable;
import jakarta.persistence.Column;
import jakarta.persistence.ElementCollection;
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
import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

/**
 * 연차 신청 (docs/02 3-3 leave_requests + 3-4 leave_dates).
 * - 신청(PENDING) 시 use_days 선차감, 반려/취소 시 복구 — 차감·복구는 서비스에서 User 도메인 메서드 호출
 * - 승인은 primary/sub 병렬 선착순: 먼저 처리한 1명으로 종료, 이중 처리는 ALREADY_PROCESSED (검증 R-5)
 */
@Entity
@Table(name = "leave_requests")
@Getter
@Builder
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor(access = AccessLevel.PRIVATE)
public class LeaveRequest extends BaseTimeEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /** 신청자 */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

    /** 연차/반차 */
    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private LeaveType leaveType;

    /** 사용 개수 (leave_type × 날짜 수) */
    @Column(nullable = false, precision = 4, scale = 1)
    private BigDecimal days;

    /** 신청 사유 (필수) */
    @Column(nullable = false)
    private String requestReason;

    /** 취소 사유 */
    private String cancelReason;

    /** 기본 승인자 — 부서 팀장, 공석 시 SYSTEM_ADMIN fallback (검증 Y-3) */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "primary_approver_id", nullable = false)
    private User primaryApprover;

    /** 서브 승인자 — 재직 중 TEAM_LEADER·SYSTEM_ADMIN 중 신청자가 선택 (선택 사항) */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "sub_approver_id")
    private User subApprover;

    /** 신청 상태 */
    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private RequestStatus status;

    /** 사용 날짜 목록 (leave_dates 테이블) */
    @ElementCollection
    @CollectionTable(name = "leave_dates", joinColumns = @JoinColumn(name = "leave_requests_id"))
    @Column(name = "day", nullable = false)
    @Builder.Default
    private List<LocalDate> dates = new ArrayList<>();

    /**
     * 연차 신청 생성 — PENDING으로 시작 (선차감은 서비스에서 user.deductLeave 호출).
     */
    public static LeaveRequest create(User user, LeaveType leaveType, List<LocalDate> dates,
                                      String requestReason, User primaryApprover, User subApprover) {
        return LeaveRequest.builder()
                .user(user)
                .leaveType(leaveType)
                .dates(new ArrayList<>(dates))
                .days(leaveType.getDaysPerDate().multiply(BigDecimal.valueOf(dates.size())))
                .requestReason(requestReason)
                .primaryApprover(primaryApprover)
                .subApprover(subApprover)
                .status(RequestStatus.PENDING)
                .build();
    }

    /**
     * 승인 — PENDING에서만 가능 (선착순 이중 처리 방지).
     */
    public void approve() {
        validateStatus(RequestStatus.PENDING);
        this.status = RequestStatus.APPROVED;
    }

    /**
     * 반려 — PENDING에서만 가능. 선차감 복구는 서비스 책임.
     */
    public void reject() {
        validateStatus(RequestStatus.PENDING);
        this.status = RequestStatus.REJECTED;
    }

    /**
     * 즉시 취소 (docs/01 2-3(b)).
     * - PENDING: 날짜 무관 즉시 취소
     * - APPROVED: 미래 날짜만 포함된 경우 즉시 취소 (날짜 검증은 서비스 책임)
     */
    public void cancel(String cancelReason) {
        if (status != RequestStatus.PENDING && status != RequestStatus.APPROVED) {
            throw new BusinessException(ErrorCode.ALREADY_PROCESSED);
        }
        this.status = RequestStatus.CANCELLED;
        this.cancelReason = cancelReason;
    }

    /**
     * 소급 취소 요청 — APPROVED + 과거 날짜 포함 건은 승인자 승인 필요.
     */
    public void requestCancel(String cancelReason) {
        validateStatus(RequestStatus.APPROVED);
        this.status = RequestStatus.CANCEL_PENDING;
        this.cancelReason = cancelReason;
    }

    /**
     * 소급 취소 승인 — CANCELLED 확정. 선차감 복구는 서비스 책임.
     */
    public void approveCancel() {
        validateStatus(RequestStatus.CANCEL_PENDING);
        this.status = RequestStatus.CANCELLED;
    }

    /**
     * 소급 취소 거부 — APPROVED 복원.
     */
    public void rejectCancel() {
        validateStatus(RequestStatus.CANCEL_PENDING);
        this.status = RequestStatus.APPROVED;
    }

    // 상태 전이 가드 — 기대 상태가 아니면 이미 처리된 신청
    private void validateStatus(RequestStatus expected) {
        if (this.status != expected) {
            throw new BusinessException(ErrorCode.ALREADY_PROCESSED);
        }
    }
}
