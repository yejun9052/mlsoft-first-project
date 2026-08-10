package com.mlsoft.backend.domain.policy.service;

import com.mlsoft.backend.domain.audit.service.AdminAuditService;
import com.mlsoft.backend.domain.policy.dto.LeavePolicyConfigResponse;
import com.mlsoft.backend.domain.policy.dto.LeavePolicyConfigUpdateRequest;
import com.mlsoft.backend.domain.policy.entity.ConfigValueType;
import com.mlsoft.backend.domain.policy.entity.LeavePolicyConfig;
import com.mlsoft.backend.domain.policy.entity.PolicyConfigKey;
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
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.BDDMockito.given;

/**
 * 연차 시스템 설정 서비스 단위 테스트 (GET/PUT /api/admin/configs, docs/02 3-11).
 *
 * 목록은 카탈로그({@link PolicyConfigKey}) 기준으로 만들어지고, 갱신은 저장 전에 타입·범위를 검증한다.
 * 검증이 없으면 잘못된 값이 저장되고 <b>값을 읽는 시점</b>(사원의 연차 신청)에 문제가 드러난다.
 */
@ExtendWith(MockitoExtension.class)
class LeavePolicyConfigServiceTest {

    /** 조작한 관리자 id — 감사 기록의 actor (리뷰 S-3) */
    private static final Long ACTOR_ID = 99L;

    @Mock
    private LeavePolicyConfigRepository leavePolicyConfigRepository;
    @Mock
    private AdminAuditService adminAuditService;

    @InjectMocks
    private LeavePolicyConfigService leavePolicyConfigService;

    // ============================ 목록 ============================

    @Test
    @DisplayName("설정 목록 — 카탈로그 전체를 선언 순서대로, 메타데이터까지 함께 응답한다")
    void getAll_returnsWholeCatalogWithMetadata() {
        given(leavePolicyConfigRepository.findAllByOrderByIdAsc()).willReturn(List.of(
                LeavePolicyConfig.create(PolicyConfigKey.ADVANCE_LEAVE_ENABLED.getKey(), "true")));

        List<LeavePolicyConfigResponse> responses = leavePolicyConfigService.getAll();

        assertEquals(PolicyConfigKey.values().length, responses.size());
        LeavePolicyConfigResponse first = responses.get(0);
        assertEquals(PolicyConfigKey.ADVANCE_LEAVE_ENABLED.getKey(), first.name());
        assertEquals("true", first.value()); // 저장된 값
        assertEquals(ConfigValueType.BOOLEAN, first.type());
        assertNotNull(first.label());
        assertNotNull(first.description());
    }

    @Test
    @DisplayName("설정 목록 — 아직 시딩되지 않은 키도 기본값과 함께 보여준다")
    void getAll_unseededKey_fallsBackToDefault() {
        given(leavePolicyConfigRepository.findAllByOrderByIdAsc()).willReturn(List.of());

        List<LeavePolicyConfigResponse> responses = leavePolicyConfigService.getAll();

        LeavePolicyConfigResponse advanceMax = responses.stream()
                .filter(r -> r.name().equals(PolicyConfigKey.ADVANCE_MAX_DAYS.getKey()))
                .findFirst()
                .orElseThrow();
        assertNull(advanceMax.id()); // DB 행이 없음
        assertEquals(PolicyConfigKey.ADVANCE_MAX_DAYS.getDefaultValue(), advanceMax.value());
        assertEquals(0, PolicyConfigKey.ADVANCE_MAX_DAYS.getMax().compareTo(advanceMax.max()));
    }

    // ============================ 갱신 ============================

    @Test
    @DisplayName("설정 변경 — name으로 조회해 value를 갱신한다")
    void update_success() {
        LeavePolicyConfig config = LeavePolicyConfig.create("advance_leave_enabled", "false");
        given(leavePolicyConfigRepository.findByName("advance_leave_enabled"))
                .willReturn(Optional.of(config));

        LeavePolicyConfigResponse response = leavePolicyConfigService
                .update(new LeavePolicyConfigUpdateRequest("advance_leave_enabled", "true"), ACTOR_ID);

        assertEquals("true", response.value());
        assertEquals("true", config.getValue());
    }

    @Test
    @DisplayName("설정 변경 — 카탈로그에 없는 키면 LEAVE_POLICY_CONFIG_NOT_FOUND")
    void update_unknownKey_throws() {
        LeavePolicyConfigUpdateRequest request = new LeavePolicyConfigUpdateRequest("unknown_key", "1");

        BusinessException ex = assertThrows(BusinessException.class,
                () -> leavePolicyConfigService.update(request, ACTOR_ID));

        assertEquals(ErrorCode.LEAVE_POLICY_CONFIG_NOT_FOUND, ex.getErrorCode());
    }

    @Test
    @DisplayName("설정 변경 — BOOLEAN 오타는 INVALID_CONFIG_VALUE (parseBoolean이 조용히 false로 만드는 것 차단)")
    void update_booleanTypo_throws() {
        LeavePolicyConfigUpdateRequest request =
                new LeavePolicyConfigUpdateRequest("advance_leave_enabled", "ture");

        BusinessException ex = assertThrows(BusinessException.class,
                () -> leavePolicyConfigService.update(request, ACTOR_ID));

        assertEquals(ErrorCode.INVALID_CONFIG_VALUE, ex.getErrorCode());
    }

    @Test
    @DisplayName("설정 변경 — BOOLEAN 대문자는 소문자로 정규화해 저장한다 (프론트가 'true' 문자열로 비교)")
    void update_booleanUpperCase_normalized() {
        LeavePolicyConfig config = LeavePolicyConfig.create("advance_leave_enabled", "false");
        given(leavePolicyConfigRepository.findByName("advance_leave_enabled"))
                .willReturn(Optional.of(config));

        LeavePolicyConfigResponse response = leavePolicyConfigService
                .update(new LeavePolicyConfigUpdateRequest("advance_leave_enabled", "TRUE"), ACTOR_ID);

        assertEquals("true", response.value());
    }

    @Test
    @DisplayName("설정 변경 — 숫자 설정에 문자열을 넣으면 INVALID_CONFIG_VALUE")
    void update_numericWithText_throws() {
        LeavePolicyConfigUpdateRequest request =
                new LeavePolicyConfigUpdateRequest("advance_max_days", "abc");

        BusinessException ex = assertThrows(BusinessException.class,
                () -> leavePolicyConfigService.update(request, ACTOR_ID));

        assertEquals(ErrorCode.INVALID_CONFIG_VALUE, ex.getErrorCode());
    }

    @Test
    @DisplayName("설정 변경 — 상한을 넘는 값은 CONFIG_VALUE_OUT_OF_RANGE")
    void update_aboveMax_throws() {
        LeavePolicyConfigUpdateRequest request =
                new LeavePolicyConfigUpdateRequest("advance_max_days", "26.0"); // max 25.0

        BusinessException ex = assertThrows(BusinessException.class,
                () -> leavePolicyConfigService.update(request, ACTOR_ID));

        assertEquals(ErrorCode.CONFIG_VALUE_OUT_OF_RANGE, ex.getErrorCode());
    }

    @Test
    @DisplayName("설정 변경 — 하한을 밑도는 값은 CONFIG_VALUE_OUT_OF_RANGE")
    void update_belowMin_throws() {
        LeavePolicyConfigUpdateRequest request =
                new LeavePolicyConfigUpdateRequest("leave_max_dates_per_request", "0"); // min 1

        BusinessException ex = assertThrows(BusinessException.class,
                () -> leavePolicyConfigService.update(request, ACTOR_ID));

        assertEquals(ErrorCode.CONFIG_VALUE_OUT_OF_RANGE, ex.getErrorCode());
    }

    @Test
    @DisplayName("설정 변경 — 정수 설정에 소수를 넣으면 INVALID_CONFIG_VALUE")
    void update_integerWithFraction_throws() {
        LeavePolicyConfigUpdateRequest request =
                new LeavePolicyConfigUpdateRequest("leave_max_dates_per_request", "10.5");

        BusinessException ex = assertThrows(BusinessException.class,
                () -> leavePolicyConfigService.update(request, ACTOR_ID));

        assertEquals(ErrorCode.INVALID_CONFIG_VALUE, ex.getErrorCode());
    }

    @Test
    @DisplayName("설정 변경 — 정수 설정의 '30.0'은 허용한다 (숫자 입력이 소수점을 붙여 보낼 수 있다)")
    void update_integerWithTrailingZero_allowed() {
        LeavePolicyConfig config = LeavePolicyConfig.create("leave_max_dates_per_request", "30");
        given(leavePolicyConfigRepository.findByName("leave_max_dates_per_request"))
                .willReturn(Optional.of(config));

        LeavePolicyConfigResponse response = leavePolicyConfigService
                .update(new LeavePolicyConfigUpdateRequest("leave_max_dates_per_request", "20.0"), ACTOR_ID);

        assertEquals("20.0", response.value());
    }

    @Test
    @DisplayName("설정 변경 — ENUM 선택지에 없는 값은 INVALID_CONFIG_VALUE")
    void update_enumOutOfOptions_throws() {
        LeavePolicyConfigUpdateRequest request =
                new LeavePolicyConfigUpdateRequest("reminder_auto_cycle", "D45");

        BusinessException ex = assertThrows(BusinessException.class,
                () -> leavePolicyConfigService.update(request, ACTOR_ID));

        assertEquals(ErrorCode.INVALID_CONFIG_VALUE, ex.getErrorCode());
    }

    @Test
    @DisplayName("설정 변경 — 카탈로그에 있지만 행이 없는 키는 행을 만들어 갱신한다")
    void update_unseededKey_createsRow() {
        given(leavePolicyConfigRepository.findByName("advance_max_days")).willReturn(Optional.empty());
        given(leavePolicyConfigRepository.save(org.mockito.ArgumentMatchers.any(LeavePolicyConfig.class)))
                .willAnswer(invocation -> invocation.getArgument(0));

        LeavePolicyConfigResponse response = leavePolicyConfigService
                .update(new LeavePolicyConfigUpdateRequest("advance_max_days", "3.0"), ACTOR_ID);

        assertEquals("3.0", response.value());
    }
}
