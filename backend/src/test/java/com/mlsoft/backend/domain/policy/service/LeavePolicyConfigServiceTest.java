package com.mlsoft.backend.domain.policy.service;

import com.mlsoft.backend.domain.policy.dto.LeavePolicyConfigResponse;
import com.mlsoft.backend.domain.policy.dto.LeavePolicyConfigUpdateRequest;
import com.mlsoft.backend.domain.policy.entity.LeavePolicyConfig;
import com.mlsoft.backend.domain.policy.repository.LeavePolicyConfigRepository;
import com.mlsoft.backend.global.exception.BusinessException;
import com.mlsoft.backend.global.exception.ErrorCode;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.BDDMockito.given;

/**
 * 연차 시스템 설정 서비스 단위 테스트 (GET/PUT /api/admin/configs, docs/02 3-11).
 * name 기준 조회·갱신, 존재하지 않는 키 처리를 검증한다.
 */
@ExtendWith(MockitoExtension.class)
class LeavePolicyConfigServiceTest {

    @Mock
    private LeavePolicyConfigRepository leavePolicyConfigRepository;

    @InjectMocks
    private LeavePolicyConfigService leavePolicyConfigService;

    @Test
    @DisplayName("설정 목록 — 등록 순서(id 오름차순) 그대로 응답으로 변환한다")
    void getAll_returnsOrderedList() {
        LeavePolicyConfig advance = LeavePolicyConfig.create("advance_leave_enabled", "false");
        LeavePolicyConfig reminder = LeavePolicyConfig.create("reminder_list_days", "30");
        given(leavePolicyConfigRepository.findAllByOrderByIdAsc())
                .willReturn(List.of(advance, reminder));

        List<LeavePolicyConfigResponse> responses = leavePolicyConfigService.getAll();

        assertEquals(2, responses.size());
        assertEquals("advance_leave_enabled", responses.get(0).name());
        assertEquals("reminder_list_days", responses.get(1).name());
    }

    @Test
    @DisplayName("설정 변경 — name으로 조회해 value를 갱신한다")
    void update_success() {
        LeavePolicyConfig config = LeavePolicyConfig.create("advance_leave_enabled", "false");
        given(leavePolicyConfigRepository.findByName("advance_leave_enabled"))
                .willReturn(Optional.of(config));
        LeavePolicyConfigUpdateRequest request = new LeavePolicyConfigUpdateRequest("advance_leave_enabled", "true");

        LeavePolicyConfigResponse response = leavePolicyConfigService.update(request);

        assertEquals("true", response.value());
        assertEquals("true", config.getValue());
    }

    @Test
    @DisplayName("설정 변경 — 존재하지 않는 키면 LEAVE_POLICY_CONFIG_NOT_FOUND")
    void update_notFound_throws() {
        given(leavePolicyConfigRepository.findByName("unknown_key")).willReturn(Optional.empty());
        LeavePolicyConfigUpdateRequest request = new LeavePolicyConfigUpdateRequest("unknown_key", "1");

        BusinessException ex = assertThrows(BusinessException.class,
                () -> leavePolicyConfigService.update(request));

        assertEquals(ErrorCode.LEAVE_POLICY_CONFIG_NOT_FOUND, ex.getErrorCode());
    }
}
