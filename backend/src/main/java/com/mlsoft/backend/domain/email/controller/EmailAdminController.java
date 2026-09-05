package com.mlsoft.backend.domain.email.controller;

import com.mlsoft.backend.domain.email.dto.EmailBulkRequest;
import com.mlsoft.backend.domain.email.dto.EmailBulkResponse;
import com.mlsoft.backend.domain.email.dto.EmailHistoryResponse;
import com.mlsoft.backend.domain.email.dto.ReminderTarget;
import com.mlsoft.backend.domain.email.service.EmailAdminService;
import com.mlsoft.backend.global.response.CommonResponse;
import com.mlsoft.backend.global.response.ResponseMessage;
import com.mlsoft.backend.security.AuthUser;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/** 관리자 이메일 대상·발송 이력 API. */
@RestController
@RequestMapping("/api/emails")
@RequiredArgsConstructor
public class EmailAdminController {

    private final EmailAdminService emailAdminService;

    @GetMapping("/reminder-targets")
    @PreAuthorize("hasRole('SYSTEM_ADMIN')")
    public ResponseEntity<CommonResponse<List<ReminderTarget>>> reminderTargets() {
        return ResponseEntity.ok(CommonResponse.success(
                ResponseMessage.EMAIL_REMINDER_TARGETS_FETCHED,
                emailAdminService.findReminderTargets()));
    }

    @PostMapping("/bulk")
    @PreAuthorize("hasRole('SYSTEM_ADMIN')")
    public ResponseEntity<CommonResponse<EmailBulkResponse>> bulk(
            @Valid @RequestBody EmailBulkRequest request,
            @AuthenticationPrincipal AuthUser authUser
    ) {
        return ResponseEntity.ok(CommonResponse.success(
                ResponseMessage.EMAIL_BULK_QUEUED,
                emailAdminService.bulk(request, authUser.id())));
    }

    @GetMapping
    @PreAuthorize("hasRole('SYSTEM_ADMIN')")
    public ResponseEntity<CommonResponse<Page<EmailHistoryResponse>>> histories(
            @RequestParam(required = false) String type,
            @RequestParam(required = false) String status,
            @PageableDefault(sort = "createdAt", direction = Sort.Direction.DESC) Pageable pageable
    ) {
        return ResponseEntity.ok(CommonResponse.success(
                ResponseMessage.EMAIL_HISTORY_FETCHED,
                emailAdminService.findHistories(type, status, pageable)));
    }

    @PostMapping("/{id}/resend")
    @PreAuthorize("hasRole('SYSTEM_ADMIN')")
    public ResponseEntity<CommonResponse<EmailHistoryResponse>> resend(
            @PathVariable Long id,
            @AuthenticationPrincipal AuthUser authUser
    ) {
        return ResponseEntity.ok(CommonResponse.success(
                ResponseMessage.EMAIL_RESENT,
                emailAdminService.resend(id, authUser.id())));
    }
}
