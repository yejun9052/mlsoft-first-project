package com.mlsoft.backend.domain.email.service;

import com.mlsoft.backend.domain.common.RequestStatus;
import com.mlsoft.backend.domain.email.entity.EmailHistory;
import com.mlsoft.backend.domain.email.entity.EmailType;
import com.mlsoft.backend.domain.email.event.EmailDispatchEvent;
import com.mlsoft.backend.domain.email.event.EmailTemplateData;
import com.mlsoft.backend.domain.email.repository.EmailHistoryRepository;
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
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;
import java.util.stream.Collectors;

/**
 * 수신자를 결정하고 <b>업무 트랜잭션 안에서</b> {@code email_history} 행을 만든다.
 *
 * <p>수신자 결정·중복 제거·사유 열람 권한을 서비스 7곳에 복제하지 않기 위한 경계다.
 *
 * <h3>왜 이력을 여기서(= 업무 트랜잭션 안에서) 만드는가 (1차 테스트 B)</h3>
 * 이전에는 커밋 후 비동기 리스너가 이력을 만들었다. 그래서 <b>리스너가 시작되지 못하면</b>
 * — 스레드풀 큐 포화, 프로세스 강제 종료 — 알림이 {@code PENDING}조차 남기지 못하고 사라졌고,
 * 재시도 스케줄러도 찾을 수 없었다. 로그가 유실되면 영구히 탐지 불가였다.
 *
 * <p>지금은 업무와 <b>같은 트랜잭션</b>에서 행을 만든다:
 * <ul>
 *   <li>업무가 롤백되면 이력도 함께 사라진다 → 안 보낼 메일이 나가지 않는다</li>
 *   <li>업무가 커밋되면 {@code PENDING}이 반드시 남는다 → 리스너가 못 돌아도 스케줄러가 줍는다</li>
 * </ul>
 * 이벤트는 그래서 id만 나른다.
 */
@Component
@RequiredArgsConstructor
public class EmailNotificationPublisher {

    private static final String DATE_SEPARATOR = ", ";

    private final ApplicationEventPublisher eventPublisher;
    private final UserRepository userRepository;
    private final EmailTemplateFactory emailTemplateFactory;
    private final EmailHistoryRepository emailHistoryRepository;

    /**
     * 수신자 후보 — 업무 트랜잭션 안에서만 쓰이므로 엔티티를 그대로 들고 있어도 안전하다.
     * (커밋 경계를 넘는 것은 {@link EmailDispatchEvent}의 id뿐이다.)
     */
    private record Recipient(User user, boolean reasonVisible) {
    }

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
        Map<Long, Recipient> recipients = new LinkedHashMap<>();
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
            Consumer<Map<Long, Recipient>> recipientCollector
    ) {
        Map<Long, Recipient> recipients = new LinkedHashMap<>();
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
            Consumer<Map<Long, Recipient>> recipientCollector
    ) {
        Map<Long, Recipient> recipients = new LinkedHashMap<>();
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

    /**
     * 수신자별로 본문을 만들어 {@code PENDING} 이력을 저장하고, 그 id로 발송 이벤트를 발행한다.
     *
     * <p>본문을 여기서 확정하는 이유: 사유 열람 권한이 수신자마다 다르므로 한 본문을 전원에게
     * 뿌릴 수 없다 (검증 Y-4). 그래서 이력 행 자체가 이미 마스킹된 최종 본문을 들고 있다.
     */
    private void publish(
            EmailType emailType,
            Map<Long, Recipient> recipients,
            EmailTemplateData templateData
    ) {
        if (recipients.isEmpty()) {
            return;
        }

        List<Long> historyIds = new ArrayList<>(recipients.size());
        for (Recipient recipient : recipients.values()) {
            EmailMessage message = emailTemplateFactory.create(templateData, recipient.reasonVisible());
            EmailHistory history = emailHistoryRepository.save(EmailHistory.create(
                    recipient.user(), null, emailType, message.title(), message.content()));
            historyIds.add(history.getId());
        }

        eventPublisher.publishEvent(new EmailDispatchEvent(historyIds));
    }

    private void addSystemAdmins(Map<Long, Recipient> recipients) {
        userRepository.findByRoleAndIsActiveTrue(Role.SYSTEM_ADMIN)
                .forEach(admin -> addRecipient(recipients, admin, true));
    }

    private void addRecipient(
            Map<Long, Recipient> recipients,
            User user,
            boolean reasonVisible
    ) {
        if (user == null || !user.isActive()) {
            return;
        }
        // 같은 사람이 신청자·승인자·SYSTEM_ADMIN을 겸할 수 있다. 한 역할이라도 사유 열람
        // 권한이 있으면 보여야 하므로 OR로 병합한다.
        recipients.merge(
                user.getId(),
                new Recipient(user, reasonVisible),
                (existing, added) -> new Recipient(
                        existing.user(),
                        existing.reasonVisible() || added.reasonVisible()));
    }
}
