package com.mlsoft.backend.domain.leave.service;

import com.mlsoft.backend.domain.common.RequestAction;
import com.mlsoft.backend.domain.common.RequestStatus;
import com.mlsoft.backend.domain.leave.dto.ApprovalRequest;
import com.mlsoft.backend.domain.leave.dto.CancelRequest;
import com.mlsoft.backend.domain.leave.dto.LeaveCreateRequest;
import com.mlsoft.backend.domain.leave.dto.LeaveResponse;
import com.mlsoft.backend.domain.leave.dto.LeaveSummaryResponse;
import com.mlsoft.backend.domain.leave.entity.LeaveActionHistory;
import com.mlsoft.backend.domain.leave.entity.LeaveRequest;
import com.mlsoft.backend.domain.leave.entity.LeaveType;
import com.mlsoft.backend.domain.leave.repository.LeaveActionHistoryRepository;
import com.mlsoft.backend.domain.leave.repository.LeaveRequestRepository;
import com.mlsoft.backend.domain.policy.entity.LeavePolicyConfig;
import com.mlsoft.backend.domain.policy.repository.LeavePolicyConfigRepository;
import com.mlsoft.backend.domain.user.entity.Role;
import com.mlsoft.backend.domain.user.entity.User;
import com.mlsoft.backend.domain.user.repository.UserRepository;
import com.mlsoft.backend.global.exception.BusinessException;
import com.mlsoft.backend.global.exception.ErrorCode;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

/**
 * 연차 서비스 핵심 로직 단위 테스트 (docs/01 2-3·2-3(b), 검증 R-5·B2).
 * 선차감/복구·당겨쓰기·상태전이·동시성 분기(조건부 갱신 rowcount)를 순수 Mockito로 검증한다.
 */
@ExtendWith(MockitoExtension.class)
class LeaveServiceTest {

    private static final ZoneId KST = ZoneId.of("Asia/Seoul");
    private static final String CONFIG_ADVANCE = "advance_leave_enabled";

    @Mock
    private LeaveRequestRepository leaveRequestRepository;
    @Mock
    private LeaveActionHistoryRepository leaveActionHistoryRepository;
    @Mock
    private UserRepository userRepository;
    @Mock
    private LeavePolicyConfigRepository leavePolicyConfigRepository;

    @InjectMocks
    private LeaveService leaveService;

    // ============================ 신청 ============================

    @Test
    @DisplayName("신청 — 잔여 충분: 선차감(use_days 증가)하고 PENDING 이력 기록")
    void apply_success_deductsBalance() {
        User applicant = user(1L, Role.EMPLOYEE, "15.0");
        User admin = user(9L, Role.SYSTEM_ADMIN, "15.0");
        List<LocalDate> dates = futureWeekdays(2);
        given(userRepository.findById(1L)).willReturn(Optional.of(applicant));
        given(userRepository.findFirstByRoleAndIsActiveTrueOrderByIdAsc(Role.SYSTEM_ADMIN))
                .willReturn(Optional.of(admin));
        givenAdvanceEnabled(false);
        given(leaveRequestRepository.findOverlapping(eq(applicant), any(), any())).willReturn(List.of());

        LeaveResponse response = leaveService.apply(1L, request(dates, null));

        assertEquals(0, new BigDecimal("2.0").compareTo(response.days()));
        assertEquals(0, new BigDecimal("2.0").compareTo(applicant.getUseDays()));
        assertEquals(0, BigDecimal.ZERO.compareTo(applicant.getAdvanceDays()));
        assertEquals(9L, response.primaryApproverId()); // 부서 미배정 → SYSTEM_ADMIN fallback
        verify(leaveRequestRepository).save(any(LeaveRequest.class));
        verify(leaveActionHistoryRepository).save(any(LeaveActionHistory.class));
    }

    @Test
    @DisplayName("신청 — 잔여 부족 + 당겨쓰기 off: INSUFFICIENT_LEAVE_BALANCE, 저장 안 함")
    void apply_insufficientWithoutAdvance_throws() {
        User applicant = user(1L, Role.EMPLOYEE, "1.0");
        User admin = user(9L, Role.SYSTEM_ADMIN, "15.0");
        given(userRepository.findById(1L)).willReturn(Optional.of(applicant));
        given(userRepository.findFirstByRoleAndIsActiveTrueOrderByIdAsc(Role.SYSTEM_ADMIN))
                .willReturn(Optional.of(admin));
        givenAdvanceEnabled(false);
        given(leaveRequestRepository.findOverlapping(eq(applicant), any(), any())).willReturn(List.of());

        BusinessException ex = assertThrows(BusinessException.class,
                () -> leaveService.apply(1L, request(futureWeekdays(2), null)));

        assertEquals(ErrorCode.INSUFFICIENT_LEAVE_BALANCE, ex.getErrorCode());
        verify(leaveRequestRepository, never()).save(any());
    }

    @Test
    @DisplayName("신청 — 잔여 부족 + 당겨쓰기 on: 부족분 advance_days 누적 + 스냅샷 기록")
    void apply_insufficientWithAdvance_accumulates() {
        User applicant = user(1L, Role.EMPLOYEE, "1.0");
        User admin = user(9L, Role.SYSTEM_ADMIN, "15.0");
        given(userRepository.findById(1L)).willReturn(Optional.of(applicant));
        given(userRepository.findFirstByRoleAndIsActiveTrueOrderByIdAsc(Role.SYSTEM_ADMIN))
                .willReturn(Optional.of(admin));
        givenAdvanceEnabled(true);
        given(leaveRequestRepository.findOverlapping(eq(applicant), any(), any())).willReturn(List.of());

        leaveService.apply(1L, request(futureWeekdays(2), null));

        // 잔여 1 - 신청 2 = 부족 1일이 당겨쓰기로 충당
        assertEquals(0, new BigDecimal("1.0").compareTo(applicant.getAdvanceDays()));
        assertEquals(0, new BigDecimal("2.0").compareTo(applicant.getUseDays()));
        ArgumentCaptor<LeaveRequest> captor = ArgumentCaptor.forClass(LeaveRequest.class);
        verify(leaveRequestRepository).save(captor.capture());
        assertEquals(0, new BigDecimal("1.0").compareTo(captor.getValue().getAdvanceUsedDays()));
    }

    @Test
    @DisplayName("신청 — 주말 포함: WEEKEND_NOT_ALLOWED (400)")
    void apply_weekend_throws() {
        User applicant = user(1L, Role.EMPLOYEE, "15.0");
        given(userRepository.findById(1L)).willReturn(Optional.of(applicant));

        List<LocalDate> dates = List.of(nextSaturday());
        BusinessException ex = assertThrows(BusinessException.class,
                () -> leaveService.apply(1L, request(dates, null)));

        assertEquals(ErrorCode.WEEKEND_NOT_ALLOWED, ex.getErrorCode());
    }

    @Test
    @DisplayName("신청 — 과거 날짜: PAST_DATE_NOT_ALLOWED (400)")
    void apply_pastDate_throws() {
        User applicant = user(1L, Role.EMPLOYEE, "15.0");
        given(userRepository.findById(1L)).willReturn(Optional.of(applicant));

        List<LocalDate> dates = List.of(pastWeekday());
        BusinessException ex = assertThrows(BusinessException.class,
                () -> leaveService.apply(1L, request(dates, null)));

        assertEquals(ErrorCode.PAST_DATE_NOT_ALLOWED, ex.getErrorCode());
    }

    @Test
    @DisplayName("신청 — 기간 중복: OVERLAPPING_LEAVE_REQUEST (409)")
    void apply_overlap_throws() {
        User applicant = user(1L, Role.EMPLOYEE, "15.0");
        User admin = user(9L, Role.SYSTEM_ADMIN, "15.0");
        given(userRepository.findById(1L)).willReturn(Optional.of(applicant));
        given(userRepository.findFirstByRoleAndIsActiveTrueOrderByIdAsc(Role.SYSTEM_ADMIN))
                .willReturn(Optional.of(admin));
        given(leaveRequestRepository.findOverlapping(eq(applicant), any(), any()))
                .willReturn(List.of(mockPending(applicant, admin)));

        BusinessException ex = assertThrows(BusinessException.class,
                () -> leaveService.apply(1L, request(futureWeekdays(2), null)));

        assertEquals(ErrorCode.OVERLAPPING_LEAVE_REQUEST, ex.getErrorCode());
        verify(leaveRequestRepository, never()).save(any());
    }

    @Test
    @DisplayName("신청 — 서브 승인자가 EMPLOYEE: INVALID_APPROVER")
    void apply_subApproverNotEligible_throws() {
        User applicant = user(1L, Role.EMPLOYEE, "15.0");
        User admin = user(9L, Role.SYSTEM_ADMIN, "15.0");
        User employeeSub = user(5L, Role.EMPLOYEE, "15.0");
        given(userRepository.findById(1L)).willReturn(Optional.of(applicant));
        given(userRepository.findFirstByRoleAndIsActiveTrueOrderByIdAsc(Role.SYSTEM_ADMIN))
                .willReturn(Optional.of(admin));
        given(userRepository.findById(5L)).willReturn(Optional.of(employeeSub));

        BusinessException ex = assertThrows(BusinessException.class,
                () -> leaveService.apply(1L, request(futureWeekdays(2), 5L)));

        assertEquals(ErrorCode.INVALID_APPROVER, ex.getErrorCode());
    }

    // ============================ 승인 / 반려 ============================

    @Test
    @DisplayName("승인 — 조건부 갱신 성공, 잔여 복구 없음")
    void approve_success() {
        User applicant = userWithBalance(1L, "15.0", "2.0", "0.0");
        User approver = user(9L, Role.SYSTEM_ADMIN, "15.0");
        LeaveRequest leave = pendingLeave(applicant, approver, futureWeekdays(2));
        given(leaveRequestRepository.findById(100L)).willReturn(Optional.of(leave));
        given(userRepository.findById(9L)).willReturn(Optional.of(approver));
        given(leaveRequestRepository.updateStatusIfCurrent(100L, RequestStatus.PENDING, RequestStatus.APPROVED))
                .willReturn(1);

        leaveService.processApproval(100L, 9L, new ApprovalRequest(true, "확인"));

        assertEquals(0, new BigDecimal("2.0").compareTo(applicant.getUseDays())); // 복구 없음
        verify(leaveActionHistoryRepository).save(historyWith(RequestAction.APPROVED));
    }

    @Test
    @DisplayName("반려 — 조건부 갱신 성공, use_days + advance_days 복구")
    void reject_restoresBalance() {
        User applicant = userWithBalance(1L, "1.0", "2.0", "1.0"); // 1일 당겨쓴 상태
        User approver = user(9L, Role.SYSTEM_ADMIN, "15.0");
        LeaveRequest leave = pendingLeave(applicant, approver, futureWeekdays(2));
        leave.recordAdvanceUsage(new BigDecimal("1.0"));
        given(leaveRequestRepository.findById(100L)).willReturn(Optional.of(leave));
        given(userRepository.findById(9L)).willReturn(Optional.of(approver));
        given(leaveRequestRepository.updateStatusIfCurrent(100L, RequestStatus.PENDING, RequestStatus.REJECTED))
                .willReturn(1);

        leaveService.processApproval(100L, 9L, new ApprovalRequest(false, "반려"));

        assertEquals(0, BigDecimal.ZERO.compareTo(applicant.getUseDays()));    // 2 - 2
        assertEquals(0, BigDecimal.ZERO.compareTo(applicant.getAdvanceDays())); // 1 - 1
        verify(leaveActionHistoryRepository).save(historyWith(RequestAction.REJECTED));
    }

    @Test
    @DisplayName("승인 — 조건부 갱신 rowcount 0(동시 처리 패배): ALREADY_PROCESSED")
    void approve_alreadyProcessed_whenRowcountZero() {
        User applicant = userWithBalance(1L, "15.0", "2.0", "0.0");
        User approver = user(9L, Role.SYSTEM_ADMIN, "15.0");
        LeaveRequest leave = pendingLeave(applicant, approver, futureWeekdays(2));
        given(leaveRequestRepository.findById(100L)).willReturn(Optional.of(leave));
        given(userRepository.findById(9L)).willReturn(Optional.of(approver));
        given(leaveRequestRepository.updateStatusIfCurrent(100L, RequestStatus.PENDING, RequestStatus.APPROVED))
                .willReturn(0);

        BusinessException ex = assertThrows(BusinessException.class,
                () -> leaveService.processApproval(100L, 9L, new ApprovalRequest(true, "확인")));

        assertEquals(ErrorCode.ALREADY_PROCESSED, ex.getErrorCode());
        verify(leaveActionHistoryRepository, never()).save(any());
    }

    @Test
    @DisplayName("승인 — 승인자가 아닌 사용자: ACCESS_DENIED")
    void approve_notApprover_accessDenied() {
        User applicant = userWithBalance(1L, "15.0", "2.0", "0.0");
        User approver = user(9L, Role.SYSTEM_ADMIN, "15.0");
        User stranger = user(7L, Role.TEAM_LEADER, "15.0");
        LeaveRequest leave = pendingLeave(applicant, approver, futureWeekdays(2));
        given(leaveRequestRepository.findById(100L)).willReturn(Optional.of(leave));
        given(userRepository.findById(7L)).willReturn(Optional.of(stranger));

        BusinessException ex = assertThrows(BusinessException.class,
                () -> leaveService.processApproval(100L, 7L, new ApprovalRequest(true, "확인")));

        assertEquals(ErrorCode.ACCESS_DENIED, ex.getErrorCode());
        verify(leaveRequestRepository, never())
                .updateStatusIfCurrent(any(), any(), any());
    }

    // ============================ 취소 ============================

    @Test
    @DisplayName("취소 — PENDING: 즉시 CANCELLED + 복구")
    void cancel_pending_immediateRestore() {
        User applicant = userWithBalance(1L, "15.0", "2.0", "0.0");
        User approver = user(9L, Role.SYSTEM_ADMIN, "15.0");
        LeaveRequest leave = pendingLeave(applicant, approver, futureWeekdays(2));
        given(leaveRequestRepository.findById(100L)).willReturn(Optional.of(leave));
        given(leaveRequestRepository.updateStatusToCancelIfCurrent(
                100L, RequestStatus.PENDING, RequestStatus.CANCELLED, "개인 사정")).willReturn(1);

        RequestStatus result = leaveService.cancel(100L, 1L, new CancelRequest("개인 사정"));

        assertEquals(RequestStatus.CANCELLED, result);
        assertEquals(0, BigDecimal.ZERO.compareTo(applicant.getUseDays()));
        verify(leaveActionHistoryRepository).save(historyWith(RequestAction.CANCELLED));
    }

    @Test
    @DisplayName("취소 — APPROVED + 미래 날짜만: 즉시 CANCELLED + 복구")
    void cancel_approvedFuture_immediate() {
        User applicant = userWithBalance(1L, "15.0", "2.0", "0.0");
        User approver = user(9L, Role.SYSTEM_ADMIN, "15.0");
        LeaveRequest leave = approvedLeave(applicant, approver, futureWeekdays(2));
        given(leaveRequestRepository.findById(100L)).willReturn(Optional.of(leave));
        given(leaveRequestRepository.updateStatusToCancelIfCurrent(
                100L, RequestStatus.APPROVED, RequestStatus.CANCELLED, "개인 사정")).willReturn(1);

        RequestStatus result = leaveService.cancel(100L, 1L, new CancelRequest("개인 사정"));

        assertEquals(RequestStatus.CANCELLED, result);
        assertEquals(0, BigDecimal.ZERO.compareTo(applicant.getUseDays()));
    }

    @Test
    @DisplayName("취소 — APPROVED + 과거 날짜 포함: CANCEL_PENDING(복구 보류)")
    void cancel_approvedPast_becomesCancelPending() {
        User applicant = userWithBalance(1L, "15.0", "2.0", "0.0");
        User approver = user(9L, Role.SYSTEM_ADMIN, "15.0");
        List<LocalDate> withPast = new ArrayList<>(futureWeekdays(1));
        withPast.add(pastWeekday());
        LeaveRequest leave = approvedLeave(applicant, approver, withPast);
        given(leaveRequestRepository.findById(100L)).willReturn(Optional.of(leave));
        given(leaveRequestRepository.updateStatusToCancelIfCurrent(
                100L, RequestStatus.APPROVED, RequestStatus.CANCEL_PENDING, "소급")).willReturn(1);

        RequestStatus result = leaveService.cancel(100L, 1L, new CancelRequest("소급"));

        assertEquals(RequestStatus.CANCEL_PENDING, result);
        assertEquals(0, new BigDecimal("2.0").compareTo(applicant.getUseDays())); // 복구 보류
        verify(leaveActionHistoryRepository).save(historyWith(RequestAction.CANCEL_PENDING));
    }

    @Test
    @DisplayName("취소 — 본인이 아니면 ACCESS_DENIED")
    void cancel_notOwner_accessDenied() {
        User applicant = userWithBalance(1L, "15.0", "2.0", "0.0");
        User approver = user(9L, Role.SYSTEM_ADMIN, "15.0");
        LeaveRequest leave = pendingLeave(applicant, approver, futureWeekdays(2));
        given(leaveRequestRepository.findById(100L)).willReturn(Optional.of(leave));

        BusinessException ex = assertThrows(BusinessException.class,
                () -> leaveService.cancel(100L, 2L, new CancelRequest("남의 것")));

        assertEquals(ErrorCode.ACCESS_DENIED, ex.getErrorCode());
    }

    // ==================== 소급취소 승인/반려 ====================

    @Test
    @DisplayName("소급취소 승인 — CANCELLED 확정 + 복구")
    void cancelApproval_approve_restores() {
        User applicant = userWithBalance(1L, "15.0", "2.0", "0.0");
        User approver = user(9L, Role.SYSTEM_ADMIN, "15.0");
        LeaveRequest leave = cancelPendingLeave(applicant, approver);
        given(leaveRequestRepository.findById(100L)).willReturn(Optional.of(leave));
        given(userRepository.findById(9L)).willReturn(Optional.of(approver));
        given(leaveRequestRepository.updateStatusIfCurrent(
                100L, RequestStatus.CANCEL_PENDING, RequestStatus.CANCELLED)).willReturn(1);

        leaveService.processCancelApproval(100L, 9L, new ApprovalRequest(true, "승인"));

        assertEquals(0, BigDecimal.ZERO.compareTo(applicant.getUseDays()));
        verify(leaveActionHistoryRepository).save(historyWith(RequestAction.CANCEL_APPROVED));
    }

    @Test
    @DisplayName("소급취소 반려 — APPROVED 복원, 복구 없음")
    void cancelApproval_reject_restoresApproved() {
        User applicant = userWithBalance(1L, "15.0", "2.0", "0.0");
        User approver = user(9L, Role.SYSTEM_ADMIN, "15.0");
        LeaveRequest leave = cancelPendingLeave(applicant, approver);
        given(leaveRequestRepository.findById(100L)).willReturn(Optional.of(leave));
        given(userRepository.findById(9L)).willReturn(Optional.of(approver));
        given(leaveRequestRepository.updateStatusIfCurrent(
                100L, RequestStatus.CANCEL_PENDING, RequestStatus.APPROVED)).willReturn(1);

        leaveService.processCancelApproval(100L, 9L, new ApprovalRequest(false, "거부"));

        assertEquals(0, new BigDecimal("2.0").compareTo(applicant.getUseDays())); // 복구 없음
        verify(leaveActionHistoryRepository).save(historyWith(RequestAction.CANCEL_REJECTED));
    }

    // ============================ 요약 ============================

    @Test
    @DisplayName("요약 — 대기 합계와 다음 기산일(마지막 기산일 + 1년) 반영")
    void summary_returnsPendingAndNextReset() {
        User user = userWithBalance(1L, "15.0", "3.0", "0.0");
        LocalDate resetDate = LocalDate.of(2026, 3, 1);
        user.resetAnnualLeave(new BigDecimal("15.0"), resetDate); // lastResetDate 설정
        given(userRepository.findById(1L)).willReturn(Optional.of(user));
        given(leaveRequestRepository.sumDaysByUserAndStatus(user, RequestStatus.PENDING))
                .willReturn(new BigDecimal("2.0"));

        LeaveSummaryResponse response = leaveService.getMySummary(1L);

        assertEquals(0, new BigDecimal("2.0").compareTo(response.pendingDays()));
        assertEquals(resetDate.plusYears(1), response.nextResetDate());
    }

    // ============================ 헬퍼 ============================

    private LeaveCreateRequest request(List<LocalDate> dates, Long subApproverId) {
        return new LeaveCreateRequest(LeaveType.ANNUAL, dates, "휴식", subApproverId);
    }

    private User user(Long id, Role role, String baseDays) {
        return User.builder()
                .id(id)
                .name("user" + id)
                .email("user" + id + "@mlsoft.com")
                .role(role)
                .baseDays(new BigDecimal(baseDays))
                .useDays(BigDecimal.ZERO)
                .bonusDays(BigDecimal.ZERO)
                .advanceDays(BigDecimal.ZERO)
                .isActive(true)
                .build();
    }

    private User userWithBalance(Long id, String baseDays, String useDays, String advanceDays) {
        return User.builder()
                .id(id)
                .name("user" + id)
                .email("user" + id + "@mlsoft.com")
                .role(Role.EMPLOYEE)
                .baseDays(new BigDecimal(baseDays))
                .useDays(new BigDecimal(useDays))
                .bonusDays(BigDecimal.ZERO)
                .advanceDays(new BigDecimal(advanceDays))
                .isActive(true)
                .build();
    }

    private LeaveRequest pendingLeave(User applicant, User approver, List<LocalDate> dates) {
        return LeaveRequest.create(applicant, LeaveType.ANNUAL, dates, "휴식", approver, null);
    }

    private LeaveRequest approvedLeave(User applicant, User approver, List<LocalDate> dates) {
        LeaveRequest leave = pendingLeave(applicant, approver, dates);
        leave.approve();
        return leave;
    }

    private LeaveRequest cancelPendingLeave(User applicant, User approver) {
        LeaveRequest leave = approvedLeave(applicant, approver, futureWeekdays(2));
        leave.requestCancel("소급 취소 요청");
        return leave;
    }

    private LeaveRequest mockPending(User applicant, User approver) {
        return pendingLeave(applicant, approver, futureWeekdays(2));
    }

    private void givenAdvanceEnabled(boolean enabled) {
        given(leavePolicyConfigRepository.findByName(CONFIG_ADVANCE))
                .willReturn(Optional.of(LeavePolicyConfig.create(CONFIG_ADVANCE, String.valueOf(enabled))));
    }

    private LeaveActionHistory historyWith(RequestAction action) {
        return org.mockito.ArgumentMatchers.argThat(h -> h != null && h.getAction() == action);
    }

    // 미래의 평일 날짜 count개 (주말·과거 검증 통과용)
    private List<LocalDate> futureWeekdays(int count) {
        List<LocalDate> result = new ArrayList<>();
        LocalDate date = LocalDate.now(KST).plusDays(1);
        while (result.size() < count) {
            if (isWeekday(date)) {
                result.add(date);
            }
            date = date.plusDays(1);
        }
        return result;
    }

    private LocalDate nextSaturday() {
        LocalDate date = LocalDate.now(KST).plusDays(1);
        while (date.getDayOfWeek() != DayOfWeek.SATURDAY) {
            date = date.plusDays(1);
        }
        return date;
    }

    private LocalDate pastWeekday() {
        LocalDate date = LocalDate.now(KST).minusDays(1);
        while (!isWeekday(date)) {
            date = date.minusDays(1);
        }
        return date;
    }

    private boolean isWeekday(LocalDate date) {
        DayOfWeek dayOfWeek = date.getDayOfWeek();
        return dayOfWeek != DayOfWeek.SATURDAY && dayOfWeek != DayOfWeek.SUNDAY;
    }
}
