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
import jakarta.persistence.Lob;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

/**
 * 이메일 발송 이력 (docs/02 3-12 email_history, 검증 R-4).
 * - 발송은 업무 트랜잭션과 분리: 커밋 후 비동기 (@Async + AFTER_COMMIT 이벤트)
 * - PENDING으로 저장 → 발송 결과에 따라 markSent/markFailed
 */
@Entity
@Table(name = "email_history")
@Getter
@Builder
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor(access = AccessLevel.PRIVATE)
public class EmailHistory extends BaseTimeEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /** 수신자 */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

    /** 발신자 (시스템 발송이면 null) */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "from_id")
    private User fromUser;

    /** 이메일 유형 */
    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private EmailType emailType;

    /** 제목 */
    @Column(nullable = false)
    private String title;

    /** 본문 (TEXT) */
    @Lob
    @Column(nullable = false)
    private String content;

    /** 발송 상태 */
    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private EmailStatus status;

    /** 실패 사유 */
    @Column(length = 500)
    private String errorMessage;

    /** 실제 발송 시각 */
    private LocalDateTime sentAt;

    /** 발송 이력 생성 — PENDING 상태로 시작 */
    public static EmailHistory create(User user, User fromUser, EmailType emailType,
                                      String title, String content) {
        return EmailHistory.builder()
                .user(user)
                .fromUser(fromUser)
                .emailType(emailType)
                .title(title)
                .content(content)
                .status(EmailStatus.PENDING)
                .build();
    }

    /** 발송 성공 처리 */
    public void markSent() {
        this.status = EmailStatus.SENT;
        this.sentAt = LocalDateTime.now();
        this.errorMessage = null;
    }

    /** 발송 실패 처리 — 실패 사유 기록 (500자 절단) */
    public void markFailed(String errorMessage) {
        this.status = EmailStatus.FAILED;
        this.errorMessage = errorMessage != null && errorMessage.length() > 500
                ? errorMessage.substring(0, 500)
                : errorMessage;
    }
}
