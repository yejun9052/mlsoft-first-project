package com.mlsoft.backend.domain.policy.service;

import com.mlsoft.backend.domain.policy.dto.LeavePolicyResponse;
import com.mlsoft.backend.domain.policy.dto.LeavePolicyUpdateRequest;
import com.mlsoft.backend.domain.policy.entity.LeavePolicy;
import com.mlsoft.backend.domain.policy.repository.LeavePolicyRepository;
import com.mlsoft.backend.global.exception.BusinessException;
import com.mlsoft.backend.global.exception.ErrorCode;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.BDDMockito.given;

/**
 * 연차 정책 관리자 서비스 단위 테스트 (GET/PATCH /api/admin/leave-policies, docs/03 시스템 설정).
 * 조회 정렬, 일수 수정, description 미포함 시 기존 값 유지, 존재하지 않는 id 처리를 검증한다.
 */
@ExtendWith(MockitoExtension.class)
class LeavePolicyAdminServiceTest {

    @Mock
    private LeavePolicyRepository leavePolicyRepository;

    @InjectMocks
    private LeavePolicyAdminService leavePolicyAdminService;

    @Test
    @DisplayName("정책 목록 — 근속년수 오름차순 정렬 결과를 그대로 응답으로 변환한다")
    void getAll_returnsOrderedList() {
        LeavePolicy year1 = LeavePolicy.create(1, new BigDecimal("15.0"), "1년차 15일");
        LeavePolicy year2 = LeavePolicy.create(2, new BigDecimal("15.0"), "2년차 15일");
        given(leavePolicyRepository.findAllByOrderByYearsOfServiceAsc())
                .willReturn(List.of(year1, year2));

        List<LeavePolicyResponse> responses = leavePolicyAdminService.getAll();

        assertEquals(2, responses.size());
        assertEquals(1, responses.get(0).yearsOfService());
        assertEquals(2, responses.get(1).yearsOfService());
    }

    @Test
    @DisplayName("정책 수정 — description 포함 시 일수·설명 모두 갱신")
    void update_withDescription_updatesBoth() {
        LeavePolicy policy = LeavePolicy.create(3, new BigDecimal("16.0"), "3년차 16일");
        given(leavePolicyRepository.findById(1L)).willReturn(Optional.of(policy));
        LeavePolicyUpdateRequest request = new LeavePolicyUpdateRequest(new BigDecimal("17.0"), "3년차 17일(수정)");

        LeavePolicyResponse response = leavePolicyAdminService.update(1L, request);

        assertEquals(0, new BigDecimal("17.0").compareTo(response.annualLeaveDays()));
        assertEquals("3년차 17일(수정)", response.description());
    }

    @Test
    @DisplayName("정책 수정 — description 미포함(null)이면 기존 설명을 유지한다")
    void update_withoutDescription_keepsExisting() {
        LeavePolicy policy = LeavePolicy.create(3, new BigDecimal("16.0"), "3년차 16일");
        given(leavePolicyRepository.findById(1L)).willReturn(Optional.of(policy));
        LeavePolicyUpdateRequest request = new LeavePolicyUpdateRequest(new BigDecimal("18.0"), null);

        LeavePolicyResponse response = leavePolicyAdminService.update(1L, request);

        assertEquals(0, new BigDecimal("18.0").compareTo(response.annualLeaveDays()));
        assertEquals("3년차 16일", response.description());
    }

    @Test
    @DisplayName("정책 수정 — 존재하지 않는 id면 LEAVE_POLICY_NOT_FOUND")
    void update_notFound_throws() {
        given(leavePolicyRepository.findById(999L)).willReturn(Optional.empty());
        LeavePolicyUpdateRequest request = new LeavePolicyUpdateRequest(new BigDecimal("18.0"), null);

        BusinessException ex = assertThrows(BusinessException.class,
                () -> leavePolicyAdminService.update(999L, request));

        assertEquals(ErrorCode.LEAVE_POLICY_NOT_FOUND, ex.getErrorCode());
    }
}
