package com.mlsoft.backend.domain.email.repository;

import com.mlsoft.backend.domain.email.entity.MailCredential;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

/** 메일 발신 계정 자격 증명 저장소. */
public interface MailCredentialRepository extends JpaRepository<MailCredential, Long> {

    /** 활성화된 제공자별 자격 증명 조회 */
    Optional<MailCredential> findByProviderAndActiveTrue(String provider);

    /** 제공자별 자격 증명 조회 */
    Optional<MailCredential> findByProvider(String provider);
}
