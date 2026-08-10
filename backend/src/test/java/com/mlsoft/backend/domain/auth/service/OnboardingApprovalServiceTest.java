package com.mlsoft.backend.domain.auth.service;

import com.mlsoft.backend.domain.audit.entity.AdminAction;
import com.mlsoft.backend.domain.audit.service.AdminAuditService;
import com.mlsoft.backend.domain.user.entity.OnboardingStatus;
import com.mlsoft.backend.domain.user.entity.Role;
import com.mlsoft.backend.domain.user.entity.User;
import com.mlsoft.backend.domain.user.repository.UserRepository;
import com.mlsoft.backend.global.exception.BusinessException;
import com.mlsoft.backend.global.exception.ErrorCode;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.willAnswer;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

/**
 * 온보딩 승인 절차 (리뷰 S-1).
 *
 * <p>연차 부여는 {@link AuthService#grantInitialLeave}에 위임하므로 여기서는 <b>위임이 실제로
 * 일어나는지</b>와 <b>상태 전이</b>를 본다. 부여 산정 자체는 {@code AuthServiceTest}가 검증한다.
 */
@ExtendWith(MockitoExtension.class)
class OnboardingApprovalServiceTest {

    @Mock
    private UserRepository userRepository;
    @Mock
    private AuthService authService;
    @Mock
    private AdminAuditService adminAuditService;

    @InjectMocks
    private OnboardingApprovalService onboardingApprovalService;

    @Test
    @DisplayName("승인 — 신고한 입사일로 자동 승인과 같은 부여 경로를 탄다")
    void approve_대기중_같은부여경로위임() {
        // 부여 로직을 여기에 복사하면 정책이 바뀔 때 자동 승인과 관리자 승인이 갈라진다
        LocalDate hireDate = LocalDate.of(2020, 3, 1);
        User user = pendingUser(hireDate);
        given(userRepository.findById(1L)).willReturn(Optional.of(user));
        // 위임된 부여를 흉내 낸다 — 실제 구현은 completeOnboarding으로 상태를 COMPLETED로 올린다
        willAnswer(invocation -> {
            user.completeOnboarding(hireDate, user.getBirthDay());
            return null;
        }).given(authService).grantInitialLeave(eq(user), eq(hireDate), any(), any());

        onboardingApprovalService.approve(1L, 99L);

        verify(authService).grantInitialLeave(eq(user), eq(hireDate), any(), any());
        assertTrue(user.isOnboardingCompleted());
    }

    @Test
    @DisplayName("반려 — 입력값을 지우고 처음으로 되돌려 사원이 다시 낼 수 있게 한다")
    void reject_대기중_입력값초기화() {
        // 상태만 두고 값을 남기면 그 계정은 관리자가 DB를 고칠 때까지 잠긴다 — S-1의 원래 증상이다
        User user = pendingUser(LocalDate.of(1990, 1, 1));
        given(userRepository.findById(1L)).willReturn(Optional.of(user));

        onboardingApprovalService.reject(1L, 99L);

        assertEquals(OnboardingStatus.NOT_STARTED, user.getOnboardingStatus());
        assertNull(user.getHireDate());
        assertNull(user.getBirthDay());
        verify(authService, never()).grantInitialLeave(any(), any(), any(), any());
    }

    @Test
    @DisplayName("대기 상태가 아니면 승인·반려 모두 ONBOARDING_NOT_PENDING")
    void approve_대기아님_예외() {
        // 이미 처리된 건을 두 번 승인하면 연차가 두 번 부여된다
        User completed = pendingUser(LocalDate.of(2020, 3, 1));
        completed.completeOnboarding(LocalDate.of(2020, 3, 1), LocalDate.of(1990, 5, 5));
        given(userRepository.findById(1L)).willReturn(Optional.of(completed));

        BusinessException e = assertThrows(BusinessException.class,
                () -> onboardingApprovalService.approve(1L, 99L));
        assertEquals(ErrorCode.ONBOARDING_NOT_PENDING, e.getErrorCode());
        verify(authService, never()).grantInitialLeave(any(), any(), any(), any());
    }

    @Test
    @DisplayName("감사 — 승인은 부여된 연차까지 남는다 (승인 한 번으로 연차가 생기므로)")
    void approve_감사기록() {
        LocalDate hireDate = LocalDate.of(2020, 3, 1);
        User user = pendingUser(hireDate);
        given(userRepository.findById(1L)).willReturn(Optional.of(user));
        willAnswer(invocation -> {
            user.completeOnboarding(hireDate, user.getBirthDay());
            user.updateBaseDays(new BigDecimal("15.0"));
            return null;
        }).given(authService).grantInitialLeave(eq(user), eq(hireDate), any(), any());

        onboardingApprovalService.approve(1L, 99L);

        verify(adminAuditService).recordUserChange(99L, AdminAction.ONBOARDING_APPROVED, user,
                "승인 대기 (입사일 2020-03-01)", "확정 · 연차 15.0일");
    }

    @Test
    @DisplayName("감사 — 반려는 지워지는 입사일을 남긴다 (여기 말고는 남지 않는다)")
    void reject_감사기록() {
        User user = pendingUser(LocalDate.of(1990, 1, 1));
        given(userRepository.findById(1L)).willReturn(Optional.of(user));

        onboardingApprovalService.reject(1L, 99L);

        assertNull(user.getHireDate()); // 엔티티에서는 사라졌다
        verify(adminAuditService).recordUserChange(99L, AdminAction.ONBOARDING_REJECTED, user,
                "승인 대기 (입사일 1990-01-01)", "반려 · 온보딩 초기화");
    }

    // ============================ 헬퍼 ============================

    private User pendingUser(LocalDate hireDate) {
        User user = User.builder()
                .id(1L)
                .name("승인 대기자")
                .email("pending@mlsoft.com")
                .role(Role.EMPLOYEE)
                .baseDays(BigDecimal.ZERO)
                .useDays(BigDecimal.ZERO)
                .bonusDays(BigDecimal.ZERO)
                .advanceDays(BigDecimal.ZERO)
                .isActive(true)
                .build();
        user.requestOnboardingApproval(hireDate, LocalDate.of(1990, 5, 5));
        return user;
    }
}
