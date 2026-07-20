package com.mlsoft.backend.domain.welfare.service;

import com.mlsoft.backend.domain.common.RequestAction;
import com.mlsoft.backend.domain.common.RequestStatus;
import com.mlsoft.backend.domain.user.entity.Role;
import com.mlsoft.backend.domain.user.entity.User;
import com.mlsoft.backend.domain.user.repository.UserRepository;
import com.mlsoft.backend.domain.welfare.dto.WelfareApprovalRequest;
import com.mlsoft.backend.domain.welfare.dto.WelfareCreateRequest;
import com.mlsoft.backend.domain.welfare.dto.WelfareResponse;
import com.mlsoft.backend.domain.welfare.entity.WelfareActionHistory;
import com.mlsoft.backend.domain.welfare.entity.WelfarePolicy;
import com.mlsoft.backend.domain.welfare.entity.WelfareRequest;
import com.mlsoft.backend.domain.welfare.entity.WelfareTarget;
import com.mlsoft.backend.domain.welfare.repository.WelfareActionHistoryRepository;
import com.mlsoft.backend.domain.welfare.repository.WelfarePolicyRepository;
import com.mlsoft.backend.domain.welfare.repository.WelfareRequestRepository;
import com.mlsoft.backend.global.exception.BusinessException;
import com.mlsoft.backend.global.exception.ErrorCode;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentMatchers;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

/**
 * 복리후생 신청 서비스 핵심 로직 단위 테스트 (docs/01, docs/03 복리후생, 검증 R-5).
 * 정책 스냅샷(add_days)·승인 시 bonus_days 가산·PENDING 전용 취소·동시성 분기(조건부 갱신 rowcount)를
 * 순수 Mockito로 검증한다. LeaveServiceTest와 동일한 스타일을 따른다.
 */
@ExtendWith(MockitoExtension.class)
class WelfareServiceTest {

    @Mock
    private WelfareRequestRepository welfareRequestRepository;
    @Mock
    private WelfareActionHistoryRepository welfareActionHistoryRepository;
    @Mock
    private WelfarePolicyRepository welfarePolicyRepository;
    @Mock
    private UserRepository userRepository;

    @InjectMocks
    private WelfareService welfareService;

    // ============================ 신청 ============================

    @Test
    @DisplayName("신청 — 정책값(구분·대상·제출자료·부여일수)을 스냅샷으로 복사하고 PENDING 이력 기록")
    void apply_success_snapshotsPolicy() {
        User applicant = user(1L, Role.EMPLOYEE);
        User admin = user(9L, Role.SYSTEM_ADMIN);
        WelfarePolicy policy = policy(100L, "결혼", WelfareTarget.SELF, "7.0");
        given(userRepository.findById(1L)).willReturn(Optional.of(applicant));
        given(welfarePolicyRepository.findByIdAndActiveTrue(100L)).willReturn(Optional.of(policy));
        given(userRepository.findFirstByRoleAndIsActiveTrueOrderByIdAsc(Role.SYSTEM_ADMIN))
                .willReturn(Optional.of(admin));

        WelfareResponse response = welfareService.apply(1L, new WelfareCreateRequest(100L, "결혼합니다", null));

        assertEquals("결혼", response.category());
        assertEquals(WelfareTarget.SELF.name(), response.target());
        assertEquals(0, new BigDecimal("7.0").compareTo(response.addDays()));
        assertEquals("결혼합니다", response.reason());
        assertEquals(RequestStatus.PENDING.name(), response.status());
        assertEquals(9L, response.primaryApproverId()); // 부서 미배정 → SYSTEM_ADMIN fallback
        verify(welfareRequestRepository).save(any(WelfareRequest.class));
        verify(welfareActionHistoryRepository).save(historyWith(RequestAction.PENDING));
    }

    @Test
    @DisplayName("신청 — 존재하지 않거나 비활성화된 정책: WELFARE_POLICY_NOT_FOUND")
    void apply_inactivePolicy_throws() {
        User applicant = user(1L, Role.EMPLOYEE);
        given(userRepository.findById(1L)).willReturn(Optional.of(applicant));
        given(welfarePolicyRepository.findByIdAndActiveTrue(100L)).willReturn(Optional.empty());

        BusinessException ex = assertThrows(BusinessException.class,
                () -> welfareService.apply(1L, new WelfareCreateRequest(100L, "사유", null)));

        assertEquals(ErrorCode.WELFARE_POLICY_NOT_FOUND, ex.getErrorCode());
        verify(welfareRequestRepository, never()).save(any());
    }

    @Test
    @DisplayName("신청 — 서브 승인자가 EMPLOYEE: INVALID_APPROVER")
    void apply_subApproverNotEligible_throws() {
        User applicant = user(1L, Role.EMPLOYEE);
        User admin = user(9L, Role.SYSTEM_ADMIN);
        User employeeSub = user(5L, Role.EMPLOYEE);
        WelfarePolicy policy = policy(100L, "결혼", WelfareTarget.SELF, "7.0");
        given(userRepository.findById(1L)).willReturn(Optional.of(applicant));
        given(welfarePolicyRepository.findByIdAndActiveTrue(100L)).willReturn(Optional.of(policy));
        given(userRepository.findFirstByRoleAndIsActiveTrueOrderByIdAsc(Role.SYSTEM_ADMIN))
                .willReturn(Optional.of(admin));
        given(userRepository.findById(5L)).willReturn(Optional.of(employeeSub));

        BusinessException ex = assertThrows(BusinessException.class,
                () -> welfareService.apply(1L, new WelfareCreateRequest(100L, "사유", 5L)));

        assertEquals(ErrorCode.INVALID_APPROVER, ex.getErrorCode());
        verify(welfareRequestRepository, never()).save(any());
    }

    // ============================ 승인 / 반려 ============================

    @Test
    @DisplayName("승인 — 조건부 갱신 성공, 신청자에게 addDays만큼 bonus_days 가산")
    void approve_success_addsBonusDays() {
        User applicant = user(1L, Role.EMPLOYEE);
        User approver = user(9L, Role.SYSTEM_ADMIN);
        WelfarePolicy policy = policy(100L, "결혼", WelfareTarget.SELF, "7.0");
        WelfareRequest welfare = pendingWelfare(policy, applicant, 9L, null);
        given(welfareRequestRepository.findById(200L)).willReturn(Optional.of(welfare));
        given(userRepository.findById(9L)).willReturn(Optional.of(approver));
        given(welfareRequestRepository.updateStatusIfCurrent(200L, RequestStatus.PENDING, RequestStatus.APPROVED))
                .willReturn(1);

        welfareService.processApproval(200L, 9L, new WelfareApprovalRequest(true, "확인"));

        assertEquals(0, new BigDecimal("7.0").compareTo(applicant.getBonusDays()));
        verify(welfareActionHistoryRepository).save(historyWith(RequestAction.APPROVED));
    }

    @Test
    @DisplayName("반려 — bonus_days 가산 없음")
    void reject_doesNotAddBonusDays() {
        User applicant = user(1L, Role.EMPLOYEE);
        User approver = user(9L, Role.SYSTEM_ADMIN);
        WelfarePolicy policy = policy(100L, "결혼", WelfareTarget.SELF, "7.0");
        WelfareRequest welfare = pendingWelfare(policy, applicant, 9L, null);
        given(welfareRequestRepository.findById(200L)).willReturn(Optional.of(welfare));
        given(userRepository.findById(9L)).willReturn(Optional.of(approver));
        given(welfareRequestRepository.updateStatusIfCurrent(200L, RequestStatus.PENDING, RequestStatus.REJECTED))
                .willReturn(1);

        welfareService.processApproval(200L, 9L, new WelfareApprovalRequest(false, "반려"));

        assertEquals(0, BigDecimal.ZERO.compareTo(applicant.getBonusDays()));
        verify(welfareActionHistoryRepository).save(historyWith(RequestAction.REJECTED));
    }

    @Test
    @DisplayName("승인 — 조건부 갱신 rowcount 0(동시 처리 패배): ALREADY_PROCESSED, bonus_days 미가산")
    void approve_alreadyProcessed_whenRowcountZero() {
        User applicant = user(1L, Role.EMPLOYEE);
        User approver = user(9L, Role.SYSTEM_ADMIN);
        WelfarePolicy policy = policy(100L, "결혼", WelfareTarget.SELF, "7.0");
        WelfareRequest welfare = pendingWelfare(policy, applicant, 9L, null);
        given(welfareRequestRepository.findById(200L)).willReturn(Optional.of(welfare));
        given(userRepository.findById(9L)).willReturn(Optional.of(approver));
        given(welfareRequestRepository.updateStatusIfCurrent(200L, RequestStatus.PENDING, RequestStatus.APPROVED))
                .willReturn(0);

        BusinessException ex = assertThrows(BusinessException.class,
                () -> welfareService.processApproval(200L, 9L, new WelfareApprovalRequest(true, "확인")));

        assertEquals(ErrorCode.ALREADY_PROCESSED, ex.getErrorCode());
        assertEquals(0, BigDecimal.ZERO.compareTo(applicant.getBonusDays()));
        verify(welfareActionHistoryRepository, never()).save(any());
    }

    @Test
    @DisplayName("승인 — 승인자가 아닌 사용자: ACCESS_DENIED")
    void approve_notApprover_accessDenied() {
        User applicant = user(1L, Role.EMPLOYEE);
        User stranger = user(7L, Role.TEAM_LEADER);
        WelfarePolicy policy = policy(100L, "결혼", WelfareTarget.SELF, "7.0");
        WelfareRequest welfare = pendingWelfare(policy, applicant, 9L, null);
        given(welfareRequestRepository.findById(200L)).willReturn(Optional.of(welfare));
        given(userRepository.findById(7L)).willReturn(Optional.of(stranger));

        BusinessException ex = assertThrows(BusinessException.class,
                () -> welfareService.processApproval(200L, 7L, new WelfareApprovalRequest(true, "확인")));

        assertEquals(ErrorCode.ACCESS_DENIED, ex.getErrorCode());
        verify(welfareRequestRepository, never()).updateStatusIfCurrent(any(), any(), any());
    }

    @Test
    @DisplayName("승인 — sub 승인자도 처리 가능")
    void approve_bySubApprover_success() {
        User applicant = user(1L, Role.EMPLOYEE);
        User sub = user(5L, Role.TEAM_LEADER);
        WelfarePolicy policy = policy(100L, "결혼", WelfareTarget.SELF, "7.0");
        WelfareRequest welfare = pendingWelfare(policy, applicant, 9L, 5L);
        given(welfareRequestRepository.findById(200L)).willReturn(Optional.of(welfare));
        given(userRepository.findById(5L)).willReturn(Optional.of(sub));
        given(welfareRequestRepository.updateStatusIfCurrent(200L, RequestStatus.PENDING, RequestStatus.APPROVED))
                .willReturn(1);

        welfareService.processApproval(200L, 5L, new WelfareApprovalRequest(true, "확인"));

        assertEquals(0, new BigDecimal("7.0").compareTo(applicant.getBonusDays()));
    }

    // ============================ 취소 ============================

    @Test
    @DisplayName("취소 — 본인 + PENDING: 조건부 갱신 성공")
    void cancel_ownerPending_success() {
        User applicant = user(1L, Role.EMPLOYEE);
        WelfarePolicy policy = policy(100L, "결혼", WelfareTarget.SELF, "7.0");
        WelfareRequest welfare = pendingWelfare(policy, applicant, 9L, null);
        given(welfareRequestRepository.findById(200L)).willReturn(Optional.of(welfare));
        given(welfareRequestRepository.updateStatusIfCurrent(200L, RequestStatus.PENDING, RequestStatus.CANCELLED))
                .willReturn(1);

        welfareService.cancel(200L, 1L);

        verify(welfareActionHistoryRepository).save(historyWith(RequestAction.CANCELLED));
    }

    @Test
    @DisplayName("취소 — 본인이 아니면 ACCESS_DENIED")
    void cancel_notOwner_accessDenied() {
        User applicant = user(1L, Role.EMPLOYEE);
        WelfarePolicy policy = policy(100L, "결혼", WelfareTarget.SELF, "7.0");
        WelfareRequest welfare = pendingWelfare(policy, applicant, 9L, null);
        given(welfareRequestRepository.findById(200L)).willReturn(Optional.of(welfare));

        BusinessException ex = assertThrows(BusinessException.class,
                () -> welfareService.cancel(200L, 2L));

        assertEquals(ErrorCode.ACCESS_DENIED, ex.getErrorCode());
        verify(welfareRequestRepository, never()).updateStatusIfCurrent(any(), any(), any());
    }

    @Test
    @DisplayName("취소 — 이미 처리된(PENDING 아닌) 신청: ALREADY_PROCESSED")
    void cancel_alreadyProcessed_whenRowcountZero() {
        User applicant = user(1L, Role.EMPLOYEE);
        WelfarePolicy policy = policy(100L, "결혼", WelfareTarget.SELF, "7.0");
        WelfareRequest welfare = pendingWelfare(policy, applicant, 9L, null);
        given(welfareRequestRepository.findById(200L)).willReturn(Optional.of(welfare));
        given(welfareRequestRepository.updateStatusIfCurrent(200L, RequestStatus.PENDING, RequestStatus.CANCELLED))
                .willReturn(0);

        BusinessException ex = assertThrows(BusinessException.class,
                () -> welfareService.cancel(200L, 1L));

        assertEquals(ErrorCode.ALREADY_PROCESSED, ex.getErrorCode());
    }

    // ============================ 헬퍼 ============================

    private User user(Long id, Role role) {
        return User.builder()
                .id(id)
                .name("user" + id)
                .email("user" + id + "@mlsoft.com")
                .role(role)
                .baseDays(new BigDecimal("15.0"))
                .useDays(BigDecimal.ZERO)
                .bonusDays(BigDecimal.ZERO)
                .advanceDays(BigDecimal.ZERO)
                .isActive(true)
                .build();
    }

    private WelfarePolicy policy(Long id, String category, WelfareTarget target, String defaultDays) {
        WelfarePolicy policy = WelfarePolicy.create(category, target, new BigDecimal(defaultDays), "증빙서류", "설명");
        setId(policy, id);
        return policy;
    }

    private WelfareRequest pendingWelfare(WelfarePolicy policy, User applicant, Long primaryApproverId, Long subApproverId) {
        return WelfareRequest.create(policy, applicant, "사유", primaryApproverId, subApproverId);
    }

    // 리플렉션으로 id 채우기 — @GeneratedValue 필드는 빌더로 직접 지정할 수 없는 정책 픽스처용 헬퍼
    private void setId(WelfarePolicy policy, Long id) {
        try {
            var field = WelfarePolicy.class.getDeclaredField("id");
            field.setAccessible(true);
            field.set(policy, id);
        } catch (ReflectiveOperationException e) {
            throw new IllegalStateException(e);
        }
    }

    private WelfareActionHistory historyWith(RequestAction action) {
        return ArgumentMatchers.argThat(h -> h != null && h.getAction() == action);
    }
}
