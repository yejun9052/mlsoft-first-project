package com.mlsoft.backend.domain.email.service;

import com.mlsoft.backend.domain.common.RequestStatus;
import com.mlsoft.backend.domain.email.entity.EmailType;
import com.mlsoft.backend.domain.email.event.EmailNotificationEvent;
import com.mlsoft.backend.domain.email.event.EmailRecipient;
import com.mlsoft.backend.domain.email.event.EmailTemplateData;
import com.mlsoft.backend.domain.email.event.EmailTemplateKind;
import com.mlsoft.backend.domain.leave.entity.LeaveRequest;
import com.mlsoft.backend.domain.user.entity.Role;
import com.mlsoft.backend.domain.user.entity.User;
import com.mlsoft.backend.domain.user.repository.UserRepository;
import com.mlsoft.backend.domain.welfare.entity.WelfareRequest;
import lombok.RequiredArgsConstructor;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;
import java.util.stream.Collectors;

/**
 * 업무 엔티티를 원시값 이메일 이벤트로 변환한다.
 *
 * <p>수신자 결정·중복 제거·사유 열람 권한을 서비스 7곳에 복제하지 않기 위한 경계다.
 */
@Component
@RequiredArgsConstructor
public class EmailNotificationPublisher {

    private static final String DATE_SEPARATOR = ", ";

    private final ApplicationEventPublisher eventPublisher;
    private final UserRepository userRepository;

    public void publishLeaveApplied(LeaveRequest leave) {
        publishLeave(
                leave,
                EmailTemplateKind.LEAVE_APPLIED,
                "",
                recipients -> {
                    addRecipient(recipients, leave.getUser(), true);
                    addRecipient(recipients, leave.getPrimaryApprover(), true);
                    addRecipient(recipients, leave.getSubApprover(), true);
                });
    }

    public void publishLeaveProcessed(LeaveRequest leave, User actor, boolean approved) {
        publishLeave(
                leave,
                approved ? EmailTemplateKind.LEAVE_APPROVED : EmailTemplateKind.LEAVE_REJECTED,
                actor.getName(),
                recipients -> {
                    addRecipient(recipients, leave.getUser(), true);
                    addRecipient(recipients, leave.getPrimaryApprover(), true);
                    addRecipient(recipients, leave.getSubApprover(), true);
                    addSystemAdmins(recipients);
                });
    }

    public void publishLeaveCancelled(LeaveRequest leave) {
        EmailTemplateKind kind = leave.getStatus() == RequestStatus.CANCEL_PENDING
                ? EmailTemplateKind.LEAVE_CANCEL_PENDING
                : EmailTemplateKind.LEAVE_CANCELLED;
        publishLeave(
                leave,
                kind,
                leave.getUser().getName(),
                recipients -> {
                    addRecipient(recipients, leave.getUser(), true);
                    addRecipient(recipients, leave.getPrimaryApprover(), true);
                    addRecipient(recipients, leave.getSubApprover(), true);
                });
    }

    public void publishLeaveCancelProcessed(LeaveRequest leave, User actor, boolean approved) {
        publishLeave(
                leave,
                approved ? EmailTemplateKind.LEAVE_CANCEL_APPROVED
                        : EmailTemplateKind.LEAVE_CANCEL_REJECTED,
                actor.getName(),
                recipients -> {
                    addRecipient(recipients, leave.getUser(), true);
                    addRecipient(recipients, leave.getPrimaryApprover(), true);
                    addRecipient(recipients, leave.getSubApprover(), true);
                    addSystemAdmins(recipients);
                });
    }

    public void publishWelfareApplied(WelfareRequest welfare) {
        publishWelfare(
                welfare,
                EmailTemplateKind.WELFARE_APPLIED,
                "",
                recipients -> {
                    addRecipient(recipients, welfare.getUser(), true);
                    addRecipient(recipients, welfare.getPrimaryApprover(), true);
                    addRecipient(recipients, welfare.getSubApprover(), true);
                });
    }

    public void publishWelfareProcessed(WelfareRequest welfare, User actor, boolean approved) {
        publishWelfare(
                welfare,
                approved ? EmailTemplateKind.WELFARE_APPROVED : EmailTemplateKind.WELFARE_REJECTED,
                actor.getName(),
                recipients -> {
                    addRecipient(recipients, welfare.getUser(), true);
                    addRecipient(recipients, welfare.getPrimaryApprover(), true);
                    addRecipient(recipients, welfare.getSubApprover(), true);
                    addSystemAdmins(recipients);
                });
    }

    public void publishBirthdayGranted(User user, LocalDate grantDate, BigDecimal days) {
        Map<Long, EmailRecipient> recipients = new LinkedHashMap<>();
        addRecipient(recipients, user, false);
        addSystemAdmins(recipients);

        publish(
                EmailType.LEAVE,
                recipients,
                new EmailTemplateData(
                        EmailTemplateKind.BIRTHDAY_LEAVE_GRANTED,
                        null,
                        user.getName(),
                        "생일 반차",
                        grantDate.toString(),
                        days.toPlainString(),
                        "",
                        ""));
    }

    private void publishLeave(
            LeaveRequest leave,
            EmailTemplateKind kind,
            String actorName,
            Consumer<Map<Long, EmailRecipient>> recipientCollector
    ) {
        Map<Long, EmailRecipient> recipients = new LinkedHashMap<>();
        recipientCollector.accept(recipients);

        String dates = leave.getDates().stream()
                .map(LocalDate::toString)
                .collect(Collectors.joining(DATE_SEPARATOR));

        publish(
                EmailType.LEAVE,
                recipients,
                new EmailTemplateData(
                        kind,
                        leave.getId(),
                        leave.getUser().getName(),
                        leave.getLeaveType().name(),
                        dates,
                        leave.getDays().toPlainString(),
                        leave.getStatus() == RequestStatus.CANCEL_PENDING
                                ? leave.getCancelReason()
                                : leave.getRequestReason(),
                        actorName));
    }

    private void publishWelfare(
            WelfareRequest welfare,
            EmailTemplateKind kind,
            String actorName,
            Consumer<Map<Long, EmailRecipient>> recipientCollector
    ) {
        Map<Long, EmailRecipient> recipients = new LinkedHashMap<>();
        recipientCollector.accept(recipients);

        publish(
                EmailType.WELFARE,
                recipients,
                new EmailTemplateData(
                        kind,
                        welfare.getId(),
                        welfare.getUser().getName(),
                        welfare.getCategory(),
                        "",
                        welfare.getAddDays().toPlainString(),
                        welfare.getReason(),
                        actorName));
    }

    private void publish(
            EmailType emailType,
            Map<Long, EmailRecipient> recipients,
            EmailTemplateData templateData
    ) {
        if (recipients.isEmpty()) {
            return;
        }
        eventPublisher.publishEvent(new EmailNotificationEvent(
                emailType,
                List.copyOf(recipients.values()),
                templateData));
    }

    private void addSystemAdmins(Map<Long, EmailRecipient> recipients) {
        userRepository.findByRoleAndIsActiveTrue(Role.SYSTEM_ADMIN)
                .forEach(admin -> addRecipient(recipients, admin, true));
    }

    private void addRecipient(
            Map<Long, EmailRecipient> recipients,
            User user,
            boolean reasonVisible
    ) {
        if (user == null || !user.isActive()) {
            return;
        }
        recipients.merge(
                user.getId(),
                new EmailRecipient(user.getId(), reasonVisible),
                (existing, added) -> new EmailRecipient(
                        existing.userId(),
                        existing.reasonVisible() || added.reasonVisible()));
    }
}
