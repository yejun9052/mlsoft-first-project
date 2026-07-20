package com.mlsoft.backend.domain.welfare.service;

import com.mlsoft.backend.domain.welfare.dto.WelfarePolicyRequest;
import com.mlsoft.backend.domain.welfare.dto.WelfarePolicyResponse;
import com.mlsoft.backend.domain.welfare.entity.WelfarePolicy;
import com.mlsoft.backend.domain.welfare.entity.WelfareTarget;
import com.mlsoft.backend.domain.welfare.repository.WelfarePolicyRepository;
import com.mlsoft.backend.global.exception.BusinessException;
import com.mlsoft.backend.global.exception.ErrorCode;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

/**
 * 복리후생 정책 서비스 단위 테스트 (docs/03 복리후생 정책 CRUD).
 * 구분·대상 조합 중복 검증, 소프트 삭제(비활성화), 존재하지 않는/비활성 정책 처리를 검증한다.
 */
@ExtendWith(MockitoExtension.class)
class WelfarePolicyServiceTest {

    @Mock
    private WelfarePolicyRepository welfarePolicyRepository;

    @InjectMocks
    private WelfarePolicyService welfarePolicyService;

    @Test
    @DisplayName("정책 추가 — 구분/대상 조합이 새로우면 생성")
    void create_success() {
        WelfarePolicyRequest request = new WelfarePolicyRequest(
                "결혼", WelfareTarget.SELF, new BigDecimal("7.0"), "청첩장", "본인 결혼");
        given(welfarePolicyRepository.existsByCategoryAndTargetAndActiveTrue("결혼", WelfareTarget.SELF))
                .willReturn(false);

        WelfarePolicyResponse response = welfarePolicyService.create(request);

        assertEquals("결혼", response.category());
        assertEquals(WelfareTarget.SELF, response.target());
        assertEquals(0, new BigDecimal("7.0").compareTo(response.defaultDays()));
        verify(welfarePolicyRepository).save(any(WelfarePolicy.class));
    }

    @Test
    @DisplayName("정책 추가 — 이미 동일한 구분/대상 조합이 활성 상태로 존재: DUPLICATE_WELFARE_POLICY")
    void create_duplicate_throws() {
        WelfarePolicyRequest request = new WelfarePolicyRequest(
                "결혼", WelfareTarget.SELF, new BigDecimal("7.0"), "청첩장", "본인 결혼");
        given(welfarePolicyRepository.existsByCategoryAndTargetAndActiveTrue("결혼", WelfareTarget.SELF))
                .willReturn(true);

        BusinessException ex = assertThrows(BusinessException.class,
                () -> welfarePolicyService.create(request));

        assertEquals(ErrorCode.DUPLICATE_WELFARE_POLICY, ex.getErrorCode());
        verify(welfarePolicyRepository, never()).save(any());
    }

    @Test
    @DisplayName("정책 수정 — 구분/대상 조합을 바꾸지 않으면 중복 검사를 하지 않는다")
    void update_sameCombo_skipsDuplicateCheck() {
        WelfarePolicy policy = WelfarePolicy.create(
                "결혼", WelfareTarget.SELF, new BigDecimal("7.0"), "청첩장", "본인 결혼");
        given(welfarePolicyRepository.findByIdAndActiveTrue(1L)).willReturn(Optional.of(policy));
        WelfarePolicyRequest request = new WelfarePolicyRequest(
                "결혼", WelfareTarget.SELF, new BigDecimal("10.0"), "청첩장(수정)", "본인 결혼(수정)");

        WelfarePolicyResponse response = welfarePolicyService.update(1L, request);

        assertEquals(0, new BigDecimal("10.0").compareTo(response.defaultDays()));
        assertEquals("청첩장(수정)", response.defaultEvidence());
        verify(welfarePolicyRepository, never())
                .existsByCategoryAndTargetAndActiveTrue(anyString(), any());
    }

    @Test
    @DisplayName("정책 수정 — 구분/대상 조합 변경 시 이미 존재하는 조합이면 DUPLICATE_WELFARE_POLICY")
    void update_comboChangedToDuplicate_throws() {
        WelfarePolicy policy = WelfarePolicy.create(
                "결혼", WelfareTarget.SELF, new BigDecimal("7.0"), "청첩장", "본인 결혼");
        given(welfarePolicyRepository.findByIdAndActiveTrue(1L)).willReturn(Optional.of(policy));
        given(welfarePolicyRepository.existsByCategoryAndTargetAndActiveTrue("결혼", WelfareTarget.SIBLING))
                .willReturn(true);
        WelfarePolicyRequest request = new WelfarePolicyRequest(
                "결혼", WelfareTarget.SIBLING, new BigDecimal("1.0"), "청첩장", "형제 결혼");

        BusinessException ex = assertThrows(BusinessException.class,
                () -> welfarePolicyService.update(1L, request));

        assertEquals(ErrorCode.DUPLICATE_WELFARE_POLICY, ex.getErrorCode());
    }

    @Test
    @DisplayName("정책 수정 — 존재하지 않거나 비활성화된 정책: WELFARE_POLICY_NOT_FOUND")
    void update_notFound_throws() {
        given(welfarePolicyRepository.findByIdAndActiveTrue(1L)).willReturn(Optional.empty());
        WelfarePolicyRequest request = new WelfarePolicyRequest(
                "결혼", WelfareTarget.SELF, new BigDecimal("7.0"), "청첩장", "본인 결혼");

        BusinessException ex = assertThrows(BusinessException.class,
                () -> welfarePolicyService.update(1L, request));

        assertEquals(ErrorCode.WELFARE_POLICY_NOT_FOUND, ex.getErrorCode());
    }

    @Test
    @DisplayName("정책 비활성화 — 소프트 삭제 (active=false)")
    void deactivate_success() {
        WelfarePolicy policy = WelfarePolicy.create(
                "결혼", WelfareTarget.SELF, new BigDecimal("7.0"), "청첩장", "본인 결혼");
        given(welfarePolicyRepository.findByIdAndActiveTrue(1L)).willReturn(Optional.of(policy));

        welfarePolicyService.deactivate(1L);

        assertFalse(policy.isActive());
    }

    @Test
    @DisplayName("정책 비활성화 — 존재하지 않으면 WELFARE_POLICY_NOT_FOUND")
    void deactivate_notFound_throws() {
        given(welfarePolicyRepository.findByIdAndActiveTrue(1L)).willReturn(Optional.empty());

        BusinessException ex = assertThrows(BusinessException.class,
                () -> welfarePolicyService.deactivate(1L));

        assertEquals(ErrorCode.WELFARE_POLICY_NOT_FOUND, ex.getErrorCode());
    }
}
