package com.mlsoft.backend.domain.welfare.entity;

import com.mlsoft.backend.domain.common.RequestStatus;
import com.mlsoft.backend.domain.user.entity.User;
import com.mlsoft.backend.global.entity.BaseTimeEntity;
import com.mlsoft.backend.global.exception.BusinessException;
import com.mlsoft.backend.global.exception.ErrorCode;
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
import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;

/**
 * 복리후생 신청 (docs/02 3-7 welfare_requests).
 * - category/target/evidence_guide는 신청 시점 정책 값의 스냅샷 (정책이 나중에 바뀌어도 근거 보존)
 * - 승인 구조·처리 방식은 연차와 동일 (primary/sub 병렬 선착순)
 * - 원문 오타 cetegory는 category로 바로잡아 구현 (docs/02 주석)
 */
@Entity
@Table(name = "welfare_requests")
@Getter
@Builder
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor(access = AccessLevel.PRIVATE)
public class WelfareRequest extends BaseTimeEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /** 근거 정책 */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "policy_id", nullable = false)
    private WelfarePolicy policy;

    /** 신청자 */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

    /** 기본 승인자 id (docs/02 원문 기준 FK 없이 보관) */
    @Column(name = "primary_approver_id", nullable = false)
    private Long primaryApproverId;

    /** 서브 승인자 id */
    @Column(name = "sub_approver_id")
    private Long subApproverId;

    /** 카테고리 (신청 시점 스냅샷) */
    @Column(nullable = false)
    private String category;

    /** 대상 (신청 시점 스냅샷) */
    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private WelfareTarget target;

    /** 제출자료 안내 (신청 시점 스냅샷) */
    @Column(nullable = false)
    private String evidenceGuide;

    /**
     * 부여 일수 (신청 시점 policy.default_days 스냅샷 — docs/02 3-7 메모 7 확정).
     * 정책이 나중에 바뀌어도 이 신청 건의 부여 근거는 유지되며, 승인 시 이 값으로 bonus_days를 가산한다.
     */
    @Column(name = "add_days", nullable = false, precision = 4, scale = 1)
    private BigDecimal addDays;

    /** 자세한 사유 */
    @Column(nullable = false)
    private String reason;

    /** 신청 상태 */
    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private RequestStatus status;

    /**
     * 복리후생 신청 생성 — 정책 값(구분·대상·제출자료·부여일수)을 스냅샷으로 복사, PENDING으로 시작.
     */
    public static WelfareRequest create(WelfarePolicy policy, User user, String reason,
                                        Long primaryApproverId, Long subApproverId) {
        return WelfareRequest.builder()
                .policy(policy)
                .user(user)
                .reason(reason)
                .primaryApproverId(primaryApproverId)
                .subApproverId(subApproverId)
                .category(policy.getCategory())
                .target(policy.getTarget())
                .evidenceGuide(policy.getDefaultEvidence())
                .addDays(policy.getDefaultDays())
                .status(RequestStatus.PENDING)
                .build();
    }

    /** 승인 — PENDING에서만 가능. bonus_days 가산은 서비스 책임. */
    public void approve() {
        validateStatus(RequestStatus.PENDING);
        this.status = RequestStatus.APPROVED;
    }

    /** 반려 — PENDING에서만 가능. */
    public void reject() {
        validateStatus(RequestStatus.PENDING);
        this.status = RequestStatus.REJECTED;
    }

    /** 취소 — PENDING에서만 가능 (승인 후 취소 정책은 서비스 구현 시 확정). */
    public void cancel() {
        validateStatus(RequestStatus.PENDING);
        this.status = RequestStatus.CANCELLED;
    }

    // 상태 전이 가드 — 기대 상태가 아니면 이미 처리된 신청
    private void validateStatus(RequestStatus expected) {
        if (this.status != expected) {
            throw new BusinessException(ErrorCode.ALREADY_PROCESSED);
        }
    }

    /** 기본 승인자 재배정 — 팀장 퇴직 시 결재 이관 (갭분석 B-4, docs/01 2-9) */
    public void reassignPrimaryApprover(Long newApproverId) {
        this.primaryApproverId = newApproverId;
    }

    /** 서브 승인자 재배정 — 팀장 퇴직 시 결재 이관 (갭분석 B-4, docs/01 2-9) */
    public void reassignSubApprover(Long newApproverId) {
        this.subApproverId = newApproverId;
    }
}
