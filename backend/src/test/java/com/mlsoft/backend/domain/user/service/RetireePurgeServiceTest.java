package com.mlsoft.backend.domain.user.service;

import com.mlsoft.backend.domain.email.service.EmailNotificationPublisher;
import com.mlsoft.backend.domain.policy.entity.PolicyConfigKey;
import com.mlsoft.backend.domain.policy.service.PolicyConfigReader;
import com.mlsoft.backend.domain.user.entity.Role;
import com.mlsoft.backend.domain.user.entity.User;
import com.mlsoft.backend.domain.user.repository.UserRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

/** 퇴직자 자동 파기 대상·예고 중복·보류 제외 규칙을 검증한다. */
@ExtendWith(MockitoExtension.class)
class RetireePurgeServiceTest {

    private static final LocalDate TODAY = LocalDate.of(2026, 3, 1);

    @Mock
    private UserRepository userRepository;
    @Mock
    private PolicyConfigReader policyConfigReader;
    @Mock
    private UserService userService;
    @Mock
    private EmailNotificationPublisher emailNotificationPublisher;

    @Test
    @DisplayName("MANUAL 모드면 대상 조회와 메일 발행을 하지 않는다")
    void manualMode_noTargetsOrMail() {
        RetireePurgeService service = service();
        given(policyConfigReader.getString(PolicyConfigKey.RETIREE_PURGE_MODE)).willReturn("MANUAL");

        assertEquals(List.of(), service.findNoticeTargetIds(TODAY));
        assertEquals(List.of(), service.findTargetIds(TODAY));
        assertEquals(0, service.publishResult(1, TODAY));

        verify(userRepository, never()).findIdsForRetireePurgeNotice(any());
        verify(userRepository, never()).findIdsForRetireePurge(any(), any());
        verify(emailNotificationPublisher, never()).publishRetireePurgeResult(any(Integer.class), eq(TODAY));
    }

    @Test
    @DisplayName("예고는 한 번만 저장하고 같은 대상에 중복 메일을 보내지 않는다")
    void notice_isIdempotent() {
        RetireePurgeService service = service();
        User target = retiredUser(7L, TODAY.minusYears(3).plusDays(30));
        given(policyConfigReader.getString(PolicyConfigKey.RETIREE_PURGE_MODE)).willReturn("AUTO");
        given(policyConfigReader.getInt(PolicyConfigKey.RETIREE_PURGE_YEARS)).willReturn(3);
        given(userRepository.findIdsForRetireePurgeNotice(TODAY.plusDays(30).minusYears(3)))
                .willReturn(List.of(7L));
        given(userRepository.findById(7L)).willReturn(Optional.of(target));

        assertEquals(List.of(7L), service.findNoticeTargetIds(TODAY));
        assertEquals(1, service.sendNotice(7L, TODAY));
        assertEquals(0, service.sendNotice(7L, TODAY));
        given(userRepository.findAllById(List.of(7L))).willReturn(List.of(target));
        assertEquals(1, service.publishNotice(List.of(7L), TODAY));

        assertEquals(TODAY, target.getPurgeNoticeSentAt().toLocalDate());
        verify(emailNotificationPublisher).publishRetireePurgeNotice(
                List.of("퇴직자 7"), TODAY.plusDays(RetireePurgeService.NOTICE_LEAD_DAYS));
    }

    @Test
    @DisplayName("보류자는 예고 대상에서 빠진다")
    void hold_skipsNotice() {
        RetireePurgeService service = service();
        User target = retiredUser(8L, TODAY.minusYears(3).plusDays(30));
        target.placePurgeHold("분쟁 진행 중");
        given(policyConfigReader.getString(PolicyConfigKey.RETIREE_PURGE_MODE)).willReturn("AUTO");
        given(policyConfigReader.getInt(PolicyConfigKey.RETIREE_PURGE_YEARS)).willReturn(3);
        given(userRepository.findById(8L)).willReturn(Optional.of(target));

        assertEquals(0, service.sendNotice(8L, TODAY));
        verify(emailNotificationPublisher, never()).publishRetireePurgeNotice(any(), any());
        assertNull(target.getPurgeNoticeSentAt());
    }

    private RetireePurgeService service() {
        return new RetireePurgeService(
                userRepository, policyConfigReader, userService, emailNotificationPublisher);
    }

    private User retiredUser(Long id, LocalDate retiredAt) {
        return User.builder()
                .id(id)
                .name("퇴직자 " + id)
                .email("retiree-" + id + "@mlsoft.com")
                .role(Role.EMPLOYEE)
                .baseDays(new BigDecimal("10.0"))
                .useDays(BigDecimal.ZERO)
                .bonusDays(BigDecimal.ZERO)
                .advanceDays(BigDecimal.ZERO)
                .isActive(false)
                .retiredAt(retiredAt)
                .build();
    }
}
