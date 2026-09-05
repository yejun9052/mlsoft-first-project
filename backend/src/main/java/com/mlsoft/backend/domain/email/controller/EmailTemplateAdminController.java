package com.mlsoft.backend.domain.email.controller;

import com.mlsoft.backend.domain.email.dto.EmailPreviewResponse;
import com.mlsoft.backend.domain.email.dto.EmailTemplateResponse;
import com.mlsoft.backend.domain.email.dto.EmailTemplateUpdateRequest;
import com.mlsoft.backend.domain.email.service.EmailTemplateAdminService;
import com.mlsoft.backend.global.response.CommonResponse;
import com.mlsoft.backend.global.response.ResponseMessage;
import com.mlsoft.backend.security.AuthUser;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/** 관리자 이메일 양식 API. */
@RestController
@RequestMapping("/api/admin/email-templates")
@RequiredArgsConstructor
public class EmailTemplateAdminController {

    private final EmailTemplateAdminService emailTemplateAdminService;

    @GetMapping
    @PreAuthorize("hasRole('SYSTEM_ADMIN')")
    public ResponseEntity<CommonResponse<List<EmailTemplateResponse>>> findAll() {
        return ResponseEntity.ok(CommonResponse.success(
                ResponseMessage.EMAIL_TEMPLATES_FETCHED,
                emailTemplateAdminService.findAll()));
    }

    @PutMapping("/{templateKey}")
    @PreAuthorize("hasRole('SYSTEM_ADMIN')")
    public ResponseEntity<CommonResponse<EmailTemplateResponse>> update(
            @PathVariable String templateKey,
            @Valid @RequestBody EmailTemplateUpdateRequest request,
            @AuthenticationPrincipal AuthUser authUser
    ) {
        return ResponseEntity.ok(CommonResponse.success(
                ResponseMessage.EMAIL_TEMPLATE_UPDATED,
                emailTemplateAdminService.update(templateKey, request, authUser.id())));
    }

    @PostMapping("/{templateKey}/preview")
    @PreAuthorize("hasRole('SYSTEM_ADMIN')")
    public ResponseEntity<CommonResponse<EmailPreviewResponse>> preview(
            @PathVariable String templateKey,
            @Valid @RequestBody EmailTemplateUpdateRequest request
    ) {
        return ResponseEntity.ok(CommonResponse.success(
                ResponseMessage.EMAIL_TEMPLATE_PREVIEW_CREATED,
                emailTemplateAdminService.preview(templateKey, request)));
    }
}
