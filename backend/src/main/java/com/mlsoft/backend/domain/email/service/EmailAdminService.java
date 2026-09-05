package com.mlsoft.backend.domain.email.service;

import com.mlsoft.backend.domain.audit.entity.AdminAction;
import com.mlsoft.backend.domain.audit.service.AdminAuditService;
import com.mlsoft.backend.domain.email.dto.EmailBulkRequest;
import com.mlsoft.backend.domain.email.dto.EmailBulkResponse;
import com.mlsoft.backend.domain.email.dto.EmailHistoryResponse;
import com.mlsoft.backend.domain.email.dto.ReminderTarget;
import com.mlsoft.backend.domain.email.entity.EmailHistory;
import com.mlsoft.backend.domain.email.entity.EmailStatus;
import com.mlsoft.backend.domain.email.entity.EmailType;
import com.mlsoft.backend.domain.email.event.EmailDispatchEvent;
import com.mlsoft.backend.domain.email.repository.EmailHistoryRepository;
import com.mlsoft.backend.domain.user.entity.User;
import com.mlsoft.backend.domain.user.repository.UserRepository;
import com.mlsoft.backend.global.exception.BusinessException;
import com.mlsoft.backend.global.exception.ErrorCode;
import lombok.RequiredArgsConstructor;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/** 관리자 이메일 대상·일괄 발송·이력·재발송 업무 경계. */
@Service
@RequiredArgsConstructor
public class EmailAdminService {

    public static final int MAX_BULK_PER_REQUEST = 100;
    public static final int MAX_BULK_PER_DAY = 400;
    private static final ZoneId KST = ZoneId.of("Asia/Seoul");

    private final EmailHistoryRepository emailHistoryRepository;
    private final UserRepository userRepository;
    private final LeaveReminderService leaveReminderService;
    private final ApplicationEventPublisher eventPublisher;
    private final AdminAuditService adminAuditService;
    private final Clock clock;

    /** 자동 주기와 무관하게 관리자 화면에 표시할 기산일 임박 대상. */
    @Transactional(readOnly = true)
    public List<ReminderTarget> findReminderTargets() {
        return leaveReminderService.findReminderTargets(LocalDate.now(clock));
    }

    /** NOTICE 아웃박스 이력을 만들고 커밋 후 발송 이벤트를 발행한다. */
    @Transactional
    public EmailBulkResponse bulk(EmailBulkRequest request, Long actorId) {
        if (request == null || request.userIds() == null
                || request.userIds().isEmpty()
                || request.userIds().size() > MAX_BULK_PER_REQUEST
                || request.userIds().stream().anyMatch(java.util.Objects::isNull)) {
            throw new BusinessException(ErrorCode.EMAIL_BULK_LIMIT_EXCEEDED);
        }
        if (request.title() == null || request.title().isBlank()
                || request.content() == null || request.content().isBlank()) {
            throw new BusinessException(ErrorCode.INVALID_INPUT_VALUE);
        }
        long todayCount = countToday();
        if (todayCount + request.userIds().size() > MAX_BULK_PER_DAY) {
            throw new BusinessException(ErrorCode.EMAIL_BULK_LIMIT_EXCEEDED);
        }

        Map<Long, User> users = new LinkedHashMap<>();
        userRepository.findAllById(request.userIds()).forEach(user -> users.put(user.getId(), user));
        List<Long> historyIds = new java.util.ArrayList<>();
        int skipped = 0;
        for (Long userId : request.userIds().stream().distinct().toList()) {
            User user = users.get(userId);
            if (user == null || !user.isActive() || user.getEmail() == null || user.getEmail().isBlank()) {
                skipped++;
                continue;
            }
            EmailHistory history = emailHistoryRepository.save(
                    EmailHistory.create(user, null, EmailType.NOTICE,
                            request.title(), request.content()));
            historyIds.add(history.getId());
        }
        if (!historyIds.isEmpty()) {
            eventPublisher.publishEvent(new EmailDispatchEvent(historyIds));
        }
        int requested = request.userIds().size();
        int queued = historyIds.size();
        skipped += Math.max(0, requested - request.userIds().stream().distinct().count());
        adminAuditService.recordEmailAction(
                actorId,
                AdminAction.EMAIL_BULK_SENT,
                "이메일 일괄 발송",
                "requested=" + requested + ", queued=" + queued + ", skipped=" + skipped);
        return new EmailBulkResponse(requested, queued, skipped);
    }

    /** 관리자 이메일 발송 이력 목록. 필터 값은 명시된 enum만 허용한다. */
    @Transactional(readOnly = true)
    public Page<EmailHistoryResponse> findHistories(String type, String status, Pageable pageable) {
        return emailHistoryRepository.searchForAdmin(parseType(type), parseStatus(status), pageable)
                .map(EmailHistoryResponse::of);
    }

    /** FAILED 이력만 retry_count를 초기화해 다시 PENDING으로 만든다. */
    @Transactional
    public EmailHistoryResponse resend(Long historyId, Long actorId) {
        EmailHistory history = emailHistoryRepository.findByIdWithUser(historyId)
                .orElseThrow(() -> new BusinessException(ErrorCode.EMAIL_HISTORY_NOT_FOUND));
        if (history.getStatus() != EmailStatus.FAILED) {
            throw new BusinessException(ErrorCode.EMAIL_RESEND_NOT_ALLOWED);
        }
        history.resetForResend();
        emailHistoryRepository.save(history);
        eventPublisher.publishEvent(new EmailDispatchEvent(List.of(history.getId())));
        adminAuditService.recordEmailAction(
                actorId,
                AdminAction.EMAIL_RESENT,
                "email_history:" + historyId,
                "historyId=" + historyId);
        return EmailHistoryResponse.of(history);
    }

    private long countToday() {
        LocalDate today = LocalDate.now(clock.withZone(KST));
        LocalDateTime from = today.atStartOfDay();
        LocalDateTime to = today.plusDays(1).atStartOfDay();
        return emailHistoryRepository.countByCreatedAtGreaterThanEqualAndCreatedAtLessThan(from, to);
    }

    private EmailType parseType(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        try {
            return EmailType.valueOf(value.trim().toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException e) {
            throw new BusinessException(ErrorCode.INVALID_INPUT_VALUE);
        }
    }

    private EmailStatus parseStatus(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        try {
            return EmailStatus.valueOf(value.trim().toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException e) {
            throw new BusinessException(ErrorCode.INVALID_INPUT_VALUE);
        }
    }
}
