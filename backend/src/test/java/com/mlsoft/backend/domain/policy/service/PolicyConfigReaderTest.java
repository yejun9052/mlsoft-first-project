package com.mlsoft.backend.domain.policy.service;

import com.mlsoft.backend.domain.policy.entity.LeavePolicyConfig;
import com.mlsoft.backend.domain.policy.entity.PolicyConfigKey;
import com.mlsoft.backend.domain.policy.repository.LeavePolicyConfigRepository;
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
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.BDDMockito.given;

/**
 * 설정 읽기 단위 테스트 — <b>읽기는 절대 실패하지 않는다</b>는 계약을 검증한다.
 *
 * 저장 시점 검증({@link PolicyConfigKey#validate})이 1차 방어선이지만, 그 검증이 생기기 전에
 * 저장된 행이 DB에 남아 있을 수 있다. 그때 예외를 던지면 <b>사원의 연차 신청이 500으로 실패</b>한다 —
 * 잘못 넣은 관리자가 아니라 사원이 피해를 본다. 그래서 기본값으로 fallback한다.
 */
@ExtendWith(MockitoExtension.class)
class PolicyConfigReaderTest {

    @Mock
    private LeavePolicyConfigRepository leavePolicyConfigRepository;

    @InjectMocks
    private PolicyConfigReader policyConfigReader;

    @Test
    @DisplayName("BOOLEAN — 저장된 값을 읽는다 (대소문자 무시)")
    void getBoolean_readsStoredValue() {
        givenStored(PolicyConfigKey.ADVANCE_LEAVE_ENABLED, "TRUE");

        assertTrue(policyConfigReader.getBoolean(PolicyConfigKey.ADVANCE_LEAVE_ENABLED));
    }

    @Test
    @DisplayName("BOOLEAN — 오타는 기본값으로 fallback한다 (parseBoolean처럼 조용히 false가 되지 않게)")
    void getBoolean_typo_fallsBackToDefault() {
        givenStored(PolicyConfigKey.ADVANCE_LEAVE_ENABLED, "ture");

        // 기본값이 false라 결과는 false지만, 판단 근거가 "오타 → 기본값"이라는 점이 다르다.
        // 기본값이 true인 설정이라면 오타 때문에 기능이 꺼지지 않는다.
        assertFalse(policyConfigReader.getBoolean(PolicyConfigKey.ADVANCE_LEAVE_ENABLED));
    }

    @Test
    @DisplayName("DECIMAL — 숫자가 아니면 기본값으로 fallback하고 예외를 던지지 않는다")
    void getDecimal_unparsable_fallsBackWithoutThrowing() {
        givenStored(PolicyConfigKey.ADVANCE_MAX_DAYS, "abc");

        BigDecimal value = policyConfigReader.getDecimal(PolicyConfigKey.ADVANCE_MAX_DAYS);

        assertEquals(0, new BigDecimal(PolicyConfigKey.ADVANCE_MAX_DAYS.getDefaultValue()).compareTo(value));
    }

    @Test
    @DisplayName("DECIMAL — DB 행이 아예 없으면 카탈로그 기본값 (시딩 전에도 동작해야 한다)")
    void getDecimal_missingRow_usesCatalogDefault() {
        given(leavePolicyConfigRepository.findByName(PolicyConfigKey.ADVANCE_MAX_DAYS.getKey()))
                .willReturn(Optional.empty());

        BigDecimal value = policyConfigReader.getDecimal(PolicyConfigKey.ADVANCE_MAX_DAYS);

        assertEquals(0, new BigDecimal("5.0").compareTo(value));
    }

    @Test
    @DisplayName("INTEGER — 소수가 저장돼 있으면 조용히 깎지 않고 기본값으로 되돌린다")
    void getInt_storedFraction_fallsBackToDefault() {
        givenStored(PolicyConfigKey.LEAVE_MAX_DATES_PER_REQUEST, "20.9");

        // 20으로 깎으면 관리자가 넣지 않은 값이 정책이 된다 — 기본값 + WARN이 옳다
        assertEquals(30, policyConfigReader.getInt(PolicyConfigKey.LEAVE_MAX_DATES_PER_REQUEST));
    }

    @Test
    @DisplayName("숫자 — 허용 범위를 벗어난 옛 값은 기본값으로 되돌린다 (상한 무력화 차단)")
    void getDecimal_outOfRange_fallsBackToDefault() {
        // 저장 시점 검증이 없던 시절의 값. 그대로 읽으면 카탈로그 상한 25.0을 넘겨 I-3 방어가 뚫린다
        givenStored(PolicyConfigKey.ADVANCE_MAX_DAYS, "100.0");

        BigDecimal value = policyConfigReader.getDecimal(PolicyConfigKey.ADVANCE_MAX_DAYS);

        assertEquals(0, new BigDecimal("5.0").compareTo(value));
    }

    @Test
    @DisplayName("ENUM — 선택지에 없는 값은 기본값으로 되돌린다")
    void getString_unknownOption_fallsBackToDefault() {
        givenStored(PolicyConfigKey.REMINDER_AUTO_CYCLE, "D45");

        assertEquals("NONE", policyConfigReader.getString(PolicyConfigKey.REMINDER_AUTO_CYCLE));
    }

    @Test
    @DisplayName("INTEGER — 정상 범위 값은 그대로 읽는다")
    void getInt_validValue_readsAsIs() {
        givenStored(PolicyConfigKey.LEAVE_MAX_DATES_PER_REQUEST, "10");

        assertEquals(10, policyConfigReader.getInt(PolicyConfigKey.LEAVE_MAX_DATES_PER_REQUEST));
    }

    @Test
    @DisplayName("ENUM·문자열 — 빈 값이면 기본값")
    void getString_blank_usesDefault() {
        givenStored(PolicyConfigKey.REMINDER_AUTO_CYCLE, "   ");

        assertEquals("NONE", policyConfigReader.getString(PolicyConfigKey.REMINDER_AUTO_CYCLE));
    }

    private void givenStored(PolicyConfigKey key, String value) {
        given(leavePolicyConfigRepository.findByName(key.getKey()))
                .willReturn(Optional.of(LeavePolicyConfig.create(key.getKey(), value)));
    }
}
