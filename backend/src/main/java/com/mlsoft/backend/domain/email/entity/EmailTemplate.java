package com.mlsoft.backend.domain.email.entity;

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
import jakarta.persistence.UniqueConstraint;
import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.springframework.data.annotation.LastModifiedDate;

import java.time.LocalDateTime;

/**
 * 관리자 편집 이메일 양식.
 *
 * <p>본문은 평문 템플릿으로 저장하고 실제 HTML escaping·변수 치환은
 * 다음 파도의 발송 서비스가 담당한다. {@link BaseTimeEntity}의 생성 시각과
 * 별도 수정 시각·버전·수정자를 함께 기록해 양식 변경 이력을 보존한다.</p>
 */
@Entity
@Table(
        name = "email_templates",
        uniqueConstraints = @UniqueConstraint(
                name = "uk_email_templates_template_key",
                columnNames = "template_key"))
@Getter
@Builder
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor(access = AccessLevel.PRIVATE)
public class EmailTemplate extends BaseTimeEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /** 양식 식별 키 (예: LEAVE_BALANCE_REMINDER) */
    @Column(name = "template_key", nullable = false, length = 100)
    private String templateKey;

    /** 메일 제목 템플릿 */
    @Column(name = "subject_template", nullable = false, length = 255)
    private String subjectTemplate;

    /** 메일 본문 템플릿 */
    @Column(name = "body_template", nullable = false, columnDefinition = "TEXT")
    private String bodyTemplate;

    /** 양식 수정 버전 — 최초 생성은 1부터 시작한다 */
    @Column(name = "version", nullable = false)
    @Builder.Default
    private int version = 1;

    /** 마지막으로 양식을 수정한 관리자 */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "updated_by")
    private User updatedBy;

    /** 마지막 수정 시각 */
    @LastModifiedDate
    @Column(name = "updated_at")
    private LocalDateTime updatedAt;

    /** 이메일 양식 생성 */
    public static EmailTemplate create(String templateKey, String subject, String body) {
        return EmailTemplate.builder()
                .templateKey(templateKey)
                .subjectTemplate(subject)
                .bodyTemplate(body)
                .build();
    }

    /** 관리자 수정으로 처음 저장하는 양식 — 최초 버전부터 수정자를 남긴다. */
    public static EmailTemplate create(String templateKey, String subject, String body, User updatedBy) {
        return EmailTemplate.builder()
                .templateKey(templateKey)
                .subjectTemplate(subject)
                .bodyTemplate(body)
                .updatedBy(updatedBy)
                .build();
    }

    /** 제목·본문을 함께 변경한다 */
    public void update(String subject, String body) {
        update(subject, body, null);
    }

    /** 제목·본문과 수정자를 함께 변경하고 버전을 올린다 */
    public void update(String subject, String body, User updatedBy) {
        this.subjectTemplate = subject;
        this.bodyTemplate = body;
        this.updatedBy = updatedBy;
        this.version += 1;
    }

}
