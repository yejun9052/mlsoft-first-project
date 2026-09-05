package com.mlsoft.backend.domain.email.service;

import com.mlsoft.backend.domain.audit.entity.AdminAction;
import com.mlsoft.backend.domain.audit.service.AdminAuditService;
import com.mlsoft.backend.domain.email.dto.EmailPreviewResponse;
import com.mlsoft.backend.domain.email.dto.EmailTemplateResponse;
import com.mlsoft.backend.domain.email.dto.EmailTemplateUpdateRequest;
import com.mlsoft.backend.domain.email.entity.EmailTemplate;
import com.mlsoft.backend.domain.email.event.ReminderTemplateData;
import com.mlsoft.backend.domain.email.repository.EmailTemplateRepository;
import com.mlsoft.backend.domain.user.entity.User;
import com.mlsoft.backend.domain.user.repository.UserRepository;
import com.mlsoft.backend.global.exception.BusinessException;
import com.mlsoft.backend.global.exception.ErrorCode;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Optional;

/** 관리자 이메일 양식 조회·수정·미리보기. */
@Service
@RequiredArgsConstructor
public class EmailTemplateAdminService {

    private static final int MAX_BODY_LENGTH = 20_000;

    private final EmailTemplateRepository emailTemplateRepository;
    private final EmailTemplateFactory emailTemplateFactory;
    private final UserRepository userRepository;
    private final AdminAuditService adminAuditService;

    /** 현재 지원하는 리마인더 양식 한 종을 반환한다. */
    @Transactional(readOnly = true)
    public List<EmailTemplateResponse> findAll() {
        return List.of(toResponse(emailTemplateRepository
                .findByTemplateKey(EmailTemplateFactory.REMINDER_TEMPLATE_KEY)
                .orElse(null)));
    }

    /** DB 양식이 없으면 코드 기본 양식을 version 0으로 표시한다. */
    @Transactional
    public EmailTemplateResponse update(
            String templateKey,
            EmailTemplateUpdateRequest request,
            Long actorId
    ) {
        validateKey(templateKey);
        validateContent(request);
        User actor = userRepository.findById(actorId)
                .orElseThrow(() -> new BusinessException(ErrorCode.USER_NOT_FOUND));
        Optional<EmailTemplate> existing = emailTemplateRepository
                .findByTemplateKey(EmailTemplateFactory.REMINDER_TEMPLATE_KEY);
        EmailTemplate template = existing.orElseGet(() -> EmailTemplate.create(
                EmailTemplateFactory.REMINDER_TEMPLATE_KEY,
                request.subjectTemplate(),
                request.bodyTemplate(),
                actor));
        if (existing.isPresent()) {
            template.update(request.subjectTemplate(), request.bodyTemplate(), actor);
        }
        EmailTemplate saved = emailTemplateRepository.save(template);
        adminAuditService.recordEmailAction(
                actorId,
                AdminAction.EMAIL_TEMPLATE_CHANGED,
                templateKey,
                "templateKey=" + templateKey + ", version=" + saved.getVersion());
        return toResponse(saved);
    }

    /** 저장하지 않고 실제 발송과 같은 변수 치환·escaping 경로로 렌더링한다. */
    @Transactional(readOnly = true)
    public EmailPreviewResponse preview(String templateKey, EmailTemplateUpdateRequest request) {
        validateKey(templateKey);
        validateContent(request);
        ReminderTemplateData sample = new ReminderTemplateData(
                "홍길동", "5.0", "2026-09-30", "20", "");
        var rendered = emailTemplateFactory.createReminder(
                sample, request.subjectTemplate(), request.bodyTemplate());
        return new EmailPreviewResponse(rendered.title(), rendered.content());
    }

    private EmailTemplateResponse toResponse(EmailTemplate template) {
        if (template == null) {
            return new EmailTemplateResponse(
                    EmailTemplateFactory.REMINDER_TEMPLATE_KEY,
                    emailTemplateFactory.defaultReminderSubject(),
                    emailTemplateFactory.defaultReminderBody(),
                    0,
                    null,
                    null,
                    emailTemplateFactory.reminderVariables());
        }
        return new EmailTemplateResponse(
                template.getTemplateKey(),
                template.getSubjectTemplate(),
                template.getBodyTemplate(),
                template.getVersion(),
                template.getUpdatedAt(),
                template.getUpdatedBy() == null ? null : template.getUpdatedBy().getName(),
                emailTemplateFactory.reminderVariables());
    }

    private void validateKey(String templateKey) {
        if (!EmailTemplateFactory.REMINDER_TEMPLATE_KEY.equals(templateKey)) {
            throw new BusinessException(ErrorCode.EMAIL_TEMPLATE_NOT_FOUND);
        }
    }

    private void validateContent(EmailTemplateUpdateRequest request) {
        if (request == null
                || request.subjectTemplate() == null || request.subjectTemplate().isBlank()
                || request.bodyTemplate() == null || request.bodyTemplate().isBlank()) {
            throw new BusinessException(ErrorCode.EMAIL_TEMPLATE_INVALID);
        }
        if (request.bodyTemplate().length() > MAX_BODY_LENGTH) {
            throw new BusinessException(ErrorCode.EMAIL_TEMPLATE_BODY_TOO_LONG);
        }
    }
}
