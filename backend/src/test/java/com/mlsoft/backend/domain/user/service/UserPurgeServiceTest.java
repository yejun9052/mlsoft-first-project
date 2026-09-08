package com.mlsoft.backend.domain.user.service;

import com.mlsoft.backend.domain.audit.service.AdminAuditService;
import com.mlsoft.backend.domain.email.repository.EmailHistoryRepository;
import com.mlsoft.backend.domain.department.repository.DepartmentRepository;
import com.mlsoft.backend.domain.leave.repository.LeaveActionHistoryRepository;
import com.mlsoft.backend.domain.leave.repository.LeaveRequestRepository;
import com.mlsoft.backend.domain.policy.entity.PolicyConfigKey;
import com.mlsoft.backend.domain.policy.service.PolicyConfigReader;
import com.mlsoft.backend.domain.user.entity.Role;
import com.mlsoft.backend.domain.user.entity.User;
import com.mlsoft.backend.domain.user.repository.UserRepository;
import com.mlsoft.backend.domain.welfare.repository.WelfareActionHistoryRepository;
import com.mlsoft.backend.domain.welfare.repository.WelfareRequestRepository;
import com.mlsoft.backend.global.exception.BusinessException;
import com.mlsoft.backend.global.exception.ErrorCode;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;

/** 퇴직자 수동 파기·보류·대상 판정의 서비스 규칙을 검증한다. */
@ExtendWith(MockitoExtension.class)
class UserPurgeServiceTest {

    private static final Long ACTOR_ID = 99L;

    @Mock
    private UserRepository userRepository;
    @Mock
    private DepartmentRepository departmentRepository;
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

    @InjectMocks
    private UserService userService;

    @Test
    @DisplayName("퇴직 3년 미만 파기 — 거부하고 users 데이터도 그대로 둔다")
    void purge_retentionNotMet_keepsData() {
        User target = retiredUser(1L, LocalDate.now().minusYears(3).plusDays(1));
        given(userRepository.findById(target.getId())).willReturn(Optional.of(target));

        BusinessException exception = assertThrows(BusinessException.class,
                () -> userService.purge(target.getId(), ACTOR_ID));

        assertEquals(ErrorCode.PURGE_RETENTION_NOT_MET, exception.getErrorCode());
        assertEquals("퇴직자 1", target.getName());
        assertEquals("retiree-1@mlsoft.com", target.getEmail());
        assertEquals(LocalDate.of(1990, 1, 1), target.getBirthDay());
        assertEquals(LocalDate.of(2018, 1, 1), target.getHireDate());
        verifyNoInteractions(leaveRequestRepository, welfareRequestRepository,
                leaveActionHistoryRepository, welfareActionHistoryRepository,
                emailHistoryRepository, adminAuditService);
    }

    @Test
    @DisplayName("보류된 퇴직자 파기 — PURGE_ON_HOLD")
    void purge_onHold_rejected() {
        User target = retiredUser(1L, LocalDate.now().minusYears(4));
        target.placePurgeHold("분쟁 진행 중");
        given(userRepository.findById(target.getId())).willReturn(Optional.of(target));

        BusinessException exception = assertThrows(BusinessException.class,
                () -> userService.purge(target.getId(), ACTOR_ID));

        assertEquals(ErrorCode.PURGE_ON_HOLD, exception.getErrorCode());
        verifyNoInteractions(leaveRequestRepository, welfareRequestRepository,
                leaveActionHistoryRepository, welfareActionHistoryRepository,
                emailHistoryRepository, adminAuditService);
    }

    @Test
    @DisplayName("이미 파기된 퇴직자 재파기 — ALREADY_PURGED")
    void purge_alreadyPurged_rejected() {
        User target = retiredUser(1L, LocalDate.now().minusYears(4));
        target.purge(LocalDateTime.now());
        given(userRepository.findById(target.getId())).willReturn(Optional.of(target));

        BusinessException exception = assertThrows(BusinessException.class,
                () -> userService.purge(target.getId(), ACTOR_ID));

        assertEquals(ErrorCode.ALREADY_PURGED, exception.getErrorCode());
        verifyNoInteractions(leaveRequestRepository, welfareRequestRepository,
                leaveActionHistoryRepository, welfareActionHistoryRepository,
                emailHistoryRepository, adminAuditService);
    }

    @Test
    @DisplayName("파기 성공 — users 익명화·잔액 유지·5개 벌크 파기·감사 기록")
    void purge_success_anonymizesAllFreeTextAndKeepsBalance() {
        User target = retiredUser(42L, LocalDate.now().minusYears(4));
        given(userRepository.findById(target.getId())).willReturn(Optional.of(target));

        userService.purge(target.getId(), ACTOR_ID);

        assertEquals("퇴직사원#42", target.getName());
        assertEquals("deleted-42@invalid", target.getEmail());
        assertNull(target.getBirthDay());
        assertNull(target.getHireDate());
        assertNull(target.getPosition());
        assertNull(target.getJobGrade());
        assertTrue(target.getPurgedAt() != null);
        assertEquals(new BigDecimal("10.0"), target.getBaseDays());
        assertEquals(new BigDecimal("2.0"), target.getUseDays());
        assertEquals(new BigDecimal("1.0"), target.getBonusDays());
        assertEquals(new BigDecimal("3.0"), target.getAdvanceDays());
        verify(leaveRequestRepository).anonymizeRequestReasonsByUserId(target.getId());
        verify(welfareRequestRepository).anonymizeReasonsByUserId(target.getId());
        verify(leaveActionHistoryRepository).anonymizeCommentsByUserId(target.getId());
        verify(welfareActionHistoryRepository).anonymizeCommentsByUserId(target.getId());
        verify(emailHistoryRepository).anonymizeContentByRecipientId(target.getId());
        verify(adminAuditService).recordUserPurged(ACTOR_ID, target);
    }

    @Test
    @DisplayName("보류 설정 — 빈 사유는 거부하고 유효한 사유는 공백을 정리한다")
    void purgeHold_requiresReason() {
        User target = retiredUser(1L, LocalDate.now().minusYears(4));
        given(userRepository.findById(target.getId())).willReturn(Optional.of(target));

        BusinessException exception = assertThrows(BusinessException.class,
                () -> userService.placePurgeHold(target.getId(), "  ", ACTOR_ID));
        assertEquals(ErrorCode.INVALID_INPUT_VALUE, exception.getErrorCode());
        assertNull(target.getPurgeHoldReason());

        userService.placePurgeHold(target.getId(), "  재입사 예정  ", ACTOR_ID);
        assertEquals("재입사 예정", target.getPurgeHoldReason());
        userService.releasePurgeHold(target.getId(), ACTOR_ID);
        assertNull(target.getPurgeHoldReason());
    }

    @Test
    @DisplayName("퇴직자 목록 — MANUAL은 전체, AUTO는 보존 기간 경과자 조회")
    void retiredUsers_manualAndAutoHaveDifferentTargets() {
        User recent = retiredUser(1L, LocalDate.now().minusYears(2));
        User old = retiredUser(2L, LocalDate.now().minusYears(4));
        PageRequest pageRequest = PageRequest.of(0, 20);

        given(policyConfigReader.getString(PolicyConfigKey.RETIREE_PURGE_MODE))
                .willReturn("MANUAL", "AUTO");
        given(policyConfigReader.getInt(PolicyConfigKey.RETIREE_PURGE_YEARS)).willReturn(3);
        given(userRepository.findByIsActiveFalse(pageRequest))
                .willReturn(new PageImpl<>(List.of(recent, old), pageRequest, 2));
        given(userRepository.findByIsActiveFalseAndRetiredAtLessThanEqual(
                any(LocalDate.class), eq(pageRequest)))
                .willReturn(new PageImpl<>(List.of(old), pageRequest, 1));

        Page<?> manual = userService.getRetiredUsers(pageRequest);
        Page<?> auto = userService.getRetiredUsers(pageRequest);

        assertEquals(2, manual.getTotalElements());
        assertEquals(1, auto.getTotalElements());
        assertFalse(((com.mlsoft.backend.domain.user.dto.UserResponse) manual.getContent().get(0)).purgeEligible());
        assertTrue(((com.mlsoft.backend.domain.user.dto.UserResponse) auto.getContent().get(0)).purgeEligible());
        verify(userRepository).findByIsActiveFalse(pageRequest);
        verify(userRepository).findByIsActiveFalseAndRetiredAtLessThanEqual(
                any(LocalDate.class), eq(pageRequest));
    }

    private User retiredUser(Long id, LocalDate retiredAt) {
        return User.builder()
                .id(id)
                .name("퇴직자 " + id)
                .email("retiree-" + id + "@mlsoft.com")
                .birthDay(LocalDate.of(1990, 1, 1))
                .hireDate(LocalDate.of(2018, 1, 1))
                .position("선임")
                .jobGrade("수석연구원")
                .role(Role.EMPLOYEE)
                .baseDays(new BigDecimal("10.0"))
                .useDays(new BigDecimal("2.0"))
                .bonusDays(new BigDecimal("1.0"))
                .advanceDays(new BigDecimal("3.0"))
                .isActive(false)
                .retiredAt(retiredAt)
                .build();
    }
}
