package com.mlsoft.backend.domain.email.service;

import com.mlsoft.backend.config.EmailAsyncConfig;
import com.mlsoft.backend.domain.email.event.EmailNotificationEvent;
import com.mlsoft.backend.domain.email.event.EmailRecipient;
import com.mlsoft.backend.domain.user.entity.User;
import com.mlsoft.backend.domain.user.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

import java.util.Optional;

/**
 * 업무 커밋 뒤 이메일 발송 이력을 별도 트랜잭션에 저장한다.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class EmailNotificationListener {

    private final UserRepository userRepository;
    private final EmailTemplateFactory emailTemplateFactory;
    private final EmailDeliveryService emailDeliveryService;

    @Async(EmailAsyncConfig.MAIL_TASK_EXECUTOR)
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void handle(EmailNotificationEvent event) {
        for (EmailRecipient recipientSnapshot : event.recipients()) {
            Optional<User> optionalRecipient = userRepository.findById(recipientSnapshot.userId());
            if (optionalRecipient.isEmpty()) {
                log.warn("[이메일] 수신자 사용자 없음 — userId={}", recipientSnapshot.userId());
                continue;
            }

            User recipient = optionalRecipient.get();
            // 이벤트 발행 뒤 비동기 실행 전 퇴직할 수 있으므로 발송 직전에 다시 검사한다.
            if (!recipient.isActive()) {
                log.info("[이메일] 퇴직자 수신 제외 — userId={}", recipient.getId());
                continue;
            }

            EmailMessage message = emailTemplateFactory.create(
                    event.templateData(),
                    recipientSnapshot.reasonVisible());
            emailDeliveryService.createAndSend(recipient, event.emailType(), message);
        }
    }
}
