package com.mlsoft.backend.domain.user.service;

import com.mlsoft.backend.domain.user.entity.OnboardingStatus;
import com.mlsoft.backend.domain.user.entity.Role;
import com.mlsoft.backend.domain.user.entity.User;
import com.mlsoft.backend.domain.user.repository.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 마지막 SYSTEM_ADMIN 보호의 <b>동시성</b> 검증 (리뷰 S-2 — Codex 리뷰 2026-08-10 지적).
 *
 * <p>처음 구현은 단순 {@code count}였다. 관리자 A와 B를 서로 다른 요청에서 동시에 강등하면
 * 각 트랜잭션이 <b>상대를 세어</b> 검증을 통과하고, 서로 다른 {@code User} 행을 갱신하므로
 * {@code @Version} 낙관적 락도 충돌하지 않는다 → 둘 다 커밋돼 관리자가 0명이 된다(쓰기 스큐).
 * 그 뒤로는 권한 부여 엔드포인트가 SA 전용이라 DB 직접 UPDATE 외에 복구 수단이 없다.
 *
 * <p>단위 테스트로는 잡을 수 없다 — 스텁이 항상 같은 값을 돌려주므로 두 트랜잭션이 서로를
 * 어떻게 보는지가 재현되지 않는다. 그래서 실제 DB(H2) + 두 스레드로 검증한다.
 *
 * <p><b>검증 대상은 불변식</b>이다: "쓸 수 있는 관리자가 최소 1명 남는다".
 * 어느 쪽이 이겼는지는 보지 않는다 — 잠금 순서에 따라 달라질 수 있고, 그건 결과가 아니다.
 * {@code findActiveByRoleForUpdate}의 {@code @Lock}을 빼면 이 테스트가 깨진다.
 */
@SpringBootTest
@ActiveProfiles("test")
class LastSystemAdminConcurrencyTest {

    /** 두 스레드가 동시에 출발하도록 맞추는 시간 — 잠금 대기가 이보다 길면 테스트가 실패한다 */
    private static final long TIMEOUT_SECONDS = 20;

    @Autowired
    private UserService userService;
    @Autowired
    private UserRepository userRepository;

    /**
     * 인메모리 DB를 공유하는 다른 테스트가 남긴 관리자를 정리한다.
     * 이 테스트는 "쓸 수 있는 관리자가 정확히 2명"인 상태를 전제로 하므로,
     * 남아 있는 관리자가 있으면 강등이 정상적으로 둘 다 성공해 경쟁이 드러나지 않는다.
     */
    @BeforeEach
    void 관리자_기준선_정리() {
        List<User> existing = userRepository.findAll().stream()
                .filter(u -> u.getRole() == Role.SYSTEM_ADMIN && u.isActive())
                .toList();
        existing.forEach(admin -> admin.changeRole(Role.EMPLOYEE));
        userRepository.saveAll(existing);
        userRepository.flush();
    }

    @Test
    @DisplayName("두 관리자를 동시에 강등해도 관리자가 0명이 되지 않는다")
    void 동시_강등_한쪽만_성공() throws Exception {
        User adminA = saveOnboardedAdmin("관리자A");
        User adminB = saveOnboardedAdmin("관리자B");

        AtomicInteger succeeded = new AtomicInteger();
        AtomicInteger rejected = new AtomicInteger();
        CountDownLatch startLine = new CountDownLatch(1);
        CountDownLatch finished = new CountDownLatch(2);
        ExecutorService pool = Executors.newFixedThreadPool(2);
        try {
            pool.submit(demoteTask(adminA.getId(), adminB.getId(), startLine, finished, succeeded, rejected));
            pool.submit(demoteTask(adminB.getId(), adminA.getId(), startLine, finished, succeeded, rejected));
            startLine.countDown(); // 동시 출발
            assertTrue(finished.await(TIMEOUT_SECONDS, TimeUnit.SECONDS), "두 요청이 제 시간에 끝나지 않았다");
        } finally {
            pool.shutdownNow();
        }

        // 핵심 단정 — 잠금이 없으면 succeeded=2, remaining=0이 된다
        long remaining = usableAdminCount();
        assertTrue(remaining >= 1,
                "쓸 수 있는 관리자가 0명이 됐다 (성공 " + succeeded.get() + " · 거부 " + rejected.get() + ")");
        assertEquals(2, succeeded.get() + rejected.get(), "두 요청이 모두 처리되지 않았다");
        assertEquals(1, rejected.get(), "한쪽은 LAST_SYSTEM_ADMIN으로 거부돼야 한다");
    }

    @Test
    @DisplayName("관리자가 3명이면 동시 강등 2건이 모두 성공한다 — 잠금이 정상 조작을 막지 않는다")
    void 관리자_셋_동시_강등_둘다성공() throws Exception {
        // 잠금을 넣은 뒤 과보호로 정상 강등까지 막히면 그것도 결함이다
        User adminA = saveOnboardedAdmin("관리자A");
        User adminB = saveOnboardedAdmin("관리자B");
        saveOnboardedAdmin("관리자C");

        AtomicInteger succeeded = new AtomicInteger();
        AtomicInteger rejected = new AtomicInteger();
        CountDownLatch startLine = new CountDownLatch(1);
        CountDownLatch finished = new CountDownLatch(2);
        ExecutorService pool = Executors.newFixedThreadPool(2);
        try {
            pool.submit(demoteTask(adminA.getId(), adminB.getId(), startLine, finished, succeeded, rejected));
            pool.submit(demoteTask(adminB.getId(), adminA.getId(), startLine, finished, succeeded, rejected));
            startLine.countDown();
            assertTrue(finished.await(TIMEOUT_SECONDS, TimeUnit.SECONDS), "두 요청이 제 시간에 끝나지 않았다");
        } finally {
            pool.shutdownNow();
        }

        assertEquals(2, succeeded.get(), "거부 " + rejected.get() + "건 — 정상 강등이 막혔다");
        assertEquals(1, usableAdminCount());
    }

    // ---- 헬퍼 ----

    /**
     * 강등 1건을 한 트랜잭션으로 실행한다.
     * {@code otherId}는 로그용이 아니라 <b>잠금 경쟁 상대</b>를 명시하기 위한 인자다.
     */
    private Runnable demoteTask(Long targetId, Long otherId, CountDownLatch startLine,
                                CountDownLatch finished, AtomicInteger succeeded, AtomicInteger rejected) {
        return () -> {
            try {
                startLine.await();
                userService.changeRole(targetId, Role.EMPLOYEE, otherId);
                succeeded.incrementAndGet();
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            } catch (RuntimeException e) {
                // LAST_SYSTEM_ADMIN(BusinessException) 또는 잠금 경쟁 실패 — 둘 다 "강등되지 않음"이다
                rejected.incrementAndGet();
            } finally {
                finished.countDown();
            }
        };
    }

    /** 실제로 관리 화면을 쓸 수 있는 관리자 수 — 재직 + 관리자 + 온보딩 완료 */
    private long usableAdminCount() {
        return userRepository.findAll().stream()
                .filter(u -> u.getRole() == Role.SYSTEM_ADMIN
                        && u.isActive()
                        && u.getOnboardingStatus() == OnboardingStatus.COMPLETED)
                .count();
    }

    private User saveOnboardedAdmin(String name) {
        User admin = User.builder()
                .name(name)
                // 시연 데이터 시더 테스트가 @mlsoft.com 계정을 자기 것으로 세지 않게 도메인을 분리한다
                .email("lastadmin-" + System.nanoTime() + "@integration.test")
                .role(Role.SYSTEM_ADMIN)
                .baseDays(new BigDecimal("15.0"))
                .useDays(BigDecimal.ZERO)
                .bonusDays(BigDecimal.ZERO)
                .advanceDays(BigDecimal.ZERO)
                .isActive(true)
                .build();
        admin.completeOnboarding(LocalDate.now().minusYears(2), LocalDate.of(1990, 1, 1));
        return userRepository.saveAndFlush(admin);
    }
}
