package com.mlsoft.backend.domain.email.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

/**
 * 메일 발신 계정 자격 증명.
 *
 * <p>원문 비밀번호나 앱 비밀번호는 저장하지 않고 {@code encryptedSecret}에
 * 외부 암호화기가 만든 암호문만 보관한다. DB 행이 없을 때는 다음 파도에서
 * 환경변수 fallback을 사용한다.</p>
 */
@Entity
@Table(
        name = "mail_credentials",
        uniqueConstraints = @UniqueConstraint(
                name = "uk_mail_credentials_provider",
                columnNames = "provider"))
@Getter
@Builder
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor(access = AccessLevel.PRIVATE)
public class MailCredential {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /** 자격 증명 제공자 식별자 (예: SMTP) */
    @Column(name = "provider", nullable = false, length = 50)
    private String provider;

    /** SMTP 발신 계정 사용자명 */
    @Column(name = "username", nullable = false, length = 255)
    private String username;

    /** 암호화된 SMTP 비밀값. 원문 저장 금지 */
    @Column(name = "encrypted_secret", nullable = false, length = 1024)
    private String encryptedSecret;

    /** 현재 사용할 자격 증명인가 */
    @Column(name = "active", nullable = false)
    private boolean active;

    /** 메일 발신 계정 생성 */
    public static MailCredential create(String provider, String username, String encryptedSecret) {
        return MailCredential.builder()
                .provider(provider)
                .username(username)
                .encryptedSecret(encryptedSecret)
                .active(true)
                .build();
    }

    /** 사용자명과 암호문을 함께 교체하고 활성화한다 */
    public void rotate(String username, String encryptedSecret) {
        this.username = username;
        this.encryptedSecret = encryptedSecret;
        this.active = true;
    }

    /** 기존 사용자명을 유지한 채 암호문만 교체하고 활성화한다 */
    public void rotate(String encryptedSecret) {
        this.encryptedSecret = encryptedSecret;
        this.active = true;
    }

    /** 사용하지 않는 자격 증명을 비활성화한다 */
    public void deactivate() {
        this.active = false;
    }
}
