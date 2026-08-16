package com.mlsoft.backend.config;

import com.mlsoft.backend.domain.common.RequestAction;
import com.mlsoft.backend.domain.department.entity.Department;
import com.mlsoft.backend.domain.department.repository.DepartmentRepository;
import com.mlsoft.backend.domain.leave.entity.LeaveActionHistory;
import com.mlsoft.backend.domain.leave.entity.LeaveRequest;
import com.mlsoft.backend.domain.leave.entity.LeaveType;
import com.mlsoft.backend.domain.leave.repository.LeaveActionHistoryRepository;
import com.mlsoft.backend.domain.leave.repository.LeaveRequestRepository;
import com.mlsoft.backend.domain.policy.entity.PolicyConfigKey;
import com.mlsoft.backend.domain.policy.service.LeavePolicyService;
import com.mlsoft.backend.domain.policy.service.PolicyConfigReader;
import com.mlsoft.backend.domain.user.entity.Role;
import com.mlsoft.backend.domain.user.entity.User;
import com.mlsoft.backend.domain.user.repository.UserRepository;
import com.mlsoft.backend.domain.welfare.entity.WelfarePolicy;
import com.mlsoft.backend.domain.welfare.entity.WelfareActionHistory;
import com.mlsoft.backend.domain.welfare.entity.WelfareRequest;
import com.mlsoft.backend.domain.welfare.repository.WelfareActionHistoryRepository;
import com.mlsoft.backend.domain.welfare.repository.WelfarePolicyRepository;
import com.mlsoft.backend.domain.welfare.repository.WelfareRequestRepository;
import jakarta.persistence.EntityManager;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.context.annotation.Profile;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/**
 * 로컬 개발용 시연 데이터 — <b>사원 계정·연차 신청·처리 이력을 실제로 만든다</b>.
 *
 * <p>프론트엔드가 화면을 채우려고 하드코딩한 mock(`mocks/data.js`)을 없애기 위한 것이다(리뷰 F-2).
 * mock은 실 API를 타지 않아서 두 가지 문제가 있었다 —
 * ① 화면은 그럴듯한데 실제 API·권한·페이징이 검증되지 않는다,
 * ② 결재 "처리 완료" 탭에 <b>실명처럼 보이는 이름</b>이 감사 기록처럼 렌더됐다.
 * DB에 실제 행을 만들면 화면이 진짜 API를 타므로 두 문제가 함께 사라진다.
 *
 * <h2>안전장치</h2>
 * <ul>
 *   <li><b>{@code local} 프로필에서만 동작한다</b> — 운영·테스트 프로필에서는 빈으로 등록조차 되지 않는다.
 *       시연 계정이 운영 DB에 들어가면 실제 사원 목록과 섞여 구분할 방법이 없다</li>
 *   <li>재기동 안전 — 시연 계정이 이미 있으면 전부 건너뛴다</li>
 *   <li>{@link DataInitializer} 다음에 실행된다({@code @Order}) — 부서·연차 정책·복리후생 정책이 먼저 있어야 한다</li>
 * </ul>
 *
 * <h2>잔액은 도메인 메서드로만 만든다</h2>
 * 행을 직접 짜 넣지 않고 실제 흐름과 같은 순서로 도메인 메서드를 호출한다 —
 * 신청 시 {@code deductLeave}로 선차감하고, 반려·취소 상태로 만들 땐 {@code restoreLeave}로 되돌린다.
 * 잔액을 손으로 채우면 {@code advance_days = max(0, use − base − bonus)} 불변식이 깨진 데이터가 만들어져
 * (리뷰 I-1) 그 위에서 하는 모든 검증이 무의미해진다.
 *
 * <h2>실명 사용 금지</h2>
 * 이름은 <b>특정 인물을 연상시키지 않는 흔한 조합</b>만 쓴다. 이전 mock이 유명인 실명을 써서
 * 화면에 실제 사원 기록처럼 보였던 것이 F-2의 핵심이었다.
 */
@Slf4j
@Component
@Profile("local")
@Order(100) // DataInitializer(기본 순서) 이후
@RequiredArgsConstructor
public class DemoDataInitializer implements ApplicationRunner {

    private static final ZoneId KST = ZoneId.of("Asia/Seoul");

    /**
     * 시연 계정의 이메일 도메인.
     *
     * <p><b>실재하는 회사 도메인을 쓰면 안 된다.</b> 시연 계정은 결재·복리후생 이력을 갖고 있어
     * 이메일 알림의 실제 수신자가 된다 — {@code EmailDeliveryService}는 {@code users.email}로
     * 그대로 발송한다. 2026-08-16에 이 값이 {@code mlsoft.com}이라 시연 계정 앞으로 메일이
     * 실제로 나갔다(3건). 그 뒤 소유 도메인으로 바꿨다.
     *
     * <p>도메인을 다시 바꿀 때는 <b>이미 시딩된 DB의 기존 행도 함께 UPDATE</b>해야 한다.
     * 여기만 고치면 재기동해도 MARKER_EMAIL이 안 보여 새 계정이 추가로 생길 뿐,
     * 옛 주소를 가진 행은 그대로 남아 계속 발송 대상이 된다
     * (보정 SQL: {@code db/backfill-2026-08-16-demo-email-domain.sql}).
     */
    // package-private인 것은 의도다 — 같은 패키지의 DemoDataInitializerTest가 이 값을 참조한다.
    // 테스트가 도메인 문자열을 따로 갖고 있으면 도메인을 바꿀 때마다 테스트가 깨진다(실제로 깨졌다).
    static final String DEMO_EMAIL_DOMAIN = "@yedevjun.com";

    /** 시연 데이터 존재 판별 기준 계정 — 이 계정이 있으면 이미 시딩된 것으로 본다 */
    private static final String MARKER_EMAIL = "dohyun.kim" + DEMO_EMAIL_DOMAIN;

    private final UserRepository userRepository;
    private final DepartmentRepository departmentRepository;
    private final LeaveRequestRepository leaveRequestRepository;
    private final LeaveActionHistoryRepository leaveActionHistoryRepository;
    private final WelfarePolicyRepository welfarePolicyRepository;
    private final WelfareRequestRepository welfareRequestRepository;
    private final WelfareActionHistoryRepository welfareActionHistoryRepository;
    private final LeavePolicyService leavePolicyService;
    private final PolicyConfigReader policyConfigReader;
    private final EntityManager entityManager;

    @Override
    @Transactional
    public void run(ApplicationArguments args) {
        if (userRepository.findByEmail(MARKER_EMAIL).isPresent()) {
            log.info("[DemoData] 시연 데이터가 이미 있어 건너뜀");
            return;
        }

        Department dev = department("개발팀", "서비스 개발·운영");
        Department design = department("디자인팀", "제품 디자인·UX");
        Department support = department("경영지원팀", "인사·총무·회계");

        // 팀장 3명 — 부서별 결재 승인자가 된다 (팀장 공석이면 SYSTEM_ADMIN fallback, 검증 Y-3)
        User devLead = employee("김도현", "dohyun.kim", Role.TEAM_LEADER, dev, "팀장", "2019-03-04", "1988-06-21");
        User designLead = employee("이서연", "seoyeon.lee", Role.TEAM_LEADER, design, "팀장", "2020-01-06", "1990-02-14");
        User supportLead = employee("윤나래", "narae.yoon", Role.TEAM_LEADER, support, "팀장", "2021-11-15", "1991-09-08");
        dev.assignLeader(devLead);
        design.assignLeader(designLead);
        support.assignLeader(supportLead);

        User devSenior = employee("박준호", "junho.park", Role.EMPLOYEE, dev, "시니어 개발자", "2021-05-10", "1993-04-02");
        User devMid = employee("최유진", "yujin.choi", Role.EMPLOYEE, dev, "개발자", "2022-09-01", "1995-12-19");
        User devJunior = employee("정민석", "minseok.jung", Role.EMPLOYEE, dev, "개발자", "2024-03-11", "1998-07-30");
        User designer = employee("한소영", "soyoung.han", Role.EMPLOYEE, design, "UX 디자이너", "2023-07-03", "1996-01-25");
        User productDesigner = employee("오지훈", "jihoon.oh", Role.EMPLOYEE, design, "프로덕트 디자이너", "2025-02-17", "1999-05-11");

        // ── 처리 완료된 신청 (승인/반려 탭) ─────────────────────────────────
        // 과거 날짜를 쓰므로 LeaveService.apply를 타지 않는다 — 그쪽은 과거 날짜를 거부한다(정상 동작).
        // 대신 apply가 하는 일(생성 → 선차감 → 이력)을 같은 순서로 직접 수행한다.
        approvedLeave(devSenior, devLead, LeaveType.ANNUAL, pastWeekdays(24, 2), "여름 휴가", "잘 다녀오세요.", 22);
        approvedLeave(devMid, devLead, LeaveType.HALF_AM, pastWeekdays(17, 1), "병원 진료", "확인했습니다.", 16);
        approvedLeave(designer, designLead, LeaveType.ANNUAL, pastWeekdays(11, 1), "개인 사유", "승인합니다.", 10);
        rejectedLeave(devJunior, devLead, LeaveType.ANNUAL, pastWeekdays(9, 1), "개인 사유",
                "해당 주 배포 일정과 겹칩니다. 일정 조정 후 재신청 바랍니다.", 8);
        cancelledLeave(productDesigner, designLead, LeaveType.ANNUAL, futureWeekdays(9, 1), "일정 변경",
                "개인 일정이 취소되어 반납합니다.", 3);

        // ── 대기 중인 신청 (대기 탭) ────────────────────────────────────────
        pendingLeave(designer, designLead, LeaveType.ANNUAL, futureWeekdays(6, 3), "가족 여행", 1);
        pendingLeave(devMid, devLead, LeaveType.HALF_PM, futureWeekdays(4, 1), "개인 용무", 0);
        // 소급 취소 대기 — 승인된 과거 연차를 되돌리려는 건. 대기 탭에서 "취소 요청"으로 표시된다
        cancelPendingLeave(devSenior, devLead, LeaveType.ANNUAL, pastWeekdays(5, 1), "출근 처리 요청",
                "실제로는 출근했습니다. 연차를 되돌려주세요.", 4);

        // ── 복리후생 ────────────────────────────────────────────────────────
        approvedWelfare(designer, designLead, "결혼", "본인 결혼", "축하합니다.", 30);
        pendingWelfare(devJunior, devLead, "조의", "조부모 조의", 1);

        log.info("[DemoData] 시연 데이터 생성 완료 — 부서 3, 사원 8, 연차 신청 8, 복리후생 2");
        log.warn("[DemoData] 이 계정들은 local 프로필 전용 시연 데이터다. 실제 사원이 아니다.");
    }

    // ─────────────────────────── 사원·부서 ───────────────────────────

    /** 부서 조회 또는 생성 (DataInitializer가 만든 "미배정"과 별개) */
    private Department department(String name, String description) {
        return departmentRepository.findByName(name)
                .orElseGet(() -> departmentRepository.save(Department.create(name, description, null)));
    }

    /**
     * 시연 사원 생성 — 온보딩까지 완료된 상태로 만든다.
     * 연차는 {@link LeavePolicyService}로 실제 근속년수 기준 계산해 부여하므로,
     * 입사일이 다른 사원끼리 잔여 연차가 자연스럽게 달라진다.
     */
    private User employee(String name, String emailLocalPart, Role role, Department department,
                          String position, String hireDate, String birthDay) {
        LocalDate hire = LocalDate.parse(hireDate);
        User user = User.builder()
                .name(name)
                .email(emailLocalPart + DEMO_EMAIL_DOMAIN)
                .role(role)
                .position(position)
                .baseDays(BigDecimal.ZERO)
                .useDays(BigDecimal.ZERO)
                .bonusDays(BigDecimal.ZERO)
                .advanceDays(BigDecimal.ZERO)
                .isActive(true)
                .build();
        user.assignDepartment(department);
        user.completeOnboarding(hire, LocalDate.parse(birthDay));

        // 온보딩과 같은 방식으로 연차 부여 — 만 근속년수 기준 정책 일수
        int years = (int) ChronoUnit.YEARS.between(hire, LocalDate.now(KST));
        LocalDate lastReset = hire.plusYears(years);
        user.resetAnnualLeave(leavePolicyService.calculateAnnualLeaveDays(Math.max(years, 1)), lastReset);
        return userRepository.save(user);
    }

    // ─────────────────────────── 연차 신청 ───────────────────────────

    /** 승인된 연차 — 선차감이 유지된다 */
    private void approvedLeave(User applicant, User approver, LeaveType type, List<LocalDate> dates,
                               String reason, String comment, int daysAgo) {
        LeaveRequest leave = applyLeave(applicant, approver, type, dates, reason, daysAgo);
        leave.approve();
        history(leave, approver, RequestAction.APPROVED, comment, daysAgo - 1);
    }

    /** 반려된 연차 — 선차감을 복구한다 (실제 반려 흐름과 동일) */
    private void rejectedLeave(User applicant, User approver, LeaveType type, List<LocalDate> dates,
                               String reason, String comment, int daysAgo) {
        LeaveRequest leave = applyLeave(applicant, approver, type, dates, reason, daysAgo);
        leave.reject();
        applicant.restoreLeave(leave.getDays());
        history(leave, approver, RequestAction.REJECTED, comment, daysAgo - 1);
    }

    /** 본인이 즉시 취소한 연차 — 선차감을 복구한다. 처리자(actor)는 신청자 본인이다 */
    private void cancelledLeave(User applicant, User approver, LeaveType type, List<LocalDate> dates,
                                String reason, String cancelReason, int daysAgo) {
        LeaveRequest leave = applyLeave(applicant, approver, type, dates, reason, daysAgo);
        leave.approve();
        history(leave, approver, RequestAction.APPROVED, "승인합니다.", daysAgo);
        leave.cancel(cancelReason);
        applicant.restoreLeave(leave.getDays());
        history(leave, applicant, RequestAction.CANCELLED, cancelReason, daysAgo - 1);
    }

    /** 결재 대기 중인 연차 */
    private void pendingLeave(User applicant, User approver, LeaveType type, List<LocalDate> dates,
                              String reason, int daysAgo) {
        applyLeave(applicant, approver, type, dates, reason, daysAgo);
    }

    /** 소급 취소 대기 — 승인된 과거 연차의 취소 요청. 복구는 승인자 승인 시점으로 미뤄진다 */
    private void cancelPendingLeave(User applicant, User approver, LeaveType type, List<LocalDate> dates,
                                    String reason, String cancelReason, int daysAgo) {
        LeaveRequest leave = applyLeave(applicant, approver, type, dates, reason, daysAgo);
        leave.approve();
        history(leave, approver, RequestAction.APPROVED, "승인합니다.", daysAgo);
        leave.requestCancel(cancelReason);
        history(leave, applicant, RequestAction.CANCEL_PENDING, cancelReason, daysAgo - 1);
    }

    /**
     * 신청 생성 + 선차감 + PENDING 이력 — {@code LeaveService.apply}가 하는 일과 같은 순서.
     * 당겨쓰기 설정·상한도 실제와 동일하게 반영한다(설정이 꺼져 있고 잔여가 부족하면 여기서 예외가 난다 —
     * 시연 데이터가 정책을 어기고 있다는 뜻이므로 조용히 넘기지 않는다).
     */
    private LeaveRequest applyLeave(User applicant, User approver, LeaveType type, List<LocalDate> dates,
                                    String reason, int daysAgo) {
        LeaveRequest leave = LeaveRequest.create(applicant, type, dates, reason, approver, null);
        BigDecimal advanceUsed = applicant.deductLeave(
                leave.getDays(),
                policyConfigReader.getBoolean(PolicyConfigKey.ADVANCE_LEAVE_ENABLED),
                policyConfigReader.getDecimal(PolicyConfigKey.ADVANCE_MAX_DAYS));
        leave.recordAdvanceUsage(advanceUsed);
        leaveRequestRepository.save(leave);
        backdate("leave_requests", leave.getId(), daysAgo);
        history(leave, applicant, RequestAction.PENDING, reason, daysAgo);
        return leave;
    }

    private void history(LeaveRequest leave, User actor, RequestAction action, String comment, int daysAgo) {
        LeaveActionHistory saved = leaveActionHistoryRepository.save(
                LeaveActionHistory.create(leave, actor, action, comment));
        backdate("leave_action_history", saved.getId(), daysAgo);
    }

    // ─────────────────────────── 복리후생 ───────────────────────────

    /** 승인된 복리후생 — bonus_days가 가산된다 (WelfareService.processApproval과 동일) */
    private void approvedWelfare(User applicant, User approver, String category, String reason,
                                 String comment, int daysAgo) {
        findWelfarePolicy(category).ifPresent(policy -> {
            WelfareRequest request = welfareRequestRepository.save(
                    WelfareRequest.create(policy, applicant, reason, approver, null));
            backdate("welfare_requests",request.getId(), daysAgo);
            welfareHistory(request, applicant, RequestAction.PENDING, reason, daysAgo);
            request.approve();
            applicant.addBonusDays(request.getAddDays());
            welfareHistory(request, approver, RequestAction.APPROVED, comment, daysAgo - 2);
        });
    }

    /** 결재 대기 중인 복리후생 */
    private void pendingWelfare(User applicant, User approver, String category, String reason, int daysAgo) {
        findWelfarePolicy(category).ifPresent(policy -> {
            WelfareRequest request = welfareRequestRepository.save(
                    WelfareRequest.create(policy, applicant, reason, approver, null));
            backdate("welfare_requests",request.getId(), daysAgo);
            welfareHistory(request, applicant, RequestAction.PENDING, reason, daysAgo);
        });
    }

    private void welfareHistory(WelfareRequest request, User actor, RequestAction action,
                                String comment, int daysAgo) {
        WelfareActionHistory saved = welfareActionHistoryRepository.save(
                WelfareActionHistory.create(request, actor, action, comment));
        backdate("welfare_action_history", saved.getId(), daysAgo);
    }

    /** 카테고리 첫 정책 — DataInitializer가 시딩한 것에서 고른다 */
    private Optional<WelfarePolicy> findWelfarePolicy(String category) {
        Optional<WelfarePolicy> policy = welfarePolicyRepository.findByActiveTrueOrderByCategoryAscIdAsc().stream()
                .filter(candidate -> candidate.getCategory().equals(category))
                .findFirst();
        if (policy.isEmpty()) {
            log.warn("[DemoData] 복리후생 정책 '{}'을 찾지 못해 해당 시연 신청을 건너뜀", category);
        }
        return policy;
    }

    // ─────────────────────────── 시각 보정 ───────────────────────────

    /**
     * {@code created_at}을 과거로 되돌린다.
     *
     * <p>{@code @CreatedDate}가 자동으로 채우므로 JPA로는 과거 시각을 넣을 수 없고
     * ({@code @Column(updatable = false)}), 보정하지 않으면 <b>모든 이력의 처리 시각이 기동 시점</b>이 된다.
     * 이력 12건이 전부 같은 초에 찍혀 있으면 로그 화면의 정렬·페이징을 확인할 수 없다.
     *
     * <p>native update로 컬럼을 직접 쓰는 유일한 곳이다 — 시연 데이터 전용이며 운영 코드가 아니다.
     */
    private void backdate(String table, Long id, int daysAgo) {
        LocalDateTime timestamp = LocalDateTime.now(KST)
                .minusDays(Math.max(daysAgo, 0))
                .withHour(10 + (int) (id % 8))
                .withMinute((int) (id * 7 % 60))
                .withSecond(0)
                .withNano(0);
        entityManager.createNativeQuery("UPDATE " + table + " SET created_at = :timestamp WHERE id = :id")
                .setParameter("timestamp", timestamp)
                .setParameter("id", id)
                .executeUpdate();
    }

    // ─────────────────────────── 날짜 헬퍼 ───────────────────────────

    /** daysAgo일 전부터 과거로 평일 count개 (주말은 신청할 수 없다) */
    private List<LocalDate> pastWeekdays(int daysAgo, int count) {
        return weekdaysFrom(LocalDate.now(KST).minusDays(daysAgo), count);
    }

    /** daysFromNow일 후부터 평일 count개 */
    private List<LocalDate> futureWeekdays(int daysFromNow, int count) {
        return weekdaysFrom(LocalDate.now(KST).plusDays(daysFromNow), count);
    }

    private List<LocalDate> weekdaysFrom(LocalDate start, int count) {
        List<LocalDate> dates = new ArrayList<>();
        LocalDate date = start;
        while (dates.size() < count) {
            DayOfWeek dayOfWeek = date.getDayOfWeek();
            if (dayOfWeek != DayOfWeek.SATURDAY && dayOfWeek != DayOfWeek.SUNDAY) {
                dates.add(date);
            }
            date = date.plusDays(1);
        }
        return dates;
    }
}
