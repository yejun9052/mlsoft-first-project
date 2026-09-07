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

    /** 본문 — length 미지정 @Lob은 MySQL에서 TINYTEXT(255B)로 생성되므로 TEXT 명시 (검증 B1) */
    @Column(nullable = false, columnDefinition = "TEXT")
    private String content;

    /** 발송 상태 */
    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private EmailStatus status;

    /**
     * 실제 발송 시도 횟수 — <b>최초 발송 실패도 1회로 센다</b> (리뷰 D-4).
     * 재시도 대상 조건이 {@code retry_count < 3}이므로 총 시도는 최초 1회 + 재시도 2회 = 3회다.
     * 이 필드가 없으면 영구 실패 건(존재하지 않는 주소 등)을 스케줄러가 무한히 재시도한다.
     */
    @Column(nullable = false)
    @Builder.Default
    private int retryCount = 0;

    /** 실패 사유 */
    @Column(length = 500)
    private String errorMessage;

    /** 실제 발송 시각 */
    private LocalDateTime sentAt;

    /** SENDING으로 선점한 시각 — 오래된 이력 생성 시각과 구분한다. */
    @Column(name = "sending_at")
    private LocalDateTime sendingAt;

    /** 발송 이력 생성 — PENDING, 시도 횟수 0으로 시작 */
    public static EmailHistory create(User user, User fromUser, EmailType emailType,
                                      String title, String content) {
        return EmailHistory.builder()
                .user(user)
                .fromUser(fromUser)
                .emailType(emailType)
                .title(title)
                .content(content)
                .status(EmailStatus.PENDING)
                .retryCount(0)
                .build();
    }

    /** 조건부 UPDATE 선점과 같은 상태 전이를 단위 도메인 테스트에서도 표현한다. */
    public void markSending() {
        if (this.status == EmailStatus.PENDING || this.status == EmailStatus.FAILED) {
            this.status = EmailStatus.SENDING;
            this.sendingAt = LocalDateTime.now();
        }
    }

    /** 발송 성공 처리 */
    public void markSent() {
        this.status = EmailStatus.SENT;
        this.sentAt = LocalDateTime.now();
        this.sendingAt = null;
        this.errorMessage = null;
    }

    /** 발송 실패 처리 — 시도 횟수를 올리고 실패 사유를 기록한다 (500자 절단) */
    public void markFailed(String errorMessage) {
        this.status = EmailStatus.FAILED;
        this.retryCount += 1;
        this.sendingAt = null;
        this.errorMessage = errorMessage != null && errorMessage.length() > 500
                ? errorMessage.substring(0, 500)
                : errorMessage;
    }

    /** 관리자가 FAILED 이력만 다시 발송 대기열에 넣는다. */
    public void resetForResend() {
        if (this.status != EmailStatus.FAILED) {
            throw new IllegalStateException("FAILED 상태의 이력만 재발송할 수 있습니다.");
        }
        this.status = EmailStatus.PENDING;
        this.retryCount = 0;
        this.errorMessage = null;
        this.sentAt = null;
        this.sendingAt = null;
    }
}
