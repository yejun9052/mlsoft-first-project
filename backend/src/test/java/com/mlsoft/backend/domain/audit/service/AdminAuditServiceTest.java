package com.mlsoft.backend.domain.audit.service;

import com.mlsoft.backend.domain.audit.dto.AdminActionOption;
import com.mlsoft.backend.domain.audit.dto.AdminAuditLogResponse;
import com.mlsoft.backend.domain.audit.entity.AdminAction;
import com.mlsoft.backend.domain.user.entity.Role;
import com.mlsoft.backend.domain.user.entity.User;
import com.mlsoft.backend.domain.user.repository.UserRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.test.context.ActiveProfiles;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 관리자 조작 감사 로그 (리뷰 S-3) — <b>실제 DB로</b> 검증한다.
 *
 * <p>Mockito로는 이 기능의 핵심이 검증되지 않는다. 기록 자체는 호출 한 줄이고,
 * 실제로 문제가 생기는 곳은 <b>영속화와 조회</b>다 — {@code getReferenceById} 프록시가
 * FK로 저장되는지, 대상 사원이 없는(null) 설정 변경 행이 응답 변환에서 터지지 않는지,
 * {@code @EntityGraph}가 붙은 {@code @Query}가 실제로 도는지.
 */
@SpringBootTest
@ActiveProfiles("test")
class AdminAuditServiceTest {

    /** id 내림차순 — createdAt은 같은 테스트 안에서 동률이 날 수 있어 정렬 기준으로 쓰지 않는다 */
    private static final PageRequest LATEST_FIRST =
            PageRequest.of(0, 20, Sort.by(Sort.Direction.DESC, "id"));

    @Autowired
    private AdminAuditService adminAuditService;
    @Autowired
    private UserRepository userRepository;

    @Test
    @DisplayName("사원 대상 조작 — actor·대상·전후 값이 그대로 조회된다")
    void recordUserChange_저장_조회() {
        User actor = saveUser("관리자", Role.SYSTEM_ADMIN);
        User target = saveUser("대상자", Role.EMPLOYEE);

        adminAuditService.recordUserChange(actor.getId(), AdminAction.BASE_DAYS_CHANGED, target, "15.0일", "20.0일");

        AdminAuditLogResponse log = onlyLogOf(target);
        assertEquals(AdminAction.BASE_DAYS_CHANGED.name(), log.action());
        assertEquals("연차 직접 설정", log.actionLabel());
        assertEquals(actor.getId(), log.actorId());
        assertEquals("관리자", log.actorName());
        assertEquals(target.getId(), log.targetUserId());
        assertEquals("대상자", log.targetLabel());
        assertEquals("15.0일", log.beforeValue());
        assertEquals("20.0일", log.afterValue());
        assertNotNull(log.createdAt());
    }

    @Test
    @DisplayName("설정 변경 — 대상 사원이 없어도(null) 조회·변환된다")
    void recordConfigChange_대상없음_조회가능() {
        // targetUser를 non-null로 잘못 잡으면 여기서 저장이 깨지고,
        // 응답 변환이 null을 안 보면 목록 조회 전체가 500이 된다
        User actor = saveUser("설정 관리자", Role.SYSTEM_ADMIN);

        adminAuditService.recordConfigChange(actor.getId(), "advance_max_days", "5.0", "3.0");

        Page<AdminAuditLogResponse> page =
                adminAuditService.getLogs(AdminAction.CONFIG_CHANGED, null, LATEST_FIRST);
        AdminAuditLogResponse log = page.getContent().get(0);
        assertNull(log.targetUserId());
        assertEquals("advance_max_days", log.targetLabel());
        assertEquals("5.0", log.beforeValue());
        assertEquals("3.0", log.afterValue());
    }

    @Test
    @DisplayName("대상 사원이 개명해도 기록의 표시명은 그때 값 그대로다")
    void targetLabel_스냅샷() {
        // FK만 두면 과거 기록이 현재 이름으로 보인다 — 감사 기록은 그때 무엇이 보였는지가 남아야 한다
        User actor = saveUser("관리자", Role.SYSTEM_ADMIN);
        User target = saveUser("개명전", Role.EMPLOYEE);
        adminAuditService.recordUserChange(actor.getId(), AdminAction.ROLE_CHANGED, target, "사원", "팀장");

        target.updateProfile("개명후", LocalDate.of(1990, 1, 1));
        userRepository.saveAndFlush(target);

        assertEquals("개명전", onlyLogOf(target).targetLabel());
    }

    @Test
    @DisplayName("action 필터 — 지정한 종류만 걸러진다")
    void getLogs_action필터() {
        User actor = saveUser("관리자", Role.SYSTEM_ADMIN);
        User target = saveUser("대상자", Role.EMPLOYEE);
        adminAuditService.recordUserChange(actor.getId(), AdminAction.ROLE_CHANGED, target, "사원", "팀장");
        adminAuditService.recordUserChange(actor.getId(), AdminAction.USER_RETIRED, target, "재직", "퇴직");

        Page<AdminAuditLogResponse> page =
                adminAuditService.getLogs(AdminAction.USER_RETIRED, target.getId(), LATEST_FIRST);

        assertEquals(1, page.getTotalElements());
        assertEquals(AdminAction.USER_RETIRED.name(), page.getContent().get(0).action());
    }

    @Test
    @DisplayName("필터를 둘 다 비우면 전체를 준다 — 조건이 null일 때 걸러지지 않는다")
    void getLogs_필터없음_전체() {
        User actor = saveUser("관리자", Role.SYSTEM_ADMIN);
        User target = saveUser("대상자", Role.EMPLOYEE);
        adminAuditService.recordUserChange(actor.getId(), AdminAction.ROLE_CHANGED, target, "사원", "팀장");
        adminAuditService.recordConfigChange(actor.getId(), "advance_max_days", "5.0", "3.0");

        Page<AdminAuditLogResponse> page = adminAuditService.getLogs(null, null, LATEST_FIRST);

        assertTrue(page.getTotalElements() >= 2, "실제 " + page.getTotalElements());
    }

    @Test
    @DisplayName("액션 목록 — 카탈로그 전체를 라벨과 함께 내려준다")
    void getActions_라벨포함() {
        List<AdminActionOption> options = adminAuditService.getActions();

        assertEquals(AdminAction.values().length, options.size());
        assertTrue(options.stream().allMatch(o -> o.label() != null && !o.label().isBlank()));
    }

    // ---- 헬퍼 ----

    /** 대상 사원으로 좁히면 다른 테스트가 남긴 행과 섞이지 않는다 */
    private AdminAuditLogResponse onlyLogOf(User target) {
        Page<AdminAuditLogResponse> page = adminAuditService.getLogs(null, target.getId(), LATEST_FIRST);
        return page.getContent().get(0);
    }

    private User saveUser(String name, Role role) {
        return userRepository.save(User.builder()
                .name(name)
                // 시연 데이터 시더 테스트가 @mlsoft.com 계정을 자기 것으로 세지 않게 도메인을 분리한다
                .email("audit-" + System.nanoTime() + "@integration.test")
                .role(role)
                .baseDays(new BigDecimal("15.0"))
                .useDays(BigDecimal.ZERO)
                .bonusDays(BigDecimal.ZERO)
                .advanceDays(BigDecimal.ZERO)
                .isActive(true)
                .build());
    }
}
