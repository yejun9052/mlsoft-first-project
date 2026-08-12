package com.mlsoft.backend.domain.user.entity;

import com.mlsoft.backend.domain.user.repository.UserRepository;
import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * 파생값 불변식을 <b>영속화 경계</b>에서 고정한다 (1차 테스트 J).
 *
 * <pre>
 * advance_days = max(0, use_days − base_days − bonus_days)
 * </pre>
 *
 * <p>도메인 메서드 6개는 이미 마지막에 {@code syncAdvanceDays()}를 호출한다. 문제는 그 경로를
 * 거치지 않는 <b>생성</b>이다 — 클래스 수준 {@code @Builder}가 공개돼 있어 네 필드를 따로 넣으면
 * 어긋난 상태 그대로 저장할 수 있었다. 이 테스트는 그런 값이 DB까지 가지 못하는 것을 확인한다.
 */
@SpringBootTest
@ActiveProfiles("test")
@Transactional
class UserAdvanceDaysInvariantTest {

    @Autowired
    private UserRepository userRepository;
    @Autowired
    private EntityManager entityManager;

    @Test
    @DisplayName("빌더로 어긋난 advance_days를 넣어도 저장 시 재계산된다")
    void 저장시_파생값재계산() {
        // 잔여 = 10 + 0 − 13 = −3 이므로 advance는 3이어야 하는데 0으로 지정했다
        User broken = User.builder()
                .name("불변식 테스트")
                .email("invariant@mlsoft.com")
                .role(Role.EMPLOYEE)
                .baseDays(new BigDecimal("10.0"))
                .bonusDays(BigDecimal.ZERO)
                .useDays(new BigDecimal("13.0"))
                .advanceDays(BigDecimal.ZERO)
                .isActive(true)
                .build();

        Long id = userRepository.saveAndFlush(broken).getId();
        entityManager.clear();

        User reloaded = userRepository.findById(id).orElseThrow();
        assertEquals(0, new BigDecimal("3.0").compareTo(reloaded.getAdvanceDays()),
                "저장 직전에 advance = max(0, 13 − 10 − 0) = 3 으로 보정돼야 한다");
    }

    @Test
    @DisplayName("잔액이 충분하면 advance는 0으로 보정된다 — 음수가 되지 않는다")
    void 저장시_음수는0으로() {
        User broken = User.builder()
                .name("불변식 테스트2")
                .email("invariant2@mlsoft.com")
                .role(Role.EMPLOYEE)
                .baseDays(new BigDecimal("15.0"))
                .bonusDays(new BigDecimal("2.0"))
                .useDays(new BigDecimal("5.0"))
                .advanceDays(new BigDecimal("9.9")) // 근거 없는 값
                .isActive(true)
                .build();

        Long id = userRepository.saveAndFlush(broken).getId();
        entityManager.clear();

        User reloaded = userRepository.findById(id).orElseThrow();
        assertEquals(0, BigDecimal.ZERO.compareTo(reloaded.getAdvanceDays()));
    }

    @Test
    @DisplayName("정상 경로의 값은 바뀌지 않는다 — 보정은 멱등이다")
    void 정상값은_그대로() {
        User user = userRepository.saveAndFlush(User.builder()
                .name("정상")
                .email("normal@mlsoft.com")
                .role(Role.EMPLOYEE)
                .baseDays(new BigDecimal("15.0"))
                .bonusDays(BigDecimal.ZERO)
                .useDays(BigDecimal.ZERO)
                .advanceDays(BigDecimal.ZERO)
                .isActive(true)
                .build());

        user.deductLeave(new BigDecimal("17.0"), true, new BigDecimal("30"));
        BigDecimal beforeFlush = user.getAdvanceDays();
        userRepository.saveAndFlush(user);
        entityManager.clear();

        User reloaded = userRepository.findById(user.getId()).orElseThrow();
        assertEquals(0, new BigDecimal("2.0").compareTo(beforeFlush),
                "도메인 메서드가 flush 전에 이미 맞춰 놓아야 한다 — 콜백은 대체재가 아니다");
        assertEquals(0, beforeFlush.compareTo(reloaded.getAdvanceDays()));
    }
}
