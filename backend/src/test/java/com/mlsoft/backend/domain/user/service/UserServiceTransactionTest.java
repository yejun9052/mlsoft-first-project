package com.mlsoft.backend.domain.user.service;

import com.mlsoft.backend.domain.audit.repository.AdminAuditLogRepository;
import com.mlsoft.backend.domain.department.entity.Department;
import com.mlsoft.backend.domain.department.repository.DepartmentRepository;
import com.mlsoft.backend.domain.user.entity.Role;
import com.mlsoft.backend.domain.user.entity.User;
import com.mlsoft.backend.domain.user.repository.UserRepository;
import com.mlsoft.backend.global.exception.BusinessException;
import com.mlsoft.backend.global.exception.ErrorCode;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.ActiveProfiles;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

/**
 * 사용자 관리 복합 조작의 실제 트랜잭션 경계를 검증한다.
 *
 * <p>Mockito 객체는 예외가 발생해도 메모리 변경을 되돌리지 않으므로,
 * 역할 변경 실패 시 앞서 수행한 부서 배정과 감사 기록까지 롤백되는지는
 * Spring 프록시와 테스트 DB를 통과해 다시 조회해야 확인할 수 있다.
 */
// 이 클래스는 "마지막 관리자"라는 **전역 조건**을 만들려고 다른 SYSTEM_ADMIN을 강등시킨다.
// 같은 컨텍스트를 쓰는 뒤 테스트가 그 상태를 물려받으면 안 되므로 끝나고 컨텍스트를 버린다.
@SpringBootTest
@ActiveProfiles("test")
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
class UserServiceTransactionTest {

    @Autowired
    private UserService userService;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private DepartmentRepository departmentRepository;

    @Autowired
    private AdminAuditLogRepository adminAuditLogRepository;

    @Test
    @DisplayName("역할·부서 동시 변경 — 역할 변경 실패 시 앞선 부서 배정과 감사 기록도 롤백된다")
    void changeRoleAndDepartment_역할실패시부서와감사도롤백() {
        String suffix = UUID.randomUUID().toString();
        Department department = departmentRepository.save(
                Department.create("롤백검증팀-" + suffix, "트랜잭션 롤백 검증", null));

        User target = User.create(
                "롤백검증관리자",
                "rollback-" + suffix + "@mlsoft.com",
                Role.SYSTEM_ADMIN);
        target.completeOnboarding(LocalDate.now().minusYears(1), LocalDate.of(1990, 1, 1));
        target = userRepository.saveAndFlush(target);

        // 역할 변경 단계가 **확실히** 실패하게 만든다.
        // changeDepartment와 changeRole은 둘 다 validateNotRetired를 부르고, 부서는 활성이어야
        // 하므로 **1단계는 통과하고 2단계만 실패하는** 가드는 마지막 관리자 보호뿐이다.
        // 그런데 이 컨텍스트에는 다른 테스트가 만든 관리자가 남아 있어, 조건을 여기서 직접 세운다
        // (그러지 않으면 승격이 그냥 성공해 롤백을 검증하지 못한 채 통과한다 — 실제로 겪었다).
        Long savedTargetId = target.getId();   // 람다에서 쓰려면 값이 고정돼 있어야 한다
        List<User> others = userRepository.findAll().stream()
                .filter(user -> user.getRole() == Role.SYSTEM_ADMIN)
                .filter(user -> !user.getId().equals(savedTargetId))
                .toList();
        others.forEach(user -> user.changeRole(Role.EMPLOYEE));
        userRepository.saveAllAndFlush(others);

        long auditCountBefore = adminAuditLogRepository.count();
        Long targetId = target.getId();

        BusinessException exception = assertThrows(
                BusinessException.class,
                () -> userService.changeRoleAndDepartment(
                        targetId,
                        Role.TEAM_LEADER,
                        department.getId(),
                        targetId));

        assertEquals(ErrorCode.LAST_SYSTEM_ADMIN, exception.getErrorCode());

        User reloaded = userRepository.findById(targetId).orElseThrow();
        assertNull(reloaded.getDepartment(), "역할 변경 실패 뒤에도 부서 배정이 남았다");
        assertEquals(Role.SYSTEM_ADMIN, reloaded.getRole());
        assertEquals(
                auditCountBefore,
                adminAuditLogRepository.count(),
                "롤백된 부서 변경의 감사 기록이 남았다");
    }
}
