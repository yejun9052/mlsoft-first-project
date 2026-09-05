package com.mlsoft.backend.domain.email.service;

import com.mlsoft.backend.domain.common.RequestStatus;
import com.mlsoft.backend.domain.email.entity.EmailHistory;
import com.mlsoft.backend.domain.email.entity.EmailType;
import com.mlsoft.backend.domain.email.event.EmailDispatchEvent;
import com.mlsoft.backend.domain.email.event.EmailTemplateData;
import com.mlsoft.backend.domain.email.repository.EmailHistoryRepository;
import com.mlsoft.backend.domain.email.event.EmailTemplateKind;
import com.mlsoft.backend.domain.email.event.ReminderTemplateData;
import com.mlsoft.backend.domain.leave.entity.LeaveRequest;
import com.mlsoft.backend.domain.user.entity.Role;
import com.mlsoft.backend.domain.user.entity.OnboardingStatus;
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
     *
     * @param reasonVisible 사유를 볼 권한이 있는가 (검증 Y-4)
     * @param applicant     이 사람이 <b>신청 당사자</b>인가 — 바로가기 버튼의 행선지가 갈린다.
     *                      신청자에게 "결재하러 가기"를 보내면 자기 신청을 자기가 결재하러 가게 된다
     */
    private record Recipient(User user, boolean reasonVisible, boolean applicant) {
    }

    public void publishLeaveApplied(LeaveRequest leave) {
        publishLeave(
                leave,
                EmailTemplateKind.LEAVE_APPLIED,
                "",
                recipients -> {
                    addApplicant(recipients, leave.getUser());
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
                    addApplicant(recipients, leave.getUser());
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
                    addApplicant(recipients, leave.getUser());
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
                    addApplicant(recipients, leave.getUser());
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
                    addApplicant(recipients, welfare.getUser());
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
                    addApplicant(recipients, welfare.getUser());
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

    /** 자동 승인 범위를 벗어난 온보딩의 승인 대기 알림 — 재직 SYSTEM_ADMIN 전원에게 보낸다. */
    public void publishOnboardingPending(User user) {
        Map<Long, Recipient> recipients = new LinkedHashMap<>();
        addSystemAdmins(recipients);
        publishOnboarding(
                EmailTemplateKind.ONBOARDING_PENDING,
                recipients,
                user,
                user.getHireDate(),
                "승인 대기",
                "",
                "");
    }

    /** 온보딩 승인 결과 — 대상 사원 본인에게만 보낸다. */
    public void publishOnboardingApproved(User user) {
        Map<Long, Recipient> recipients = new LinkedHashMap<>();
        addApplicant(recipients, user);
        String days = user.getBaseDays() == null ? "" : user.getBaseDays().toPlainString();
        publishOnboarding(
                EmailTemplateKind.ONBOARDING_APPROVED,
                recipients,
                user,
                user.getHireDate(),
                "확정",
                days,
                "");
    }

    /** 반려 전 캡처한 입사일을 본문에 남긴다 — rejectOnboarding() 뒤에는 null이 된다. */
    public void publishOnboardingRejected(User user, LocalDate rejectedHireDate) {
        Map<Long, Recipient> recipients = new LinkedHashMap<>();
        addApplicant(recipients, user);
        publishOnboarding(
                EmailTemplateKind.ONBOARDING_REJECTED,
                recipients,
                user,
                rejectedHireDate,
                "반려",
                "",
                "");
    }

    /** 1회 수정 결과 — 자동 승인 범위 안이면 확정, 밖이면 승인 대기 재발생으로 구분한다. */
    public void publishOnboardingRevised(User user) {
        Map<Long, Recipient> recipients = new LinkedHashMap<>();
        addSystemAdmins(recipients);
        boolean completed = user.getOnboardingStatus() == OnboardingStatus.COMPLETED;
        publishOnboarding(
                EmailTemplateKind.ONBOARDING_REVISED,
                recipients,
                user,
                user.getHireDate(),
                completed ? "수정 후 확정" : "수정 후 승인 대기",
                "",
                completed
                        ? "수정한 온보딩이 자동 승인 범위 안에서 확정되었습니다."
                        : "수정한 온보딩이 승인 대기 상태로 다시 접수되었습니다.");
    }

    /**
     * 연차 소진 안내 한 건을 기존 아웃박스에 넣고, 생성된 이력을 호출자에게 돌려준다.
     * 업무 이력(leave_reminder_dispatch)이 같은 트랜잭션에서 이 id를 연결할 수 있도록
     * 저장·이벤트 발행 경계를 이 메서드에 둔다.
     */
    public EmailHistory publishLeaveBalanceReminder(User user, ReminderTemplateData templateData) {
        EmailMessage message = emailTemplateFactory.createReminder(templateData);
        EmailHistory history = emailHistoryRepository.save(
                EmailHistory.create(user, null, EmailType.REMINDER, message.title(), message.content()));
        eventPublisher.publishEvent(new EmailDispatchEvent(List.of(history.getId())));
        return history;
    }

    /** 리마인더 도메인 서비스가 사용할 짧은 별칭 */
    public EmailHistory publishReminder(User user, ReminderTemplateData templateData) {
        return publishLeaveBalanceReminder(user, templateData);
    }

    private void publishOnboarding(
            EmailTemplateKind kind,
            Map<Long, Recipient> recipients,
            User user,
            LocalDate hireDate,
            String state,
            String days,
            String summary
    ) {
        publish(
                EmailType.NOTICE,
                recipients,
                new EmailTemplateData(
                        kind,
                        null,
                        user.getName(),
                        state,
                        hireDate == null ? "" : hireDate.toString(),
                        days,
                        "",
                        summary));
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
                        // name()을 쓰면 수신자가 "구분: ANNUAL"을 받는다 (2026-08-16 실제 발송)
                        leave.getLeaveType().getLabel(),
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
            EmailMessage message = emailTemplateFactory.create(
                    templateData, recipient.reasonVisible(), recipient.applicant());
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

    /** 신청 당사자 — 사유는 당연히 보이고, 버튼은 결재선이 아니라 자기 내역으로 간다 */
    private void addApplicant(Map<Long, Recipient> recipients, User user) {
        addRecipient(recipients, user, true, true);
    }

    private void addRecipient(
            Map<Long, Recipient> recipients,
            User user,
            boolean reasonVisible
    ) {
        addRecipient(recipients, user, reasonVisible, false);
    }

    private void addRecipient(
            Map<Long, Recipient> recipients,
            User user,
            boolean reasonVisible,
            boolean applicant
    ) {
        if (user == null || !user.isActive()) {
            return;
        }
        // 같은 사람이 신청자·승인자·SYSTEM_ADMIN을 겸할 수 있다. 한 역할이라도 사유 열람
        // 권한이 있으면 보여야 하므로 OR로 병합한다.
        //
        // 신청자 여부도 OR다 — 겸직이면 "내가 낸 신청"이라는 사실이 이긴다.
        //
        // 2026-08-19부터 **총관리자만은 자기 신청의 승인자이기도 하다**(ApproverResolver 참고).
        // 그 한 사람은 접수 메일에서도 "내 신청 확인하기"(/history)를 받는다 — 결재하러 가려면
        // 사이드바 결재 관리로 가야 한다. 승인자 여부를 따로 들고 다니면 바로잡을 수 있지만,
        // 그러자고 Recipient에 플래그를 하나 더 얹지는 않았다. 화면에는 결재 대기 배지가 뜨므로
        // 막히는 곳은 없고, 이 겸직은 총관리자 본인에게만 생긴다.
        recipients.merge(
                user.getId(),
                new Recipient(user, reasonVisible, applicant),
                (existing, added) -> new Recipient(
                        existing.user(),
                        existing.reasonVisible() || added.reasonVisible(),
                        existing.applicant() || added.applicant()));
    }
}
