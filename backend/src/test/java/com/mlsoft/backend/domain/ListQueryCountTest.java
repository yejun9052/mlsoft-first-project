package com.mlsoft.backend.domain;

import com.mlsoft.backend.domain.common.RequestStatus;
import com.mlsoft.backend.domain.department.entity.Department;
import com.mlsoft.backend.domain.department.repository.DepartmentRepository;
import com.mlsoft.backend.domain.leave.entity.LeaveRequest;
import com.mlsoft.backend.domain.leave.entity.LeaveType;
import com.mlsoft.backend.domain.leave.repository.LeaveRequestRepository;
import com.mlsoft.backend.domain.user.entity.Role;
import com.mlsoft.backend.domain.user.entity.User;
import com.mlsoft.backend.domain.user.repository.UserRepository;
import com.mlsoft.backend.domain.welfare.entity.WelfarePolicy;
import com.mlsoft.backend.domain.welfare.entity.WelfareRequest;
import com.mlsoft.backend.domain.welfare.entity.WelfareTarget;
import com.mlsoft.backend.domain.welfare.repository.WelfarePolicyRepository;
import com.mlsoft.backend.domain.welfare.repository.WelfareRequestRepository;
import jakarta.persistence.EntityManager;
import org.hibernate.SessionFactory;
import org.hibernate.stat.Statistics;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 목록 조회의 <b>실제 쿼리 수</b> 검증 (리뷰 D-2).
 *
 * <p>{@code @EntityGraph}를 붙였다는 사실만으로는 N+1이 사라졌다고 말할 수 없다 —
 * 응답 DTO가 그래프에 없는 연관을 하나라도 읽으면 그 자리에서 다시 쿼리가 나간다.
 * 그래서 Hibernate {@link Statistics}로 <b>세어서</b> 고정한다.
 *
 * <p>단정은 "정확히 몇 개"가 아니라 <b>행 수에 비례하지 않는다</b>는 것이다.
 * 행을 5개 만들고 상한을 낮게 잡으면, 그래프를 빼는 순간 초과해서 실패한다.
 *
 * <p>{@code dates} 컬렉션은 {@code @BatchSize(50)}라 <b>1회</b>가 더 붙는다 — 그래프에 넣으면
 * 페이징이 메모리로 가므로(HHH90003004) 의도한 설계다. 상한에 그 1회를 포함해 둔다.
 */
@SpringBootTest(properties = "spring.jpa.properties.hibernate.generate_statistics=true")
@ActiveProfiles("test")
// 트랜잭션 안에서 돌려야 flush/clear로 1차 캐시를 비울 수 있고, 만든 데이터가 롤백돼
// 다른 테스트의 목록 조회 결과를 오염시키지 않는다 (SecurityAccessMatrixTest와 같은 패턴).
@Transactional
class ListQueryCountTest {

    /** 목록에 담을 행 수 — 상한을 넘기려면 N+1이 실제로 있어야 하는 정도 */
    private static final int ROWS = 5;

    @Autowired
    private EntityManager entityManager;
    @Autowired
    private UserRepository userRepository;
    @Autowired
    private DepartmentRepository departmentRepository;
    @Autowired
    private LeaveRequestRepository leaveRequestRepository;
    @Autowired
    private WelfareRequestRepository welfareRequestRepository;
    @Autowired
    private WelfarePolicyRepository welfarePolicyRepository;

    private Statistics statistics;

    @BeforeEach
    void 통계_초기화() {
        statistics = entityManager.getEntityManagerFactory()
                .unwrap(SessionFactory.class)
                .getStatistics();
        statistics.clear();
    }

    @Test
    @DisplayName("연차 관리자 목록 — 행 수에 비례하는 추가 쿼리가 없다 (신청자·부서·승인자 2명)")
    void 연차_관리자목록_쿼리수() {
        Department department = saveDepartment("쿼리검증팀");
        User approver = saveUser("승인자", department);
        User sub = saveUser("서브승인자", department);
        for (int i = 0; i < ROWS; i++) {
            saveLeave(saveUser("신청자" + i, department), approver, sub, i);
        }
        clearPersistenceContextAndStats();

        // 응답 변환까지 포함해야 의미가 있다 — LAZY 접근은 DTO를 만들 때 일어난다
        Page<LeaveRequest> page = leaveRequestRepository.findForAdmin(
                RequestStatus.PENDING, null, PageRequest.of(0, 20));
        page.getContent().forEach(this::touchLeaveResponseFields);

        // 목록 1 + count 1 + dates 배치 1 = 3. 여유를 둬 4로 잡는다.
        // 그래프를 빼면 신청자·부서·승인자 2명이 행마다 붙어 5행에서 20회 이상이 된다.
        assertQueryCountAtMost(4, page.getNumberOfElements());
    }

    @Test
    @DisplayName("복리후생 관리자 목록 — 신청자·부서·정책이 행마다 조회되지 않는다")
    void 복리후생_관리자목록_쿼리수() {
        Department department = saveDepartment("복리검증팀");
        User approver = saveUser("승인자", department);
        WelfarePolicy policy = savePolicy();
        for (int i = 0; i < ROWS; i++) {
            welfareRequestRepository.save(WelfareRequest.create(
                    policy, saveUser("복리신청자" + i, department), "사유" + i, approver, null));
        }
        clearPersistenceContextAndStats();

        Page<WelfareRequest> page = welfareRequestRepository.findAll(PageRequest.of(0, 20));
        page.getContent().forEach(welfare -> {
            welfare.getUser().getName();
            Department dept = welfare.getUser().getDepartment();
            if (dept != null) {
                dept.getName();
            }
            welfare.getPolicy().getCategory();
            // 승인자는 id만 읽는다 — 프록시에 FK가 있어 쿼리가 나가지 않아야 한다 (리뷰 D-5)
            welfare.getPrimaryApprover().getId();
        });

        // 목록 1 + count 1 = 2. 컬렉션이 없어 배치 조회도 없다.
        assertQueryCountAtMost(3, page.getNumberOfElements());
    }

    @Test
    @DisplayName("승인자 프록시의 getId()는 쿼리를 만들지 않는다 (리뷰 D-5 전제)")
    void 승인자_프록시_id접근_쿼리없음() {
        // 이 성질이 깨지면 WelfareResponse가 행마다 승인자를 조회한다 —
        // 그때는 승인자도 @EntityGraph에 넣어야 한다.
        Department department = saveDepartment("프록시검증팀");
        User approver = saveUser("승인자", department);
        WelfareRequest saved = welfareRequestRepository.save(WelfareRequest.create(
                savePolicy(), saveUser("신청자", department), "사유", approver, null));
        clearPersistenceContextAndStats();

        WelfareRequest found = welfareRequestRepository.findById(saved.getId()).orElseThrow();
        long afterFind = statistics.getPrepareStatementCount();
        found.getPrimaryApprover().getId();

        assertEquals(afterFind, statistics.getPrepareStatementCount(),
                "승인자 프록시에서 id를 읽었는데 쿼리가 나갔다");
    }

    @Test
    @DisplayName("사원 목록 — 부서명이 행마다 조회되지 않는다")
    void 사원목록_쿼리수() {
        Department department = saveDepartment("사원목록검증팀");
        for (int i = 0; i < ROWS; i++) {
            saveUser("목록사원" + i, department);
        }
        clearPersistenceContextAndStats();

        Page<User> page = userRepository.search(null, "목록사원", PageRequest.of(0, 20));
        page.getContent().forEach(user -> {
            Department dept = user.getDepartment();
            if (dept != null) {
                dept.getName();
            }
        });

        assertQueryCountAtMost(3, page.getNumberOfElements());
    }

    // ---- 헬퍼 ----

    /** LeaveResponse가 읽는 연관을 그대로 훑는다 */
    private void touchLeaveResponseFields(LeaveRequest leave) {
        leave.getUser().getName();
        Department dept = leave.getUser().getDepartment();
        if (dept != null) {
            dept.getName();
        }
        leave.getPrimaryApprover().getName();
        if (leave.getSubApprover() != null) {
            leave.getSubApprover().getName();
        }
        leave.getDates().size();
    }

    private void assertQueryCountAtMost(long limit, int rowCount) {
        long executed = statistics.getPrepareStatementCount();
        assertTrue(rowCount >= ROWS, "검증할 행이 부족하다 — 실제 " + rowCount + "행");
        assertTrue(executed <= limit,
                "행 " + rowCount + "개 조회에 쿼리 " + executed + "개 (상한 " + limit + ") — N+1이 살아 있다");
    }

    /**
     * 1차 캐시를 비우고 통계를 초기화한다.
     * 비우지 않으면 방금 저장한 엔티티가 영속성 컨텍스트에 남아 LAZY 접근이 쿼리 없이 성공해
     * 테스트가 <b>N+1이 있어도 통과</b>한다.
     */
    private void clearPersistenceContextAndStats() {
        entityManager.flush();
        entityManager.clear();
        statistics.clear();
    }

    private Department saveDepartment(String name) {
        return departmentRepository.save(Department.create(name + System.nanoTime(), "설명", null));
    }

    private User saveUser(String name, Department department) {
        User user = User.builder()
                .name(name)
                // 시연 데이터 시더 테스트가 @mlsoft.com 계정을 자기 것으로 세지 않게 도메인을 분리한다
                .email("qcount-" + System.nanoTime() + "@integration.test")
                .role(Role.EMPLOYEE)
                .department(department)
                .baseDays(new BigDecimal("15.0"))
                .useDays(BigDecimal.ZERO)
                .bonusDays(BigDecimal.ZERO)
                .advanceDays(BigDecimal.ZERO)
                .isActive(true)
                .build();
        return userRepository.save(user);
    }

    private void saveLeave(User applicant, User approver, User sub, int offset) {
        leaveRequestRepository.save(LeaveRequest.create(applicant, LeaveType.ANNUAL,
                List.of(LocalDate.now().plusDays(offset + 1L)), "휴식", approver, sub));
    }

    private WelfarePolicy savePolicy() {
        return welfarePolicyRepository.save(WelfarePolicy.create(
                "결혼" + System.nanoTime(), WelfareTarget.SELF, new BigDecimal("7.0"), "청첩장", "본인 결혼"));
    }
}
