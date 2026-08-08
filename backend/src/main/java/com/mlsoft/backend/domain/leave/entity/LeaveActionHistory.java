package com.mlsoft.backend.domain.leave.entity;

import com.mlsoft.backend.domain.common.RequestAction;
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
import jakarta.persistence.Index;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

/**
 * 연차 처리 이력 (docs/02 3-5 leave_action_history).
 * 신청자(user)를 중복 보관하는 것은 조인 없이 로그를 조회하기 위한 반정규화 (검토 메모 4).
 */
@Entity
// 조회 인덱스 (리뷰 D-1) — 이 테이블은 append-only(수정·삭제 없음)라 인덱스 유지 비용이 insert에만 붙는다.
// 목록 3종이 모두 createdAt 내림차순 정렬이므로 필터 컬럼과 묶어 정렬까지 인덱스로 처리한다.
// action 필터는 값이 7개뿐이라 선택도가 낮아 별도 인덱스를 두지 않는다 — 위 3개로 좁힌 뒤 걸러도 충분하다.
@Table(name = "leave_action_history", indexes = {
        @Index(name = "idx_leave_history_created", columnList = "created_at"),
        @Index(name = "idx_leave_history_actor_created", columnList = "actor_id, created_at"),
        @Index(name = "idx_leave_history_user_created", columnList = "user_id, created_at")
})
@Getter
@Builder
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor(access = AccessLevel.PRIVATE)
public class LeaveActionHistory extends BaseTimeEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /** 대상 연차 신청 */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "leave_requests_id", nullable = false)
    private LeaveRequest leaveRequest;

    /** 처리자 */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "actor_id", nullable = false)
    private User actor;

    /** 처리 코멘트 */
    @Column(nullable = false)
    private String comment;

    /** 액션 (7종) */
    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private RequestAction action;

    /** 신청자 (반정규화) */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

    /** 처리 이력 생성 */
    public static LeaveActionHistory create(LeaveRequest leaveRequest, User actor,
                                            RequestAction action, String comment) {
        return LeaveActionHistory.builder()
                .leaveRequest(leaveRequest)
                .actor(actor)
                .action(action)
                .comment(comment)
                .user(leaveRequest.getUser())
                .build();
    }
}
