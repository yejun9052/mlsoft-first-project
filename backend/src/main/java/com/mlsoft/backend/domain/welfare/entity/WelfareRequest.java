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

/**
 * 복리후생 신청 (docs/02 3-7 welfare_requests).
 * - category/target/evidence_guide는 신청 시점 정책 값의 스냅샷 (정책이 나중에 바뀌어도 근거 보존)
 * - 승인 구조·처리 방식은 연차와 동일 (primary/sub 병렬 선착순)
 * - 원문 오타 cetegory는 category로 바로잡아 구현 (docs/02 주석)
 */
@Entity
// 조회 인덱스 (리뷰 D-2) — 연차 신청과 대칭으로 빠져 있었다.
// 목록 3종이 모두 "누구의 + 어떤 상태" 조합이다: 내 신청(user), 결재 대기(primary·sub 승인자).
// FK가 만드는 단일 인덱스는 컬럼 하나뿐이라 상태까지 걸러 주지 못한다.
@Table(name = "welfare_requests", indexes = {
        @Index(name = "idx_welfare_requests_user_status", columnList = "user_id, status"),
        @Index(name = "idx_welfare_requests_primary_status", columnList = "primary_approver_id, status"),
        @Index(name = "idx_welfare_requests_sub_status", columnList = "sub_approver_id, status")
})
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

    /**
     * 기본 승인자 (리뷰 D-5).
     *
     * <p>원래 FK 없는 raw {@code Long}이었다(docs/02 원문 기준). 컬럼은 그대로 두고 연관만 얹었으므로
     * 스키마상 달라지는 것은 FK 제약뿐이다. 바꾼 이유:
     * <ul>
     *   <li>존재하지 않는 사원 id가 들어가도 DB가 막지 않았다 — 연차({@code LeaveRequest})는 FK가 있어
     *       같은 자리에서 규칙이 갈려 있었다</li>
     *   <li>승인자 이름을 응답에 담으려면 서비스가 별도 조회를 돌려야 했다.
     *       이제 {@code @EntityGraph}로 합칠 수 있다</li>
     * </ul>
     *
     * <p><b>LAZY 프록시의 {@code getId()}는 DB를 보지 않는다</b> — FK 값이 프록시에 이미 있다.
     * 그래서 id만 쓰는 곳(응답 DTO·승인자 판별)은 연관으로 바꿔도 쿼리가 늘지 않는다.
     */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "primary_approver_id", nullable = false)
    private User primaryApprover;

    /** 서브 승인자 — 선택 사항이라 nullable */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "sub_approver_id")
    private User subApprover;

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

    /** 자세한 사유. 파기 시 개인정보 보호를 위해 NULL로 익명화할 수 있다. */
    @Column
    private String reason;

    /** 신청 상태 */
    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private RequestStatus status;

    /**
     * 복리후생 신청 생성 — 정책 값(구분·대상·제출자료·부여일수)을 스냅샷으로 복사, PENDING으로 시작.
     */
    public static WelfareRequest create(WelfarePolicy policy, User user, String reason,
                                        User primaryApprover, User subApprover) {
        return WelfareRequest.builder()
                .policy(policy)
                .user(user)
                .reason(reason)
                .primaryApprover(primaryApprover)
                .subApprover(subApprover)
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

    /** 재입사로 이전 근속의 살아 있는 신청을 종결한다 — 승인 완료 건은 보존한다. */
    public void cancelByRehire() {
        if (status != RequestStatus.PENDING && status != RequestStatus.CANCEL_PENDING) {
            throw new BusinessException(ErrorCode.ALREADY_PROCESSED);
        }
        this.status = RequestStatus.CANCELLED;
    }

    // 상태 전이 가드 — 기대 상태가 아니면 이미 처리된 신청
    private void validateStatus(RequestStatus expected) {
        if (this.status != expected) {
            throw new BusinessException(ErrorCode.ALREADY_PROCESSED);
        }
    }

    /** 기본 승인자 재배정 — 팀장 퇴직 시 결재 이관 (갭분석 B-4, docs/01 2-9) */
    public void reassignPrimaryApprover(User newApprover) {
        this.primaryApprover = newApprover;
    }

    /** 서브 승인자 재배정 — 팀장 퇴직 시 결재 이관 (갭분석 B-4, docs/01 2-9) */
    public void reassignSubApprover(User newApprover) {
        this.subApprover = newApprover;
    }

    /**
     * 이 사람이 결재할 수 있는가 — primary 또는 sub (병렬 선착순).
     * 판별을 도메인에 두면 서비스마다 프록시에서 id를 꺼내는 코드가 흩어지지 않는다.
     * LAZY 프록시의 {@code getId()}는 DB를 보지 않으므로 쿼리가 나가지 않는다.
     */
    public boolean isApprover(Long actorId) {
        return primaryApprover.getId().equals(actorId)
                || (subApprover != null && subApprover.getId().equals(actorId));
    }
}
