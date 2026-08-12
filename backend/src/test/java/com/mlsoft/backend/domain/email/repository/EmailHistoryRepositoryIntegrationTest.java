package com.mlsoft.backend.domain.email.repository;

import com.mlsoft.backend.domain.email.entity.EmailHistory;
import com.mlsoft.backend.domain.email.entity.EmailStatus;
import com.mlsoft.backend.domain.email.entity.EmailType;
import com.mlsoft.backend.domain.user.entity.Role;
import com.mlsoft.backend.domain.user.entity.User;
import com.mlsoft.backend.domain.user.repository.UserRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 재시도 대상 조회를 DB로 고정한다 (리뷰 D-4).
 *
 * <p><b>목으로는 이걸 검증할 수 없다.</b> 상한을 지키는 주체가 서비스 코드가 아니라
 * 파생 쿼리 이름({@code RetryCountLessThan}) 자체이기 때문이다. 리포지토리를 목으로 두고
 * "빈 리스트를 반환하더라"를 확인하면 쿼리가 {@code GreaterThan}으로 바뀌어도 테스트가 통과한다 —
 * 그 상태에서 운영에 나가면 <b>영구 실패 건을 무한 재시도</b>하게 된다.
 *
 * <p>그래서 실제 행을 넣고 경계(2 포함 / 3 제외)를 확인한다.
 */
@SpringBootTest
@ActiveProfiles("test")
@Transactional
class EmailHistoryRepositoryIntegrationTest {

    private static final int MAX_ATTEMPTS = 3;

    @Autowired
    private EmailHistoryRepository emailHistoryRepository;
    @Autowired
    private UserRepository userRepository;

    @Test
    @DisplayName("재시도 대상은 FAILED이면서 시도 횟수가 상한 미만인 것만 — 상한 도달분은 빠진다")
    void findRetryTargets_상한도달분제외() {
        User user = saveUser();

        // markFailed가 카운트를 올리므로 FAILED의 최솟값은 1이다 (0인 FAILED는 도메인상 도달 불가)
        EmailHistory 최초실패 = saveFailed(user, 1);                // 1 → 포함
        EmailHistory 한계직전 = saveFailed(user, MAX_ATTEMPTS - 1); // 2 → 포함
        EmailHistory 한계도달 = saveFailed(user, MAX_ATTEMPTS);     // 3 → 제외
        EmailHistory 초과 = saveFailed(user, MAX_ATTEMPTS + 1);     // 4 → 제외

        List<Long> targets = emailHistoryRepository
                .findTop100ByStatusAndRetryCountLessThanOrderByIdAsc(EmailStatus.FAILED, MAX_ATTEMPTS)
                .stream()
                .map(EmailHistory::getId)
                .toList();

        assertEquals(List.of(최초실패.getId(), 한계직전.getId()), targets,
                "상한 미만만, 오래된 순으로 나와야 한다");
        assertTrue(targets.stream().noneMatch(id -> id.equals(한계도달.getId())));
        assertTrue(targets.stream().noneMatch(id -> id.equals(초과.getId())));
    }

    @Test
    @DisplayName("SENT·PENDING은 시도 횟수와 무관하게 재시도 대상이 아니다")
    void findRetryTargets_상태가FAILED인것만() {
        User user = saveUser();
        saveHistory(user, EmailStatus.SENT, 0);
        saveHistory(user, EmailStatus.PENDING, 0);
        EmailHistory 실패 = saveFailed(user, 1);

        List<EmailHistory> targets = emailHistoryRepository
                .findTop100ByStatusAndRetryCountLessThanOrderByIdAsc(EmailStatus.FAILED, MAX_ATTEMPTS);

        assertEquals(1, targets.size());
        assertEquals(실패.getId(), targets.getFirst().getId());
    }

    private EmailHistory saveFailed(User user, int retryCount) {
        return saveHistory(user, EmailStatus.FAILED, retryCount);
    }

    /**
     * 상태를 도메인 메서드로만 만든다 — Setter가 없기도 하고, 필드에 직접 대입하면
     * "markFailed가 카운트를 올린다"는 불변식을 우회한 채 쿼리만 통과시키게 된다.
     */
    private EmailHistory saveHistory(User user, EmailStatus status, int failCount) {
        EmailHistory history = EmailHistory.create(
                user, null, EmailType.LEAVE, "제목", "본문");
        for (int i = 0; i < failCount; i++) {
            history.markFailed("실패 " + (i + 1));
        }
        if (status == EmailStatus.SENT) {
            history.markSent();
        }
        return emailHistoryRepository.saveAndFlush(history);
    }

    private User saveUser() {
        return userRepository.saveAndFlush(User.builder()
                .name("수신자")
                .email("recipient@mlsoft.com")
                .role(Role.EMPLOYEE)
                .baseDays(new BigDecimal("15.0"))
                .useDays(BigDecimal.ZERO)
                .bonusDays(BigDecimal.ZERO)
                .advanceDays(BigDecimal.ZERO)
                .isActive(true)
                .build());
    }
}
