package com.mlsoft.backend.domain.email.service;

import com.mlsoft.backend.domain.email.entity.EmailHistory;
import com.mlsoft.backend.domain.email.entity.EmailStatus;
import com.mlsoft.backend.domain.email.entity.EmailType;
import com.mlsoft.backend.domain.email.repository.EmailHistoryRepository;
import com.mlsoft.backend.domain.user.entity.Role;
import com.mlsoft.backend.domain.user.entity.User;
import com.mlsoft.backend.domain.user.repository.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.support.TransactionTemplate;

import java.math.BigDecimal;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

/**
 * 이메일 트랜잭션 경계를 <b>실제 commit/rollback</b>으로 검증한다.
 *
 * <p>{@code @Transactional} 테스트로는 이걸 검증할 수 없다 — 테스트 종료 시 롤백되므로
 * 커밋 경계가 아예 발생하지 않는다. 그래서 {@link TransactionTemplate}으로 테스트 중간에
 * 진짜 커밋과 롤백을 일으킨다.
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
    private UserRepository userRepository;
    @Autowired
    private EmailHistoryRepository emailHistoryRepository;
    @Autowired
    private EmailDeliveryService emailDeliveryService;

    private User user;

    @BeforeEach
    void setUp() {
        emailHistoryRepository.deleteAll();
        userRepository.deleteAll();

        user = userRepository.saveAndFlush(User.builder()
                .name("트랜잭션 테스트")
                .email("transaction@mlsoft.com")
                .role(Role.EMPLOYEE)
                .baseDays(new BigDecimal("15.0"))
                .useDays(BigDecimal.ZERO)
                .bonusDays(BigDecimal.ZERO)
                .advanceDays(BigDecimal.ZERO)
                .isActive(true)
                .build());
    }

    /**
     * 1차 테스트 A — 발송 결과는 바깥 트랜잭션과 운명을 같이하면 안 된다.
     *
     * <p>이전 구조에서는 리스너 하나가 수신자 전체를 한 트랜잭션으로 묶었다. 그래서 뒤쪽에서
     * 예외가 나면 <b>이미 메일이 나간 앞쪽 수신자의 SENT 이력까지 롤백</b>됐다.
     * 메일은 되돌릴 수 없으므로 "보냈는데 기록이 없는" 상태가 남고, FAILED도 아니라 재시도도 안 됐다.
     *
     * <p>이 테스트는 그 구조를 직접 막는다 — {@code send()}가 {@code REQUIRES_NEW}가 아니면
     * 바깥 롤백에 휩쓸려 PENDING으로 되돌아가고 이 테스트가 깨진다.
     */
    @Test
    @DisplayName("발송 결과는 바깥 트랜잭션이 롤백돼도 살아남는다 — 수신자별 트랜잭션 분리")
    void send_바깥롤백에도_이력이_남는다() {
        Long historyId = savePending();

        assertThrows(IllegalStateException.class, () ->
                transactionTemplate.executeWithoutResult(status -> {
                    emailDeliveryService.send(historyId);
                    throw new IllegalStateException("바깥 트랜잭션 롤백");
                }));

        EmailHistory after = emailHistoryRepository.findById(historyId).orElseThrow();
        assertEquals(EmailStatus.FAILED, after.getStatus(),
                "계정 미설정이라 FAILED가 확정돼야 한다 — PENDING이면 바깥 롤백에 휩쓸린 것이다");
        assertEquals(1, after.getRetryCount());
    }

    /**
     * 1차 테스트 B — 업무가 롤백되면 알림도 없어야 한다.
     *
     * <p>이력을 업무 트랜잭션 안에서 만들기 때문에 자동으로 성립한다.
     */
    @Test
    @DisplayName("업무 트랜잭션이 롤백되면 이메일 이력이 남지 않는다")
    void 업무롤백시_이력없음() {
        assertThrows(IllegalStateException.class, () ->
                transactionTemplate.executeWithoutResult(status -> {
                    emailHistoryRepository.save(EmailHistory.create(
                            user, null, EmailType.LEAVE, "제목", "본문"));
                    throw new IllegalStateException("업무 롤백");
                }));

        assertEquals(0, emailHistoryRepository.count());
    }

    /**
     * 1차 테스트 B — 업무가 커밋되면 <b>비동기가 한 줄도 안 돌아도</b> PENDING이 남아야 한다.
     *
     * <p>이게 큐 포화·강제 종료에서 알림이 사라지지 않게 하는 근거다. PENDING이 DB에 있으면
     * 재시도 스케줄러가 회수한다({@code findDispatchTargetIds}).
     */
    @Test
    @DisplayName("업무가 커밋되면 발송 전이라도 PENDING 이력이 DB에 남는다")
    void 업무커밋시_PENDING이_남는다() {
        transactionTemplate.executeWithoutResult(status ->
                emailHistoryRepository.save(EmailHistory.create(
                        user, null, EmailType.LEAVE, "제목", "본문")));

        EmailHistory saved = emailHistoryRepository.findAll().getFirst();
        assertEquals(EmailStatus.PENDING, saved.getStatus());
        assertEquals(0, saved.getRetryCount());
    }

    private Long savePending() {
        return transactionTemplate.execute(status ->
                emailHistoryRepository.save(EmailHistory.create(
                        user, null, EmailType.LEAVE, "제목", "본문")).getId());
    }
}
