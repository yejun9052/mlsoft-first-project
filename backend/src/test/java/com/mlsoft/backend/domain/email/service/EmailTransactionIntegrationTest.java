package com.mlsoft.backend.domain.email.service;

import com.mlsoft.backend.domain.email.entity.EmailHistory;
import com.mlsoft.backend.domain.email.entity.EmailType;
import com.mlsoft.backend.domain.email.event.EmailNotificationEvent;
import com.mlsoft.backend.domain.email.event.EmailRecipient;
import com.mlsoft.backend.domain.email.event.EmailTemplateData;
import com.mlsoft.backend.domain.email.event.EmailTemplateKind;
import com.mlsoft.backend.domain.email.repository.EmailHistoryRepository;
import com.mlsoft.backend.domain.user.entity.Role;
import com.mlsoft.backend.domain.user.entity.User;
import com.mlsoft.backend.domain.user.repository.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.support.TransactionTemplate;

import java.math.BigDecimal;
import java.time.Duration;
import java.time.Instant;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

/**
 * AFTER_COMMIT 경계를 실제 트랜잭션 commit/rollback으로 검증한다.
 *
 * <p>@Transactional 테스트는 테스트 메서드 종료 때 롤백되므로 AFTER_COMMIT 리스너가 실행되지 않는다.
 * TransactionTemplate을 사용해 테스트 중간에 실제 commit과 rollback을 확정한다.
 */
@SpringBootTest(properties = {
        "spring.mail.username=",
        "spring.mail.password="
})
@ActiveProfiles("test")
class EmailTransactionIntegrationTest {

    @Autowired
    private TransactionTemplate transactionTemplate;

    @Autowired
    private ApplicationEventPublisher eventPublisher;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private EmailHistoryRepository emailHistoryRepository;

    private Long userId;

    @BeforeEach
    void setUp() {
        emailHistoryRepository.deleteAll();
        userRepository.deleteAll();

        User user = User.builder()
                .name("트랜잭션 테스트")
                .email("transaction@mlsoft.com")
                .role(Role.EMPLOYEE)
                .baseDays(new BigDecimal("15.0"))
                .useDays(BigDecimal.ZERO)
                .bonusDays(BigDecimal.ZERO)
                .advanceDays(BigDecimal.ZERO)
                .isActive(true)
                .build();
        userId = userRepository.saveAndFlush(user).getId();
    }

    @Test
    @DisplayName("메일 실패와 무관하게 업무 트랜잭션은 커밋되고 FAILED 이력이 별도 저장된다")
    void mailFailure_업무커밋유지() {
        transactionTemplate.executeWithoutResult(status -> eventPublisher.publishEvent(event()));

        awaitHistoryCount(1);

        User committed = userRepository.findById(userId).orElseThrow();
        List<EmailHistory> histories = emailHistoryRepository.findAll();

        assertEquals("트랜잭션 테스트", committed.getName());
        assertEquals(1, histories.size());
        assertEquals(1, histories.getFirst().getRetryCount());
    }

    @Test
    @DisplayName("업무 트랜잭션이 롤백되면 이메일 이벤트를 발송하지 않는다")
    void businessRollback_이메일발송안함() {
        assertThrows(IllegalStateException.class, () ->
                transactionTemplate.executeWithoutResult(status -> {
                    eventPublisher.publishEvent(event());
                    throw new IllegalStateException("업무 롤백");
                }));

        // 비동기 리스너가 잘못 호출됐다면 충분히 이력이 생길 시간을 준 뒤 확인한다.
        waitFor(Duration.ofMillis(500));
        assertEquals(0, emailHistoryRepository.count());
    }

    private EmailNotificationEvent event() {
        return new EmailNotificationEvent(
                EmailType.LEAVE,
                List.of(new EmailRecipient(userId, true)),
                new EmailTemplateData(
                        EmailTemplateKind.LEAVE_APPLIED,
                        1L,
                        "트랜잭션 테스트",
                        "ANNUAL",
                        "2026-08-20",
                        "1.0",
                        "테스트",
                        ""));
    }

    private void awaitHistoryCount(long expected) {
        Instant deadline = Instant.now().plusSeconds(5);
        while (Instant.now().isBefore(deadline)) {
            if (emailHistoryRepository.count() == expected) {
                return;
            }
            waitFor(Duration.ofMillis(50));
        }
        assertEquals(expected, emailHistoryRepository.count());
    }

    private void waitFor(Duration duration) {
        try {
            Thread.sleep(duration.toMillis());
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("테스트 대기 중 인터럽트", e);
        }
    }
}
