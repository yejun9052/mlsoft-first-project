package com.mlsoft.backend.domain.holiday.credential;

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
 * 외부 공휴일 제공자별 API 자격 증명.
 *
 * <p>원문 키는 저장하지 않고 {@code encryptedApiKey}에 AES-GCM 결과만 저장한다.
 * 날짜별 {@code Holiday} 행에는 키를 복제하지 않는다.</p>
 */
@Entity
@Table(
        name = "holiday_api_credentials",
        uniqueConstraints = @UniqueConstraint(
                name = "uk_holiday_api_credentials_provider",
                columnNames = "provider"))
@Getter
@Builder
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor(access = AccessLevel.PRIVATE)
public class HolidayApiCredential {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, length = 50)
    private String provider;

    @Column(name = "encrypted_api_key", nullable = false, length = 1024)
    private String encryptedApiKey;

    @Column(nullable = false)
    private boolean active;

    public static HolidayApiCredential create(String provider, String encryptedApiKey) {
        return HolidayApiCredential.builder()
                .provider(provider)
                .encryptedApiKey(encryptedApiKey)
                .active(true)
                .build();
    }

    public void rotate(String encryptedApiKey) {
        this.encryptedApiKey = encryptedApiKey;
        this.active = true;
    }

    public void deactivate() {
        this.active = false;
    }
}
