package com.mlsoft.backend.domain.email.repository;

import com.mlsoft.backend.domain.email.entity.EmailHistory;
import com.mlsoft.backend.domain.email.entity.EmailStatus;
import com.mlsoft.backend.domain.email.entity.EmailType;
import com.mlsoft.backend.domain.user.entity.Role;
import com.mlsoft.backend.domain.user.entity.User;
import com.mlsoft.backend.domain.user.repository.UserRepository;
import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.data.domain.PageRequest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 재발송 대상 조회를 DB로 고정한다 (리뷰 D-4 · 1차 테스트 B).
 *
 * <p><b>목으로는 이걸 검증할 수 없다.</b> 상한과 회수 조건을 지키는 주체가 서비스 코드가 아니라
 * 쿼리 자체이기 때문이다. 리포지토리를 목으로 두고 "빈 리스트를 반환하더라"를 확인하면
 * 조건이 반대로 바뀌어도 테스트가 통과한다 — 그 상태로 운영에 나가면
 * <b>영구 실패 건을 무한 재시도</b>하거나 <b>유실된 알림을 영영 회수하지 못한다.</b>
 */
@SpringBootTest
@ActiveProfiles("test")
@Transactional
class EmailHistoryRepositoryIntegrationTest {

    private static final int MAX_ATTEMPTS = 3;
    private static final LocalDateTime STALE_BEFORE = LocalDateTime.now().minusMinutes(10);

    @Autowired
    private EmailHistoryRepository emailHistoryRepository;
    @Autowired
    private UserRepository userRepository;
    @Autowired
    private EntityManager entityManager;

    @Test
    @DisplayName("FAILED는 시도 횟수가 상한 미만인 것만 — 상한 도달분은 빠진다")
    void 재발송대상_상한도달분제외() {
        User user = saveUser();

        // markFailed가 카운트를 올리므로 FAILED의 최솟값은 1이다 (0인 FAILED는 도메인상 도달 불가)
        Long 최초실패 = saveFailed(user, 1);                 // 1 → 포함
        Long 한계직전 = saveFailed(user, MAX_ATTEMPTS - 1);  // 2 → 포함
        Long 한계도달 = saveFailed(user, MAX_ATTEMPTS);      // 3 → 제외
        Long 초과 = saveFailed(user, MAX_ATTEMPTS + 1);      // 4 → 제외

        List<Long> targets = findTargets();

        assertEquals(List.of(최초실패, 한계직전), targets, "상한 미만만, 오래된 순으로 나와야 한다");
        assertTrue(targets.stream().noneMatch(id -> id.equals(한계도달)));
        assertTrue(targets.stream().noneMatch(id -> id.equals(초과)));
    }

    /**
     * 1차 테스트 B의 안전망 — 비동기 발송이 시작조차 못 한 건을 회수한다.
     *
     * <p>큐 포화나 프로세스 강제 종료로 생긴다. 이 조건이 없으면 업무는 커밋됐는데 메일만
     * 영영 안 나가고 아무도 모른다.
     */
    @Test
    @DisplayName("오래 묶인 PENDING은 회수하고, 방금 만든 PENDING은 건드리지 않는다")
    void 재발송대상_오래된PENDING만회수() {
        User user = saveUser();

        Long 방금생성 = savePending(user);
        Long 오래묶임 = savePending(user);
        backdate(오래묶임, LocalDateTime.now().minusHours(1));

        List<Long> targets = findTargets();

        assertEquals(List.of(오래묶임), targets,
                "방금 만든 PENDING까지 집으면 정상 발송 중인 건을 중복 발송한다");
        assertTrue(targets.stream().noneMatch(id -> id.equals(방금생성)));
    }

    @Test
    @DisplayName("SENT는 오래돼도 재발송 대상이 아니다")
    void 재발송대상_SENT제외() {
        User user = saveUser();
        Long 발송완료 = savePending(user);
        EmailHistory sent = emailHistoryRepository.findById(발송완료).orElseThrow();
        sent.markSent();
        emailHistoryRepository.saveAndFlush(sent);
        backdate(발송완료, LocalDateTime.now().minusHours(1));

        assertEquals(List.of(), findTargets());
    }

    @Test
    @DisplayName("조건부 SENDING 선점은 같은 이력에서 한 번만 성공한다")
    void sending선점_동일이력_한번만성공() {
        User user = saveUser();
        Long historyId = savePending(user);

        int first = emailHistoryRepository.claimForSending(
                historyId, EmailStatus.SENDING, EmailStatus.PENDING, EmailStatus.FAILED, MAX_ATTEMPTS);
        int second = emailHistoryRepository.claimForSending(
                historyId, EmailStatus.SENDING, EmailStatus.PENDING, EmailStatus.FAILED, MAX_ATTEMPTS);

        assertEquals(1, first);
        assertEquals(0, second);
        assertEquals(EmailStatus.SENDING,
                emailHistoryRepository.findById(historyId).orElseThrow().getStatus());
    }

    @Test
    @DisplayName("오래 묶인 SENDING은 FAILED로 복구되어 재시도할 수 있다")
    void staleSending_실패로복구() {
        User user = saveUser();
        Long historyId = savePending(user);
        emailHistoryRepository.claimForSending(
                historyId, EmailStatus.SENDING, EmailStatus.PENDING, EmailStatus.FAILED, MAX_ATTEMPTS);
        backdate(historyId, LocalDateTime.now().minusHours(1));

        int recovered = emailHistoryRepository.recoverStaleSending(
                EmailStatus.SENDING,
                EmailStatus.FAILED,
                LocalDateTime.now().minusMinutes(10),
                "stale sending");

        assertEquals(1, recovered);
        assertEquals(EmailStatus.FAILED,
                emailHistoryRepository.findById(historyId).orElseThrow().getStatus());
    }

    @Test
    @DisplayName("관리자 이력 검색은 type·status 생략과 필터를 모두 지원한다")
    void 관리자이력검색_필터() {
        User user = saveUser();
        savePending(user);

        assertEquals(1, emailHistoryRepository.searchForAdmin(
                null, null, PageRequest.of(0, 20)).getTotalElements());
        assertEquals(1, emailHistoryRepository.searchForAdmin(
                EmailType.LEAVE, EmailStatus.PENDING, PageRequest.of(0, 20)).getTotalElements());
        assertEquals(0, emailHistoryRepository.searchForAdmin(
                EmailType.NOTICE, EmailStatus.PENDING, PageRequest.of(0, 20)).getTotalElements());
    }

    private List<Long> findTargets() {
        return emailHistoryRepository.findDispatchTargetIds(
                MAX_ATTEMPTS, EmailStatus.FAILED, EmailStatus.PENDING, STALE_BEFORE,
                PageRequest.of(0, 100));
    }

    /** created_at은 @CreatedDate + updatable=false라 엔티티로는 못 바꾼다 — 네이티브로 소급한다 */
    private void backdate(Long id, LocalDateTime createdAt) {
        entityManager.flush();
        entityManager.createNativeQuery(
                        "update email_history set created_at = :createdAt where id = :id")
                .setParameter("createdAt", createdAt)
                .setParameter("id", id)
                .executeUpdate();
        entityManager.clear();
    }

    private Long saveFailed(User user, int failCount) {
        EmailHistory history = EmailHistory.create(user, null, EmailType.LEAVE, "제목", "본문");
        // 상태를 도메인 메서드로만 만든다 — 필드에 직접 대입하면 "markFailed가 카운트를 올린다"는
        // 불변식을 우회한 채 쿼리만 통과시키게 된다.
        for (int i = 0; i < failCount; i++) {
            history.markFailed("실패 " + (i + 1));
        }
        return emailHistoryRepository.saveAndFlush(history).getId();
    }

    private Long savePending(User user) {
        return emailHistoryRepository.saveAndFlush(
                EmailHistory.create(user, null, EmailType.LEAVE, "제목", "본문")).getId();
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
