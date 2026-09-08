package com.mlsoft.backend.domain.user.service;

import com.mlsoft.backend.domain.audit.entity.AdminAction;
import com.mlsoft.backend.domain.audit.entity.AdminAuditLog;
import com.mlsoft.backend.domain.audit.dto.AdminAuditLogResponse;
import com.mlsoft.backend.domain.audit.repository.AdminAuditLogRepository;
import com.mlsoft.backend.domain.audit.service.AdminAuditService;
import com.mlsoft.backend.domain.policy.entity.LeavePolicyConfig;
import com.mlsoft.backend.domain.policy.repository.LeavePolicyConfigRepository;
import com.mlsoft.backend.domain.user.entity.Role;
import com.mlsoft.backend.domain.user.entity.User;
import com.mlsoft.backend.domain.user.repository.UserRepository;
import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.test.context.ActiveProfiles;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

/** 자동 파기의 실제 트랜잭션과 시스템 actor 표기를 검증한다. */
@SpringBootTest
@ActiveProfiles("test")
class UserAutomaticPurgeIntegrationTest {

    @Autowired
    private UserService userService;
    @Autowired
    private UserRepository userRepository;
    @Autowired
    private LeavePolicyConfigRepository configRepository;
    @Autowired
    private AdminAuditLogRepository auditLogRepository;
    @Autowired
    private AdminAuditService adminAuditService;
    @Autowired
    private EntityManager entityManager;

    @Test
    @DisplayName("예고 30일 뒤 자동 파기와 시스템 actor 감사가 함께 커밋된다")
    void automaticPurge_keepsSystemActor() {
        setConfig("retiree_purge_mode", "AUTO");
        setConfig("retiree_purge_years", "3");
        LocalDate today = LocalDate.now();
        User target = saveRetiredUser(today.minusYears(4));
        target.markPurgeNoticeSent(today.minusDays(30).atStartOfDay());
        userRepository.saveAndFlush(target);

        assertEquals(1, userService.purgeAutomatically(target.getId(), today));
        entityManager.clear();

        User reloaded = userRepository.findById(target.getId()).orElseThrow();
        assertEquals("퇴직사원#" + target.getId(), reloaded.getName());
        AdminAuditLog audit = auditLogRepository
                .search(AdminAction.USER_PURGED, target.getId(),
                        org.springframework.data.domain.PageRequest.of(0, 20))
                .getContent().getFirst();
        assertNull(audit.getActor());
        assertEquals("user_id=" + target.getId(), audit.getTargetLabel());
    }

    @Test
    @DisplayName("예고 후 보류가 걸리면 자동 파기하지 않고 예고 상태를 초기화한다")
    void holdAfterNotice_skipsAndClearsNotice() {
        setConfig("retiree_purge_mode", "AUTO");
        setConfig("retiree_purge_years", "3");
        LocalDate today = LocalDate.now();
        User target = saveRetiredUser(today.minusYears(4));
        target.markPurgeNoticeSent(today.minusDays(30).atStartOfDay());
        target.placePurgeHold("분쟁 진행 중");
        userRepository.saveAndFlush(target);

        assertEquals(0, userService.purgeAutomatically(target.getId(), today));
        entityManager.clear();

        User reloaded = userRepository.findById(target.getId()).orElseThrow();
        assertNull(reloaded.getPurgedAt());
        assertNull(reloaded.getPurgeNoticeSentAt());
    }

    @Test
    @DisplayName("자동 파기 감사 행은 actor가 비어 있어도 목록에서 사라지지 않는다")
    void 자동파기_감사행이_목록에_남는다() {
        // 깨지면: actor_id를 비운 시스템 감사 행이 @EntityGraph 조인에서 탈락해
        //         "누가 지웠는지 아무 기록도 없는" 파기가 된다. nullable로 바꾼 순간의 위험이다
        setConfig("retiree_purge_mode", "AUTO");
        setConfig("retiree_purge_years", "3");
        LocalDate today = LocalDate.now();
        User target = saveRetiredUser(today.minusYears(4));
        target.markPurgeNoticeSent(today.minusDays(30).atStartOfDay());
        userRepository.saveAndFlush(target);

        assertEquals(1, userService.purgeAutomatically(target.getId(), today));
        entityManager.clear();

        Page<AdminAuditLogResponse> logs = adminAuditService.getLogs(
                AdminAction.USER_PURGED, target.getId(), PageRequest.of(0, 10));

        assertEquals(1, logs.getTotalElements(), "시스템 감사 행이 목록에서 빠졌다");
        AdminAuditLogResponse row = logs.getContent().get(0);
        assertNull(row.actorId(), "자동 파기는 사람이 누른 것이 아니다");
        assertEquals("시스템", row.actorName());
    }

    private User saveRetiredUser(LocalDate retiredAt) {
        return userRepository.saveAndFlush(User.builder()
                .name("자동 파기 대상")
                .email("auto-purge-" + System.nanoTime() + "@integration.test")
                .role(Role.EMPLOYEE)
                .baseDays(new BigDecimal("10.0"))
                .useDays(BigDecimal.ZERO)
                .bonusDays(BigDecimal.ZERO)
                .advanceDays(BigDecimal.ZERO)
                .isActive(false)
                .retiredAt(retiredAt)
                .build());
    }

    private void setConfig(String key, String value) {
        configRepository.findByName(key)
                .ifPresentOrElse(config -> {
                    config.updateValue(value);
                    configRepository.saveAndFlush(config);
                }, () -> configRepository.saveAndFlush(LeavePolicyConfig.create(key, value)));
    }
}
