package com.mlsoft.backend.domain.welfare.entity;

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
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

/**
 * 복리후생 처리 이력 (docs/02 3-8 welfare_action_history).
 * 액션 체계는 연차와 동일 (RequestAction 7종).
 */
@Entity
@Table(name = "welfare_action_history")
@Getter
@Builder
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor(access = AccessLevel.PRIVATE)
public class WelfareActionHistory extends BaseTimeEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /** 대상 복리후생 신청 */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "welfare_request_id", nullable = false)
    private WelfareRequest welfareRequest;

    /** 처리자 */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "actor_id", nullable = false)
    private User actor;

    /** 신청자 (반정규화) */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

    /** 액션 (연차와 동일 체계) */
    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private RequestAction action;

    /** 처리자 코멘트 */
    @Column(nullable = false)
    private String comment;

    /** 처리 이력 생성 */
    public static WelfareActionHistory create(WelfareRequest welfareRequest, User actor,
                                              RequestAction action, String comment) {
        return WelfareActionHistory.builder()
                .welfareRequest(welfareRequest)
                .actor(actor)
                .action(action)
                .comment(comment)
                .user(welfareRequest.getUser())
                .build();
    }
}
