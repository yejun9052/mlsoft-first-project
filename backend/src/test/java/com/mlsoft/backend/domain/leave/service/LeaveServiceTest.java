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
import com.mlsoft.backend.domain.holiday.service.HolidayService;
import com.mlsoft.backend.domain.policy.entity.PolicyConfigKey;
import com.mlsoft.backend.domain.policy.service.PolicyConfigReader;
import com.mlsoft.backend.domain.user.entity.Role;
import com.mlsoft.backend.domain.user.entity.User;
import com.mlsoft.backend.domain.user.repository.UserRepository;
import com.mlsoft.backend.domain.user.service.ApproverResolver;
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
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

/**
 * 연차 서비스 핵심 로직 단위 테스트 (docs/01 2-3·2-3(b), 검증 R-5·B2).
 * 선차감/복구·당겨쓰기·상태전이·동시성 분기(조건부 갱신 rowcount)를 순수 Mockito로 검증한다.
 */
@ExtendWith(MockitoExtension.class)
class LeaveServiceTest {

    private static final ZoneId KST = ZoneId.of("Asia/Seoul");
    /** 상한을 검증하지 않는 테스트에서 쓰는 넉넉한 당겨쓰기 상한 */
    private static final BigDecimal NO_ADVANCE_LIMIT = new BigDecimal("999.0");

    @Mock
    private LeaveRequestRepository leaveRequestRepository;
    @Mock
    private LeaveActionHistoryRepository leaveActionHistoryRepository;
    @Mock
    private UserRepository userRepository;
    @Mock
    private PolicyConfigReader policyConfigReader;

    // 공휴일 판정 (리뷰 I-6). 대부분의 테스트는 공휴일이 아닌 날짜를 쓰므로 빈 집합이 기본값이다 —
    // givenMaxDatesPerRequest에서 함께 스텁한다(둘 다 validateDates가 부르는 협력자).
    @Mock
    private HolidayService holidayService;

    // 승인자 결정은 ApproverResolver로 모였다 (리뷰 I-5) — 여기서는 결정 결과만 주입하고,
    // 자격 판정 규칙 자체는 ApproverResolverTest가 검증한다.
    @Mock
    private ApproverResolver approverResolver;
    /** 이메일 발행기 — 목이 없으면 @InjectMocks가 null을 넣어 신청·승인마다 NPE가 난다 */
    @Mock
    private com.mlsoft.backend.domain.email.service.EmailNotificationPublisher emailNotificationPublisher;

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
        given(approverResolver.resolvePrimary(applicant)).willReturn(admin);
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
        given(approverResolver.resolvePrimary(applicant)).willReturn(admin);
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
        given(approverResolver.resolvePrimary(applicant)).willReturn(admin);
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
    @DisplayName("신청 — 당겨쓰기 상한 초과: ADVANCE_LIMIT_EXCEEDED, 저장 안 함 (리뷰 I-3)")
    void apply_advanceOverLimit_throws() {
        User applicant = user(1L, Role.EMPLOYEE, "1.0");
        User admin = user(9L, Role.SYSTEM_ADMIN, "15.0");
        given(userRepository.findById(1L)).willReturn(Optional.of(applicant));
        given(approverResolver.resolvePrimary(applicant)).willReturn(admin);
        givenMaxDatesPerRequest(366);
        given(policyConfigReader.getBoolean(PolicyConfigKey.ADVANCE_LEAVE_ENABLED)).willReturn(true);
        given(policyConfigReader.getDecimal(PolicyConfigKey.ADVANCE_MAX_DAYS)).willReturn(new BigDecimal("2.0"));
        given(leaveRequestRepository.findOverlapping(eq(applicant), any(), any())).willReturn(List.of());

        // 잔여 1일에 5일 신청 → 당겨쓰기 4일 필요, 상한 2일 초과
        BusinessException ex = assertThrows(BusinessException.class,
                () -> leaveService.apply(1L, request(futureWeekdays(5), null)));

        assertEquals(ErrorCode.ADVANCE_LIMIT_EXCEEDED, ex.getErrorCode());
        assertEquals(0, BigDecimal.ZERO.compareTo(applicant.getUseDays())); // 선차감도 없어야 한다
        verify(leaveRequestRepository, never()).save(any());
    }

    @Test
    @DisplayName("신청 — 날짜 개수가 설정 상한 초과: TOO_MANY_LEAVE_DATES, 사용자 조회 외 아무것도 안 함 (리뷰 I-3)")
    void apply_tooManyDates_throws() {
        User applicant = user(1L, Role.EMPLOYEE, "15.0");
        given(userRepository.findById(1L)).willReturn(Optional.of(applicant));
        givenMaxDatesPerRequest(3);

        BusinessException ex = assertThrows(BusinessException.class,
                () -> leaveService.apply(1L, request(futureWeekdays(4), null)));

        assertEquals(ErrorCode.TOO_MANY_LEAVE_DATES, ex.getErrorCode());
        verify(leaveRequestRepository, never()).save(any());
    }

    @Test
    @DisplayName("신청 — 날짜 개수 상한은 중복 제거 전 원본 개수로 판정한다")
    void apply_duplicateDatesCountTowardLimit_throws() {
        User applicant = user(1L, Role.EMPLOYEE, "15.0");
        given(userRepository.findById(1L)).willReturn(Optional.of(applicant));
        givenMaxDatesPerRequest(2);

        LocalDate sameDay = futureWeekdays(1).get(0);
        BusinessException ex = assertThrows(BusinessException.class,
                () -> leaveService.apply(1L, request(List.of(sameDay, sameDay, sameDay), null)));

        assertEquals(ErrorCode.TOO_MANY_LEAVE_DATES, ex.getErrorCode());
    }

    @Test
    @DisplayName("신청 — 주말 포함: WEEKEND_NOT_ALLOWED (400)")
    void apply_weekend_throws() {
        User applicant = user(1L, Role.EMPLOYEE, "15.0");
        given(userRepository.findById(1L)).willReturn(Optional.of(applicant));
        givenMaxDatesPerRequest(366);

        List<LocalDate> dates = List.of(nextSaturday());
        BusinessException ex = assertThrows(BusinessException.class,
                () -> leaveService.apply(1L, request(dates, null)));

        assertEquals(ErrorCode.WEEKEND_NOT_ALLOWED, ex.getErrorCode());
    }

    @Test
    @DisplayName("신청 — 공휴일 포함: HOLIDAY_NOT_ALLOWED (400)")
    void apply_holiday_throws() {
        User applicant = user(1L, Role.EMPLOYEE, "15.0");
        given(userRepository.findById(1L)).willReturn(Optional.of(applicant));
        given(policyConfigReader.getInt(PolicyConfigKey.LEAVE_MAX_DATES_PER_REQUEST)).willReturn(366);

        List<LocalDate> dates = futureWeekdays(1);
        // 그날이 공휴일로 적재돼 있는 상황
        given(holidayService.findHolidayDates(any()))
                .willReturn(Set.of(dates.get(0)));

        BusinessException ex = assertThrows(BusinessException.class,
                () -> leaveService.apply(1L, request(dates, null)));

        assertEquals(ErrorCode.HOLIDAY_NOT_ALLOWED, ex.getErrorCode());
        // 검증 단계에서 걸렸으므로 차감이 일어나면 안 된다
        assertEquals(0, applicant.getUseDays().compareTo(BigDecimal.ZERO));
    }

    @Test
    @DisplayName("신청 — 공휴일이 적재돼 있지 않으면 검증이 느슨해질 뿐 신청은 통과한다")
    void apply_holidayNotLoaded_passes() {
        User applicant = user(1L, Role.EMPLOYEE, "15.0");
        given(userRepository.findById(1L)).willReturn(Optional.of(applicant));
        givenAdvanceEnabled(false);
        given(approverResolver.resolvePrimary(applicant)).willReturn(user(9L, Role.SYSTEM_ADMIN, "15.0"));
        given(leaveRequestRepository.findOverlapping(eq(applicant), any(), any())).willReturn(List.of());
        given(leaveRequestRepository.save(any(LeaveRequest.class))).willAnswer(inv -> inv.getArgument(0));

        // 외부 API 장애 등으로 그 해 공휴일이 비어 있는 상황 — 예외가 아니라 통과여야 한다
        leaveService.apply(1L, request(futureWeekdays(1), null));

        assertEquals(0, applicant.getUseDays().compareTo(new BigDecimal("1.0")));
    }

    @Test
    @DisplayName("신청 — 과거 날짜: PAST_DATE_NOT_ALLOWED (400)")
    void apply_pastDate_throws() {
        User applicant = user(1L, Role.EMPLOYEE, "15.0");
        given(userRepository.findById(1L)).willReturn(Optional.of(applicant));
        givenMaxDatesPerRequest(366);

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
        given(approverResolver.resolvePrimary(applicant)).willReturn(admin);
        givenMaxDatesPerRequest(366);
        given(leaveRequestRepository.findOverlapping(eq(applicant), any(), any()))
                .willReturn(List.of(mockPending(applicant, admin)));

        BusinessException ex = assertThrows(BusinessException.class,
                () -> leaveService.apply(1L, request(futureWeekdays(2), null)));

        assertEquals(ErrorCode.OVERLAPPING_LEAVE_REQUEST, ex.getErrorCode());
        verify(leaveRequestRepository, never()).save(any());
    }

    // ---- 하루 정원 1.0일 규칙 (리뷰 I-7) ----

    @Test
    @DisplayName("신청 — 같은 날 오전 반차 뒤 오후 반차: 합계 1.0일이라 통과한다")
    void apply_halfAmThenHalfPm_allowed() {
        // 예전에는 날짜 교집합만 보고 409로 막았다. 정상적인 사용 패턴인데 낼 방법이 없었다.
        User applicant = user(1L, Role.EMPLOYEE, "15.0");
        User admin = user(9L, Role.SYSTEM_ADMIN, "15.0");
        LocalDate day = futureWeekdays(1).get(0);
        given(userRepository.findById(1L)).willReturn(Optional.of(applicant));
        given(approverResolver.resolvePrimary(applicant)).willReturn(admin);
        givenAdvanceEnabled(false);
        given(leaveRequestRepository.findOverlapping(eq(applicant), any(), any()))
                .willReturn(List.of(halfLeave(applicant, admin, LeaveType.HALF_AM, day)));

        LeaveResponse response =
                leaveService.apply(1L, halfRequest(LeaveType.HALF_PM, List.of(day)));

        assertEquals(0, new BigDecimal("0.5").compareTo(response.days()));
        assertEquals(0, new BigDecimal("0.5").compareTo(applicant.getUseDays()));
        verify(leaveRequestRepository).save(any(LeaveRequest.class));
    }

    @Test
    @DisplayName("신청 — 같은 날 같은 반차 종류 두 번: OVERLAPPING_LEAVE_REQUEST")
    void apply_sameHalfTypeTwice_throws() {
        // 합계 규칙만 두면 0.5 + 0.5 = 1.0이 통과한다. 오전 반차를 두 번 쓸 수는 없다.
        User applicant = user(1L, Role.EMPLOYEE, "15.0");
        User admin = user(9L, Role.SYSTEM_ADMIN, "15.0");
        LocalDate day = futureWeekdays(1).get(0);
        given(userRepository.findById(1L)).willReturn(Optional.of(applicant));
        given(approverResolver.resolvePrimary(applicant)).willReturn(admin);
        givenMaxDatesPerRequest(366);
        given(leaveRequestRepository.findOverlapping(eq(applicant), any(), any()))
                .willReturn(List.of(halfLeave(applicant, admin, LeaveType.HALF_AM, day)));

        BusinessException ex = assertThrows(BusinessException.class,
                () -> leaveService.apply(1L, halfRequest(LeaveType.HALF_AM, List.of(day))));

        assertEquals(ErrorCode.OVERLAPPING_LEAVE_REQUEST, ex.getErrorCode());
        assertEquals(0, BigDecimal.ZERO.compareTo(applicant.getUseDays()));
        verify(leaveRequestRepository, never()).save(any());
    }

    @Test
    @DisplayName("신청 — 반차가 있는 날에 종일 연차: 합계 1.5일이라 거부")
    void apply_halfPlusAnnual_throws() {
        User applicant = user(1L, Role.EMPLOYEE, "15.0");
        User admin = user(9L, Role.SYSTEM_ADMIN, "15.0");
        LocalDate day = futureWeekdays(1).get(0);
        given(userRepository.findById(1L)).willReturn(Optional.of(applicant));
        given(approverResolver.resolvePrimary(applicant)).willReturn(admin);
        givenMaxDatesPerRequest(366);
        given(leaveRequestRepository.findOverlapping(eq(applicant), any(), any()))
                .willReturn(List.of(halfLeave(applicant, admin, LeaveType.HALF_PM, day)));

        BusinessException ex = assertThrows(BusinessException.class,
                () -> leaveService.apply(1L, request(List.of(day), null)));

        assertEquals(ErrorCode.OVERLAPPING_LEAVE_REQUEST, ex.getErrorCode());
        verify(leaveRequestRepository, never()).save(any());
    }

    @Test
    @DisplayName("신청 — 조회된 신청의 날짜가 실제로는 겹치지 않으면 통과한다")
    void apply_overlapQueryHitButNoSharedDate_allowed() {
        // findOverlapping은 distinct 신청 단위로 돌아오므로, 어떤 날짜가 겹쳤는지는 자바에서 다시 봐야 한다.
        // 여기서 날짜를 안 맞춰 보면 관계없는 날짜의 잔여 점유가 이 신청의 정원에 더해진다.
        User applicant = user(1L, Role.EMPLOYEE, "15.0");
        User admin = user(9L, Role.SYSTEM_ADMIN, "15.0");
        List<LocalDate> days = futureWeekdays(2);
        given(userRepository.findById(1L)).willReturn(Optional.of(applicant));
        given(approverResolver.resolvePrimary(applicant)).willReturn(admin);
        givenAdvanceEnabled(false);
        given(leaveRequestRepository.findOverlapping(eq(applicant), any(), any()))
                .willReturn(List.of(halfLeave(applicant, admin, LeaveType.HALF_AM, days.get(1))));

        // 신청은 days[0] 하루만 — 조회 결과의 days[1]과는 겹치지 않는다
        leaveService.apply(1L, request(List.of(days.get(0)), null));

        verify(leaveRequestRepository).save(any(LeaveRequest.class));
    }

    @Test
    @DisplayName("신청 — 서브 승인자가 EMPLOYEE: INVALID_APPROVER")
    void apply_subApproverNotEligible_throws() {
        // 자격 규칙 자체는 ApproverResolverTest가 검증한다. 여기서는 신청 흐름이 그 거부를
        // 그대로 전파하고 **차감 전에** 멈추는지만 본다 (예외 전에 상태를 바꾸면 롤백에 의존하게 된다).
        User applicant = user(1L, Role.EMPLOYEE, "15.0");
        given(userRepository.findById(1L)).willReturn(Optional.of(applicant));
        givenMaxDatesPerRequest(366); // 날짜 검증은 승인자 결정보다 먼저 돈다
        given(approverResolver.resolveSub(eq(5L), eq(applicant), any()))
                .willThrow(new BusinessException(ErrorCode.INVALID_APPROVER));

        BusinessException ex = assertThrows(BusinessException.class,
                () -> leaveService.apply(1L, request(futureWeekdays(2), 5L)));

        assertEquals(ErrorCode.INVALID_APPROVER, ex.getErrorCode());
        assertEquals(0, BigDecimal.ZERO.compareTo(applicant.getUseDays()));
        verify(leaveRequestRepository, never()).save(any());
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
    @DisplayName("반려 — 조건부 갱신 성공, use_days 복구 후 당겨쓰기 재계산")
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
        assertEquals(0, BigDecimal.ZERO.compareTo(applicant.getAdvanceDays())); // max(0, 0 - 1) = 0
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

    // 2026-08-17 — 승인자 후보 목록은 본인을 빼지만 그건 목록일 뿐이다. 저장된 승인자가
    // 어떤 경로로 본인이 됐든 결재 시점에 막아야 실제 방어선이 된다. 총관리자도 예외가 아니다.
    @Test
    @DisplayName("승인 — 본인이 신청한 건은 본인이 결재할 수 없다 (총관리자여도)")
    void approve_자기신청은거부() {
        User admin = userWithBalance(9L, "15.0", "2.0", "0.0");
        // 신청자이면서 승인자로도 저장된 상태를 만든다
        LeaveRequest leave = pendingLeave(admin, admin, futureWeekdays(2));
        given(leaveRequestRepository.findById(100L)).willReturn(Optional.of(leave));
        given(userRepository.findById(9L)).willReturn(Optional.of(admin));

        BusinessException ex = assertThrows(BusinessException.class,
                () -> leaveService.processApproval(100L, 9L, new ApprovalRequest(true, "확인")));

        assertEquals(ErrorCode.CANNOT_APPROVE_OWN_REQUEST, ex.getErrorCode());
        assertEquals(RequestStatus.PENDING, leave.getStatus(), "거부됐는데 상태가 바뀌었다");
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
    @DisplayName("취소 — APPROVED + 오늘 날짜: 즉시 취소다 (I-7 정책 확정 2026-08-10)")
    void cancel_approvedToday_immediate() {
        // 경계가 미결이던 항목이다. 오늘 연차를 쓰려다 출근한 경우가 실제로 흔하고,
        // 소급으로 돌리면 결재를 기다리는 사이 그날이 지나 버려 어차피 소급 경로가 된다.
        // 판정은 date.isBefore(today) — 오늘은 "지난 날짜"가 아니다.
        User applicant = userWithBalance(1L, "15.0", "1.0", "0.0");
        User approver = user(9L, Role.SYSTEM_ADMIN, "15.0");
        LeaveRequest leave = approvedLeave(applicant, approver, List.of(LocalDate.now(KST)));
        given(leaveRequestRepository.findById(100L)).willReturn(Optional.of(leave));
        given(leaveRequestRepository.updateStatusToCancelIfCurrent(
                100L, RequestStatus.APPROVED, RequestStatus.CANCELLED, "출근함")).willReturn(1);

        RequestStatus result = leaveService.cancel(100L, 1L, new CancelRequest("출근함"));

        assertEquals(RequestStatus.CANCELLED, result);
        assertEquals(0, BigDecimal.ZERO.compareTo(applicant.getUseDays())); // 즉시 복구
    }

    @Test
    @DisplayName("취소 — APPROVED + 어제 날짜: 여기서부터 소급이다 (오늘과의 경계)")
    void cancel_approvedYesterday_becomesCancelPending() {
        User applicant = userWithBalance(1L, "15.0", "1.0", "0.0");
        User approver = user(9L, Role.SYSTEM_ADMIN, "15.0");
        LeaveRequest leave = approvedLeave(applicant, approver, List.of(LocalDate.now(KST).minusDays(1)));
        given(leaveRequestRepository.findById(100L)).willReturn(Optional.of(leave));
        given(leaveRequestRepository.updateStatusToCancelIfCurrent(
                100L, RequestStatus.APPROVED, RequestStatus.CANCEL_PENDING, "소급")).willReturn(1);

        RequestStatus result = leaveService.cancel(100L, 1L, new CancelRequest("소급"));

        assertEquals(RequestStatus.CANCEL_PENDING, result);
        assertEquals(0, new BigDecimal("1.0").compareTo(applicant.getUseDays())); // 복구 보류
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

    @Test
    @DisplayName("취소 — 신청 2건 중 먼저 신청한 건을 먼저 취소하면 당겨쓰기가 정산된다 (리뷰 I-1)")
    void cancel_firstOfTwoApplied_settlesAdvanceDays() {
        User applicant = user(1L, Role.EMPLOYEE, "10.0");
        User admin = user(9L, Role.SYSTEM_ADMIN, "15.0");
        List<LocalDate> dates = futureWeekdays(13);
        given(userRepository.findById(1L)).willReturn(Optional.of(applicant));
        given(approverResolver.resolvePrimary(applicant)).willReturn(admin);
        givenAdvanceEnabled(true);
        given(leaveRequestRepository.findOverlapping(eq(applicant), any(), any())).willReturn(List.of());

        leaveService.apply(1L, request(dates.subList(0, 8), null));  // A 8일
        leaveService.apply(1L, request(dates.subList(8, 13), null)); // B 5일 — 3일 당겨쓰기

        ArgumentCaptor<LeaveRequest> captor = ArgumentCaptor.forClass(LeaveRequest.class);
        verify(leaveRequestRepository, times(2)).save(captor.capture());
        LeaveRequest leaveA = captor.getAllValues().get(0);
        assertEquals(0, new BigDecimal("13.0").compareTo(applicant.getUseDays()));
        assertEquals(0, new BigDecimal("3.0").compareTo(applicant.getAdvanceDays()));

        given(leaveRequestRepository.findById(100L)).willReturn(Optional.of(leaveA));
        given(leaveRequestRepository.updateStatusToCancelIfCurrent(
                100L, RequestStatus.PENDING, RequestStatus.CANCELLED, "A 취소")).willReturn(1);

        leaveService.cancel(100L, 1L, new CancelRequest("A 취소"));

        // 남은 B 5일은 잔여 10으로 전부 충당된다 — 당겨쓰기가 남으면 다음 기산일에 3일이 증발한다
        assertEquals(0, new BigDecimal("5.0").compareTo(applicant.getUseDays()));
        assertEquals(0, BigDecimal.ZERO.compareTo(applicant.getAdvanceDays()));
    }

    @Test
    @DisplayName("취소 — 나중에 신청한 건을 먼저 취소해도 최종 잔액이 같다 (순서 무관)")
    void cancel_lastOfTwoApplied_reachesSameBalance() {
        User applicant = user(1L, Role.EMPLOYEE, "10.0");
        User admin = user(9L, Role.SYSTEM_ADMIN, "15.0");
        List<LocalDate> dates = futureWeekdays(13);
        given(userRepository.findById(1L)).willReturn(Optional.of(applicant));
        given(approverResolver.resolvePrimary(applicant)).willReturn(admin);
        givenAdvanceEnabled(true);
        given(leaveRequestRepository.findOverlapping(eq(applicant), any(), any())).willReturn(List.of());

        leaveService.apply(1L, request(dates.subList(0, 8), null));
        leaveService.apply(1L, request(dates.subList(8, 13), null));

        ArgumentCaptor<LeaveRequest> captor = ArgumentCaptor.forClass(LeaveRequest.class);
        verify(leaveRequestRepository, times(2)).save(captor.capture());
        LeaveRequest leaveB = captor.getAllValues().get(1);

        given(leaveRequestRepository.findById(200L)).willReturn(Optional.of(leaveB));
        given(leaveRequestRepository.updateStatusToCancelIfCurrent(
                200L, RequestStatus.PENDING, RequestStatus.CANCELLED, "B 취소")).willReturn(1);

        leaveService.cancel(200L, 1L, new CancelRequest("B 취소"));

        assertEquals(0, new BigDecimal("8.0").compareTo(applicant.getUseDays()));
        assertEquals(0, BigDecimal.ZERO.compareTo(applicant.getAdvanceDays()));
    }

    // ============ 기산일 경계 복구 (리뷰 I-10) ============
    // 2/28·3/1·3/2 3일 신청이 3/1 리셋을 넘긴 상황. 리셋은 use_days를 "기산일 이후 날짜"로만
    // 다시 채우므로(docs/09 §5) 그 시점 use_days는 3.0이 아니라 2.0이다.
    // 전체(3.0)를 복구하면 use_days가 −1.0이 되어 연차 1일이 공짜로 생긴다.
    // 반려·즉시취소·소급취소승인 세 경로가 모두 같은 계산을 쓰는지 각각 고정한다.

    @Test
    @DisplayName("I-10 반려 — 기산일 경계 신청은 현재 연도 몫 2일만 복구한다")
    void processApproval_기산일경계_현재연도몫만복구() {
        LocalDate resetDate = LocalDate.of(2026, 3, 1);
        User applicant = userWithResetBalance(1L, "15.0", "2.0", resetDate);
        User approver = user(9L, Role.SYSTEM_ADMIN, "15.0");
        LeaveRequest leave = pendingLeave(applicant, approver, boundaryDates(resetDate));
        given(leaveRequestRepository.findById(100L)).willReturn(Optional.of(leave));
        given(userRepository.findById(9L)).willReturn(Optional.of(approver));
        given(leaveRequestRepository.updateStatusIfCurrent(
                100L, RequestStatus.PENDING, RequestStatus.REJECTED)).willReturn(1);

        leaveService.processApproval(100L, 9L, new ApprovalRequest(false, "반려"));

        assertEquals(0, BigDecimal.ZERO.compareTo(applicant.getUseDays()));
        assertEquals(0, BigDecimal.ZERO.compareTo(applicant.getAdvanceDays()));
    }

    @Test
    @DisplayName("I-10 즉시 취소 — 기산일 경계 신청은 현재 연도 몫 2일만 복구한다")
    void cancel_기산일경계_현재연도몫만복구() {
        // APPROVED 즉시 취소 경로를 타려면 날짜가 전부 미래여야 하므로 오늘 기준으로 잡는다
        LocalDate resetDate = LocalDate.now(KST).plusDays(2);
        User applicant = userWithResetBalance(1L, "15.0", "2.0", resetDate);
        User approver = user(9L, Role.SYSTEM_ADMIN, "15.0");
        LeaveRequest leave = approvedLeave(applicant, approver, boundaryDates(resetDate));
        given(leaveRequestRepository.findById(100L)).willReturn(Optional.of(leave));
        given(leaveRequestRepository.updateStatusToCancelIfCurrent(
                100L, RequestStatus.APPROVED, RequestStatus.CANCELLED, "취소")).willReturn(1);

        leaveService.cancel(100L, 1L, new CancelRequest("취소"));

        assertEquals(0, BigDecimal.ZERO.compareTo(applicant.getUseDays()));
        assertEquals(0, BigDecimal.ZERO.compareTo(applicant.getAdvanceDays()));
    }

    @Test
    @DisplayName("I-10 소급취소 승인 — 기산일 경계 신청은 현재 연도 몫 2일만 복구한다")
    void processCancelApproval_기산일경계_현재연도몫만복구() {
        LocalDate resetDate = LocalDate.of(2026, 3, 1);
        User applicant = userWithResetBalance(1L, "15.0", "2.0", resetDate);
        User approver = user(9L, Role.SYSTEM_ADMIN, "15.0");
        LeaveRequest leave = approvedLeave(applicant, approver, boundaryDates(resetDate));
        leave.requestCancel("소급 취소");
        given(leaveRequestRepository.findById(100L)).willReturn(Optional.of(leave));
        given(userRepository.findById(9L)).willReturn(Optional.of(approver));
        given(leaveRequestRepository.updateStatusIfCurrent(
                100L, RequestStatus.CANCEL_PENDING, RequestStatus.CANCELLED)).willReturn(1);

        leaveService.processCancelApproval(100L, 9L, new ApprovalRequest(true, "승인"));

        assertEquals(0, BigDecimal.ZERO.compareTo(applicant.getUseDays()));
        assertEquals(0, BigDecimal.ZERO.compareTo(applicant.getAdvanceDays()));
    }

    @Test
    @DisplayName("I-10 — 신청 전체가 기산일 이전이면 복구량이 0이다")
    void processApproval_전체가기산일이전_복구없음() {
        // 그 몫은 이전 연도에서 이미 정리됐다. 되돌리면 없던 연차가 생긴다
        LocalDate resetDate = LocalDate.of(2026, 3, 1);
        User applicant = userWithResetBalance(1L, "15.0", "0.0", resetDate);
        User approver = user(9L, Role.SYSTEM_ADMIN, "15.0");
        LeaveRequest leave = pendingLeave(applicant, approver,
                List.of(LocalDate.of(2026, 2, 25), LocalDate.of(2026, 2, 26)));
        given(leaveRequestRepository.findById(100L)).willReturn(Optional.of(leave));
        given(userRepository.findById(9L)).willReturn(Optional.of(approver));
        given(leaveRequestRepository.updateStatusIfCurrent(
                100L, RequestStatus.PENDING, RequestStatus.REJECTED)).willReturn(1);

        leaveService.processApproval(100L, 9L, new ApprovalRequest(false, "반려"));

        assertEquals(0, BigDecimal.ZERO.compareTo(applicant.getUseDays()));
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

    /** 반차 신청 — 하루 정원 규칙 검증용 (리뷰 I-7) */
    private LeaveCreateRequest halfRequest(LeaveType type, List<LocalDate> dates) {
        return new LeaveCreateRequest(type, dates, "반차", null);
    }

    /** 이미 하루를 일부 점유하고 있는 기존 반차 신청 */
    private LeaveRequest halfLeave(User applicant, User approver, LeaveType type, LocalDate date) {
        return LeaveRequest.create(applicant, type, List.of(date), "기존 반차", approver, null);
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

    /** 기산일 리셋을 한 번 겪은 사원 — use_days는 이미 "기산일 이후 날짜"만 담고 있다 (리뷰 I-10) */
    private User userWithResetBalance(Long id, String baseDays, String useDays, LocalDate lastResetDate) {
        BigDecimal base = new BigDecimal(baseDays);
        BigDecimal use = new BigDecimal(useDays);
        return User.builder()
                .id(id)
                .name("user" + id)
                .email("user" + id + "@mlsoft.com")
                .role(Role.EMPLOYEE)
                .baseDays(base)
                .useDays(use)
                .bonusDays(BigDecimal.ZERO)
                .advanceDays(use.subtract(base).max(BigDecimal.ZERO))
                .lastResetDate(lastResetDate)
                .isActive(true)
                .build();
    }

    /** 기산일을 걸친 3일 — 하루 전·기산일 당일·하루 뒤 (기산일 이후는 2일) */
    private List<LocalDate> boundaryDates(LocalDate resetDate) {
        return List.of(resetDate.minusDays(1), resetDate, resetDate.plusDays(1));
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

    /** 당겨쓰기 허용 여부 + 상한 무제한. 날짜 개수 상한도 함께 열어둔다 */
    private void givenAdvanceEnabled(boolean enabled) {
        given(policyConfigReader.getBoolean(PolicyConfigKey.ADVANCE_LEAVE_ENABLED)).willReturn(enabled);
        given(policyConfigReader.getDecimal(PolicyConfigKey.ADVANCE_MAX_DAYS)).willReturn(NO_ADVANCE_LIMIT);
        givenMaxDatesPerRequest(366);
    }

    private void givenMaxDatesPerRequest(int max) {
        given(policyConfigReader.getInt(PolicyConfigKey.LEAVE_MAX_DATES_PER_REQUEST)).willReturn(max);
        // 공휴일 없음이 기본. lenient인 이유 — 개수 상한·주말·과거에서 먼저 걸리는 테스트는
        // 여기까지 오지 않아 스텁이 쓰이지 않는다 (validateDates가 값싼 검사를 먼저 한다).
        lenient().when(holidayService.findHolidayDates(any())).thenReturn(Set.of());
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
