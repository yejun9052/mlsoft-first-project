package com.mlsoft.backend.config;

import com.mlsoft.backend.domain.common.RequestAction;
import com.mlsoft.backend.domain.common.RequestStatus;
import com.mlsoft.backend.domain.department.repository.DepartmentRepository;
import com.mlsoft.backend.domain.leave.entity.LeaveRequest;
import com.mlsoft.backend.domain.leave.repository.LeaveActionHistoryRepository;
import com.mlsoft.backend.domain.leave.repository.LeaveRequestRepository;
import com.mlsoft.backend.domain.policy.service.LeavePolicyService;
import com.mlsoft.backend.domain.policy.service.PolicyConfigReader;
import com.mlsoft.backend.domain.user.entity.User;
import com.mlsoft.backend.domain.user.repository.UserRepository;
import com.mlsoft.backend.domain.welfare.repository.WelfareActionHistoryRepository;
import com.mlsoft.backend.domain.welfare.repository.WelfarePolicyRepository;
import com.mlsoft.backend.domain.welfare.repository.WelfareRequestRepository;
import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.ApplicationContext;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 시연 데이터 시더 통합 테스트 (H2).
 *
 * <p>시더는 {@code @Profile("local")}이라 test 프로필에서는 <b>빈으로 등록조차 되지 않는다</b> —
 * {@code @Import}로도 우회되지 않는다. 그래서 여기서는 협력자를 주입받아 직접 생성해 실행한다.
 * 등록되지 않는다는 사실 자체가 안전장치이므로 아래 회귀 테스트로 고정한다.
 *
 * <p>가장 중요한 검증은 <b>시딩된 데이터가 잔액 불변식을 지키는가</b>다.
 * 시더가 행을 직접 짜 넣으면 {@code advance_days = max(0, use − base − bonus)}가 깨진 데이터가 만들어지고
 * (리뷰 I-1), 그 위에서 하는 모든 화면·API 검증이 의미를 잃는다.
 */
@SpringBootTest
@ActiveProfiles("test")
@Transactional
class DemoDataInitializerTest {

    @Autowired
    private ApplicationContext applicationContext;
    @Autowired
    private UserRepository userRepository;
    @Autowired
    private DepartmentRepository departmentRepository;
    @Autowired
    private LeaveRequestRepository leaveRequestRepository;
    @Autowired
    private LeaveActionHistoryRepository leaveActionHistoryRepository;
    @Autowired
    private WelfarePolicyRepository welfarePolicyRepository;
    @Autowired
    private WelfareRequestRepository welfareRequestRepository;
    @Autowired
    private WelfareActionHistoryRepository welfareActionHistoryRepository;
    @Autowired
    private LeavePolicyService leavePolicyService;
    @Autowired
    private PolicyConfigReader policyConfigReader;
    @Autowired
    private EntityManager entityManager;

    private DemoDataInitializer demoDataInitializer;

    @BeforeEach
    void setUp() {
        demoDataInitializer = new DemoDataInitializer(
                userRepository, departmentRepository, leaveRequestRepository, leaveActionHistoryRepository,
                welfarePolicyRepository, welfareRequestRepository, welfareActionHistoryRepository,
                leavePolicyService, policyConfigReader, entityManager);
    }

    @Test
    @DisplayName("안전장치 — local 프로필이 아니면 시더가 빈으로 등록되지 않는다")
    void notRegistered_outsideLocalProfile() {
        assertTrue(applicationContext.getBeansOfType(DemoDataInitializer.class).isEmpty(),
                "시연 계정이 운영 DB에 들어가면 실제 사원과 구분할 방법이 없다 — @Profile(\"local\")을 지우지 말 것");
    }

    /** DemoDataInitializer가 만드는 사원 이름 8명 — 시더 계정 판별용 */
    private static final java.util.Set<String> DEMO_NAMES = java.util.Set.of(
            "김도현", "이서연", "윤나래", "박준호", "최유진", "정민석", "한소영", "오지훈");

    @Test
    @DisplayName("시딩 — 사원·신청·이력이 생성되고 모든 사원이 잔액 불변식을 만족한다")
    void run_createsConsistentDemoData() {
        demoDataInitializer.run(null);

        List<User> demoUsers = demoUsers();
        assertEquals(8, demoUsers.size());

        // 잔액 불변식 — 시더가 도메인 메서드를 타지 않고 값을 직접 넣으면 여기서 깨진다
        for (User user : demoUsers) {
            BigDecimal bonus = user.getBonusDays() != null ? user.getBonusDays() : BigDecimal.ZERO;
            BigDecimal expectedAdvance = user.getUseDays()
                    .subtract(user.getBaseDays())
                    .subtract(bonus)
                    .max(BigDecimal.ZERO);
            assertEquals(0, expectedAdvance.compareTo(user.getAdvanceDays()),
                    user.getName() + " 잔액 불변식 위반: base=" + user.getBaseDays()
                            + " bonus=" + bonus + " use=" + user.getUseDays()
                            + " advance=" + user.getAdvanceDays());
        }

        // 결재 화면 각 탭이 볼 데이터가 실제로 있는지 — 하나라도 비면 화면이 빈 상태로 보인다
        assertFalse(leaveRequestRepository.findAll().isEmpty());
        assertTrue(hasStatus(RequestStatus.PENDING), "대기 탭에 보여줄 PENDING 신청이 없다");
        assertTrue(hasStatus(RequestStatus.APPROVED), "승인 탭에 보여줄 APPROVED 신청이 없다");
        assertTrue(hasStatus(RequestStatus.REJECTED), "반려 탭에 보여줄 REJECTED 신청이 없다");
        assertTrue(hasStatus(RequestStatus.CANCELLED), "취소된 신청이 없다");
        assertTrue(hasStatus(RequestStatus.CANCEL_PENDING), "소급 취소 대기 건이 없다");

        // 처리 이력 — 승인·반려 이력이 있어야 "처리 완료" 탭이 채워진다
        assertTrue(hasAction(RequestAction.APPROVED), "승인 이력이 없다");
        assertTrue(hasAction(RequestAction.REJECTED), "반려 이력이 없다");
    }

    @Test
    @DisplayName("시딩 — 두 번 실행해도 데이터가 중복되지 않는다 (재기동 안전)")
    void run_twice_isIdempotent() {
        demoDataInitializer.run(null);
        int usersAfterFirst = demoUsers().size();
        long requestsAfterFirst = leaveRequestRepository.count();

        demoDataInitializer.run(null);

        assertEquals(usersAfterFirst, demoUsers().size());
        assertEquals(requestsAfterFirst, leaveRequestRepository.count());
    }

    @Test
    @DisplayName("시딩 — 반려·취소된 신청은 선차감이 복구돼 사용 일수에 남지 않는다")
    void run_rejectedAndCancelled_restoreBalance() {
        demoDataInitializer.run(null);

        // 반려 건의 신청자 — 다른 신청이 없으므로 사용 일수가 0이어야 한다
        User rejectedApplicant = userRepository.findByEmail("minseok.jung@mlsoft.com").orElseThrow();
        assertEquals(0, BigDecimal.ZERO.compareTo(rejectedApplicant.getUseDays()),
                "반려된 신청의 선차감이 복구되지 않았다");

        // 즉시 취소 건의 신청자
        User cancelledApplicant = userRepository.findByEmail("jihoon.oh@mlsoft.com").orElseThrow();
        assertEquals(0, BigDecimal.ZERO.compareTo(cancelledApplicant.getUseDays()),
                "취소된 신청의 선차감이 복구되지 않았다");
    }

    /**
     * 시더가 만든 계정만 골라낸다.
     * <p>`@mlsoft.com`으로만 거르면 같은 H2를 쓰는 다른 테스트가 만든 계정까지 섞여
     * <b>실행 순서에 따라 개수 단언이 흔들린다.</b> 시더는 온보딩까지 마친 계정만 만들고,
     * 이름이 고정돼 있으므로 이름으로 판별한다.
     */
    private List<User> demoUsers() {
        return userRepository.findAll().stream()
                .filter(user -> user.getEmail().endsWith("@mlsoft.com"))
                .filter(user -> DEMO_NAMES.contains(user.getName()))
                .toList();
    }

    private boolean hasStatus(RequestStatus status) {
        return leaveRequestRepository.findAll().stream()
                .map(LeaveRequest::getStatus)
                .anyMatch(candidate -> candidate == status);
    }

    private boolean hasAction(RequestAction action) {
        return leaveActionHistoryRepository.findAll().stream()
                .anyMatch(history -> history.getAction() == action);
    }
}
