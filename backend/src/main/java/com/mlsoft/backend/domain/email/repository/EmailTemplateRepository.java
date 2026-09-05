package com.mlsoft.backend.domain.email.repository;

import com.mlsoft.backend.domain.email.entity.EmailTemplate;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

/** 이메일 양식 저장소. */
public interface EmailTemplateRepository extends JpaRepository<EmailTemplate, Long> {

    /** 양식 키로 한 건을 조회한다 */
    Optional<EmailTemplate> findByTemplateKey(String templateKey);
}
