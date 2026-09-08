package com.mlsoft.backend.domain.user.service;

import com.mlsoft.backend.domain.audit.entity.AdminAction;
import com.mlsoft.backend.domain.audit.service.AdminAuditService;
import com.mlsoft.backend.domain.common.RequestAction;
import com.mlsoft.backend.domain.common.RequestStatus;
import com.mlsoft.backend.domain.department.entity.Department;
import com.mlsoft.backend.domain.department.repository.DepartmentRepository;
import com.mlsoft.backend.domain.leave.entity.LeaveActionHistory;
import com.mlsoft.backend.domain.leave.entity.LeaveRequest;
import com.mlsoft.backend.domain.leave.entity.LeaveType;
import com.mlsoft.backend.domain.leave.entity.LeaveResetHistory;
import com.mlsoft.backend.domain.leave.repository.LeaveActionHistoryRepository;
import com.mlsoft.backend.domain.leave.repository.LeaveRequestRepository;
import com.mlsoft.backend.domain.leave.repository.LeaveResetHistoryRepository;
import com.mlsoft.backend.domain.leave.service.AnnualLeaveResetService;
import com.mlsoft.backend.domain.leave.service.MonthlyLeaveGrantService;
import com.mlsoft.backend.domain.policy.entity.PolicyConfigKey;
import com.mlsoft.backend.domain.policy.service.LeavePolicyService;
import com.mlsoft.backend.domain.policy.service.PolicyConfigReader;
import com.mlsoft.backend.domain.user.dto.RehireRequest;
import com.mlsoft.backend.domain.user.entity.EmploymentPeriod;
import com.mlsoft.backend.domain.user.entity.OnboardingStatus;
import com.mlsoft.backend.domain.user.entity.Role;
import com.mlsoft.backend.domain.user.entity.User;
import com.mlsoft.backend.domain.user.repository.EmploymentPeriodRepository;
import com.mlsoft.backend.domain.user.repository.UserRepository;
import com.mlsoft.backend.domain.welfare.entity.WelfareActionHistory;
import com.mlsoft.backend.domain.welfare.entity.WelfarePolicy;
import com.mlsoft.backend.domain.welfare.entity.WelfareRequest;
import com.mlsoft.backend.domain.welfare.entity.WelfareTarget;
import com.mlsoft.backend.domain.welfare.repository.WelfareActionHistoryRepository;
import com.mlsoft.backend.domain.welfare.repository.WelfareRequestRepository;
import com.mlsoft.backend.domain.email.repository.EmailHistoryRepository;
import com.mlsoft.backend.global.exception.BusinessException;
import com.mlsoft.backend.global.exception.ErrorCode;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

/**
 * 재입사 처리 R1 회귀 테스트 (설계-초안/재입사자-처리-설계-2026-09-07 §9).
 *
 * <p>재입사로 잔액을 0으로 만드는 순간에도 이전 근속의 신청 상태·감사 이력과
 * 스케줄러의 기산일 경계를 함께 검증한다.
 */
@ExtendWith(MockitoExtension.class)
class UserRehireServiceTest {

    private static final Long TARGET_ID = 1L;
    private static final Long ACTOR_ID = 99L;
    private static final LocalDate OLD_HIRE_DATE = LocalDate.of(2020, 1, 15);
    private static final LocalDate RETIRED_AT = LocalDate.of(2025, 12, 31);
    private static final List<RequestStatus> REHIRE_CLOSE_STATUSES =
            List.of(RequestStatus.PENDING, RequestStatus.CANCEL_PENDING);

    @Mock
    private UserRepository userRepository;
    @Mock
    private DepartmentRepository departmentRepository;
    @Mock
    private EmploymentPeriodRepository employmentPeriodRepository;
    @Mock
    private LeaveRequestRepository leaveRequestRepository;
    @Mock
    private LeaveActionHistoryRepository leaveActionHistoryRepository;
    @Mock
    private WelfareRequestRepository welfareRequestRepository;
    @Mock
    private WelfareActionHistoryRepository welfareActionHistoryRepository;
    @Mock
    private EmailHistoryRepository emailHistoryRepository;
    @Mock
    private PolicyConfigReader policyConfigReader;
    @Mock
    private AdminAuditService adminAuditService;

    @Mock
    private LeaveResetHistoryRepository leaveResetHistoryRepository;
    @Mock
    private LeavePolicyService leavePolicyService;

    @InjectMocks
    private UserService userService;

    @Test
    @DisplayName("재입사 — 입사일·기산일·잔액·역할·직책·직급을 새 근속 기준으로 초기화한다")
    void rehire_resetsEmploymentBalanceAndRole() {
        User target = retiredUser();
        Department department = Department.create("개발팀", "설명", null);
        LocalDate rehireDate = LocalDate.of(2026, 2, 1);
        given(userRepository.findById(TARGET_ID)).willReturn(Optional.of(target));
        given(departmentRepository.findByIdAndActiveTrue(10L)).willReturn(Optional.of(department));
        given(employmentPeriodRepository.findTopByUserOrderBySeqDesc(target)).willReturn(Optional.empty());
        given(leaveRequestRepository.findByUserAndStatusIn(target, REHIRE_CLOSE_STATUSES)).willReturn(List.of());
        given(welfareRequestRepository.findByUserAndStatusIn(target, REHIRE_CLOSE_STATUSES)).willReturn(List.of());

        userService.rehire(TARGET_ID, new RehireRequest(rehireDate, 10L), ACTOR_ID);

        assertEquals(rehireDate, target.getHireDate());
        assertEquals(rehireDate, target.getLastResetDate());
        assertBigDecimal("0.0", target.getBaseDays());
        assertBigDecimal("0.0", target.getBonusDays());
        assertBigDecimal("0.0", target.getUseDays());
        assertBigDecimal("0.0", target.getAdvanceDays());
        assertEquals(0, target.getMonthlyGrantedCount());
        assertNull(target.getLastBirthdayGrantYear());
        assertTrue(target.isActive());
        assertNull(target.getRetiredAt());
        assertEquals(Role.EMPLOYEE, target.getRole());
        assertNull(target.getPosition());
        assertNull(target.getJobGrade());
        assertEquals(LocalDate.of(1990, 5, 20), target.getBirthDay());
        assertEquals(OnboardingStatus.COMPLETED, target.getOnboardingStatus());
        assertEquals(department, target.getDepartment());

        ArgumentCaptor<EmploymentPeriod> periodCaptor = ArgumentCaptor.forClass(EmploymentPeriod.class);
        verify(employmentPeriodRepository).save(periodCaptor.capture());
        EmploymentPeriod period = periodCaptor.getValue();
        assertEquals(1, period.getSeq());
        assertEquals(OLD_HIRE_DATE, period.getHireDate());
        assertEquals(RETIRED_AT, period.getRetiredAt());
        assertEquals(target, period.getUser());
        verify(adminAuditService).recordUserChange(eq(ACTOR_ID), eq(AdminAction.USER_REHIRED),
                eq(target), anyString(), anyString());
    }

    @Test
    @DisplayName("재입사 — PENDING·CANCEL_PENDING 신청은 취소하고 APPROVED는 보존한다")
    void rehire_closesLiveRequestsAndPreservesApproved() {
        User target = retiredUser();
        User actor = activeUser(ACTOR_ID, Role.SYSTEM_ADMIN);
        LeaveRequest pendingLeave = LeaveRequest.create(target, LeaveType.ANNUAL,
                List.of(LocalDate.of(2026, 1, 10)), "대기 신청", actor, null);
        LeaveRequest approvedLeave = LeaveRequest.create(target, LeaveType.ANNUAL,
                List.of(LocalDate.of(2025, 11, 10)), "승인 신청", actor, null);
        approvedLeave.approve();
        WelfarePolicy policy = WelfarePolicy.create("결혼", WelfareTarget.SELF,
                new BigDecimal("7.0"), "증빙", "설명");
        WelfareRequest pendingWelfare = WelfareRequest.create(policy, target, "복지 대기", actor, null);
        given(userRepository.findById(TARGET_ID)).willReturn(Optional.of(target));
        given(userRepository.getReferenceById(ACTOR_ID)).willReturn(actor);
        given(employmentPeriodRepository.findTopByUserOrderBySeqDesc(target)).willReturn(Optional.empty());
        given(leaveRequestRepository.findByUserAndStatusIn(target, REHIRE_CLOSE_STATUSES))
                .willReturn(List.of(pendingLeave));
        given(welfareRequestRepository.findByUserAndStatusIn(target, REHIRE_CLOSE_STATUSES))
                .willReturn(List.of(pendingWelfare));

        userService.rehire(TARGET_ID,
                new RehireRequest(LocalDate.of(2026, 2, 1), null), ACTOR_ID);

        assertEquals(RequestStatus.CANCELLED, pendingLeave.getStatus());
        assertEquals("재입사 처리로 자동 취소", pendingLeave.getCancelReason());
        assertEquals(RequestStatus.CANCELLED, pendingWelfare.getStatus());
        assertEquals(RequestStatus.APPROVED, approvedLeave.getStatus());
        assertBigDecimal("0.0", target.getUseDays());

        ArgumentCaptor<LeaveActionHistory> leaveHistoryCaptor = ArgumentCaptor.forClass(LeaveActionHistory.class);
        verify(leaveActionHistoryRepository).save(leaveHistoryCaptor.capture());
        assertEquals(RequestAction.CANCELLED, leaveHistoryCaptor.getValue().getAction());
        assertEquals(actor, leaveHistoryCaptor.getValue().getActor());

        ArgumentCaptor<WelfareActionHistory> welfareHistoryCaptor = ArgumentCaptor.forClass(WelfareActionHistory.class);
        verify(welfareActionHistoryRepository).save(welfareHistoryCaptor.capture());
        assertEquals(RequestAction.CANCELLED, welfareHistoryCaptor.getValue().getAction());
        assertEquals(actor, welfareHistoryCaptor.getValue().getActor());
    }

    @Test
    @DisplayName("재입사 — 두 번째 재입사에서는 종료 근속 순번이 2가 된다")
    void rehire_secondPeriod_usesSequenceTwo() {
        User target = retiredUser();
        EmploymentPeriod firstPeriod = EmploymentPeriod.create(target, 1,
                OLD_HIRE_DATE, RETIRED_AT);
        given(userRepository.findById(TARGET_ID)).willReturn(Optional.of(target));
        given(employmentPeriodRepository.findTopByUserOrderBySeqDesc(target))
                .willReturn(Optional.of(firstPeriod));
        given(leaveRequestRepository.findByUserAndStatusIn(target, REHIRE_CLOSE_STATUSES)).willReturn(List.of());
        given(welfareRequestRepository.findByUserAndStatusIn(target, REHIRE_CLOSE_STATUSES)).willReturn(List.of());

        userService.rehire(TARGET_ID,
                new RehireRequest(LocalDate.of(2027, 1, 1), null), ACTOR_ID);

        ArgumentCaptor<EmploymentPeriod> periodCaptor = ArgumentCaptor.forClass(EmploymentPeriod.class);
        verify(employmentPeriodRepository).save(periodCaptor.capture());
        assertEquals(2, periodCaptor.getValue().getSeq());
    }

    @Test
    @DisplayName("재입사 — 재직자이면 NOT_RETIRED로 거부하고 상태를 건드리지 않는다")
    void rehire_activeUser_rejected() {
        User target = activeUser(TARGET_ID, Role.EMPLOYEE);
        given(userRepository.findById(TARGET_ID)).willReturn(Optional.of(target));

        BusinessException exception = assertThrows(BusinessException.class,
                () -> userService.rehire(TARGET_ID,
                        new RehireRequest(LocalDate.of(2026, 2, 1), null), ACTOR_ID));

        assertEquals(ErrorCode.NOT_RETIRED, exception.getErrorCode());
        assertTrue(target.isActive());
        verify(employmentPeriodRepository, never()).save(any());
        verify(leaveRequestRepository, never()).findByUserAndStatusIn(any(), any());
    }

    @Test
    @DisplayName("재입사 — 재입사일이 퇴직일보다 빠르면 거부한다")
    void rehire_beforeRetirement_rejected() {
        User target = retiredUser();
        given(userRepository.findById(TARGET_ID)).willReturn(Optional.of(target));

        BusinessException exception = assertThrows(BusinessException.class,
                () -> userService.rehire(TARGET_ID,
                        new RehireRequest(RETIRED_AT.minusDays(1), null), ACTOR_ID));

        assertEquals(ErrorCode.REHIRE_DATE_BEFORE_RETIREMENT, exception.getErrorCode());
        verify(employmentPeriodRepository, never()).save(any());
        verify(leaveRequestRepository, never()).findByUserAndStatusIn(any(), any());
    }

    @Test
    @DisplayName("재입사 — 파기된 사원은 ALREADY_PURGED로 거부한다")
    void rehire_purgedUser_rejected() {
        User target = retiredUser();
        target.purge(LocalDateTime.of(2026, 1, 1, 0, 0));
        given(userRepository.findById(TARGET_ID)).willReturn(Optional.of(target));

        BusinessException exception = assertThrows(BusinessException.class,
                () -> userService.rehire(TARGET_ID,
                        new RehireRequest(LocalDate.of(2026, 2, 1), null), ACTOR_ID));

        assertEquals(ErrorCode.ALREADY_PURGED, exception.getErrorCode());
        verify(employmentPeriodRepository, never()).save(any());
        verify(leaveRequestRepository, never()).findByUserAndStatusIn(any(), any());
    }

    @Test
    @DisplayName("재입사 후 리셋 — 공백 기간을 catch-up하지 않는다")
    void rehire_thenAnnualReset_doesNotCatchUpRetirementGap() {
        User target = retiredUser();
        given(userRepository.findById(TARGET_ID)).willReturn(Optional.of(target));
        given(employmentPeriodRepository.findTopByUserOrderBySeqDesc(target)).willReturn(Optional.empty());
        given(leaveRequestRepository.findByUserAndStatusIn(target, REHIRE_CLOSE_STATUSES)).willReturn(List.of());
        given(welfareRequestRepository.findByUserAndStatusIn(target, REHIRE_CLOSE_STATUSES)).willReturn(List.of());

        LocalDate rehireDate = LocalDate.of(2026, 2, 1);
        userService.rehire(TARGET_ID, new RehireRequest(rehireDate, null), ACTOR_ID);

        AnnualLeaveResetService resetService = new AnnualLeaveResetService(
                userRepository, leaveRequestRepository, leaveResetHistoryRepository,
                leavePolicyService, policyConfigReader);
        int rounds = resetService.reset(TARGET_ID, rehireDate.plusMonths(1));

        assertEquals(0, rounds);
        assertEquals(rehireDate, target.getLastResetDate());
        verify(leaveResetHistoryRepository, never()).save(any());
        verify(leavePolicyService, never()).calculateAnnualLeaveDays(anyInt());
    }

    @Test
    @DisplayName("재입사 1개월 후 — 월차가 count 0에서 1일 적립된다")
    void rehire_oneMonthLater_grantsMonthlyLeave() {
        User target = retiredUser();
        LocalDate rehireDate = LocalDate.of(2026, 1, 31);
        given(userRepository.findById(TARGET_ID)).willReturn(Optional.of(target));
        given(employmentPeriodRepository.findTopByUserOrderBySeqDesc(target)).willReturn(Optional.empty());
        given(leaveRequestRepository.findByUserAndStatusIn(target, REHIRE_CLOSE_STATUSES)).willReturn(List.of());
        given(welfareRequestRepository.findByUserAndStatusIn(target, REHIRE_CLOSE_STATUSES)).willReturn(List.of());
        given(policyConfigReader.getInt(PolicyConfigKey.MONTHLY_LEAVE_MAX_DAYS)).willReturn(11);

        userService.rehire(TARGET_ID, new RehireRequest(rehireDate, null), ACTOR_ID);
        MonthlyLeaveGrantService monthlyService = new MonthlyLeaveGrantService(userRepository, policyConfigReader);

        int granted = monthlyService.grant(TARGET_ID, LocalDate.of(2026, 2, 28));

        assertEquals(1, granted);
        assertEquals(1, target.getMonthlyGrantedCount());
        assertBigDecimal("1.0", target.getBaseDays());
    }

    @Test
    @DisplayName("재입사 1년 후 — 리셋 잡이 정책 연차를 부여한다")
    void rehire_oneYearLater_resetsToPolicyAnnualLeave() {
        User target = retiredUser();
        LocalDate rehireDate = LocalDate.of(2026, 1, 1);
        given(userRepository.findById(TARGET_ID)).willReturn(Optional.of(target));
        given(employmentPeriodRepository.findTopByUserOrderBySeqDesc(target)).willReturn(Optional.empty());
        given(leaveRequestRepository.findByUserAndStatusIn(target, REHIRE_CLOSE_STATUSES)).willReturn(List.of());
        given(welfareRequestRepository.findByUserAndStatusIn(target, REHIRE_CLOSE_STATUSES)).willReturn(List.of());
        given(policyConfigReader.getBoolean(PolicyConfigKey.BONUS_CARRY_OVER_ENABLED)).willReturn(false);
        given(leavePolicyService.calculateAnnualLeaveDays(1)).willReturn(new BigDecimal("15.0"));
        given(leaveRequestRepository.sumPreDeductedDaysWithin(
                eq(target), eq(rehireDate.plusYears(1)), eq(rehireDate.plusYears(2)), any()))
                .willReturn(BigDecimal.ZERO);

        userService.rehire(TARGET_ID, new RehireRequest(rehireDate, null), ACTOR_ID);
        AnnualLeaveResetService resetService = new AnnualLeaveResetService(
                userRepository, leaveRequestRepository, leaveResetHistoryRepository,
                leavePolicyService, policyConfigReader);

        int rounds = resetService.reset(TARGET_ID, rehireDate.plusYears(1));

        assertEquals(1, rounds);
        assertEquals(rehireDate.plusYears(1), target.getLastResetDate());
        assertBigDecimal("15.0", target.getBaseDays());
        assertBigDecimal("0.0", target.getUseDays());
        ArgumentCaptor<LeaveResetHistory> historyCaptor = ArgumentCaptor.forClass(LeaveResetHistory.class);
        verify(leaveResetHistoryRepository).save(historyCaptor.capture());
        assertBigDecimal("15.0", historyCaptor.getValue().getNewBaseDays());
    }

    @Test
    @DisplayName("재입사 — 처리 경계가 하나의 트랜잭션으로 선언되어 있다")
    void rehire_isTransactional() throws NoSuchMethodException {
        assertTrue(UserService.class.getMethod("rehire", Long.class, RehireRequest.class, Long.class)
                .isAnnotationPresent(Transactional.class));
    }

    private User retiredUser() {
        return User.builder()
                .id(TARGET_ID)
                .name("기존 사원")
                .email("old-user@mlsoft.com")
                .role(Role.TEAM_LEADER)
                .position("선임")
                .jobGrade("수석연구원")
                .birthDay(LocalDate.of(1990, 5, 20))
                .onboardingStatus(OnboardingStatus.COMPLETED)
                .hireDate(OLD_HIRE_DATE)
                .lastResetDate(LocalDate.of(2025, 1, 15))
                .baseDays(new BigDecimal("15.0"))
                .bonusDays(new BigDecimal("2.0"))
                .useDays(new BigDecimal("3.0"))
                .advanceDays(BigDecimal.ZERO)
                .monthlyGrantedCount(7)
                .lastBirthdayGrantYear(2025)
                .isActive(false)
                .retiredAt(RETIRED_AT)
                .build();
    }

    private User activeUser(Long id, Role role) {
        return User.builder()
                .id(id)
                .name("활성 사원")
                .email("active-" + id + "@mlsoft.com")
                .role(role)
                .onboardingStatus(OnboardingStatus.COMPLETED)
                .hireDate(LocalDate.of(2025, 1, 1))
                .lastResetDate(LocalDate.of(2025, 1, 1))
                .baseDays(BigDecimal.ZERO)
                .bonusDays(BigDecimal.ZERO)
                .useDays(BigDecimal.ZERO)
                .advanceDays(BigDecimal.ZERO)
                .isActive(true)
                .build();
    }

    private void assertBigDecimal(String expected, BigDecimal actual) {
        assertEquals(0, new BigDecimal(expected).compareTo(actual),
                "기대 " + expected + " 이지만 실제 " + actual);
    }
}
