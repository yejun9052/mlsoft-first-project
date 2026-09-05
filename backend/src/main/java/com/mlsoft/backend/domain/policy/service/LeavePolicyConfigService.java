package com.mlsoft.backend.domain.policy.service;

import com.mlsoft.backend.domain.audit.service.AdminAuditService;
import com.mlsoft.backend.domain.policy.dto.LeavePolicyConfigResponse;
import com.mlsoft.backend.domain.policy.dto.LeavePolicyConfigUpdateRequest;
import com.mlsoft.backend.domain.policy.entity.LeavePolicyConfig;
import com.mlsoft.backend.domain.policy.entity.PolicyConfigKey;
import com.mlsoft.backend.domain.email.entity.ReminderCycle;
import com.mlsoft.backend.domain.policy.repository.LeavePolicyConfigRepository;
import com.mlsoft.backend.global.exception.BusinessException;
import com.mlsoft.backend.global.exception.ErrorCode;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.Arrays;
import java.util.Map;
import java.util.function.Function;
import java.util.List;
import java.util.stream.Collectors;

/**
 * 연차 시스템 설정 관리 (GET/PUT /api/admin/configs, SA — docs/03 시스템 설정, docs/02 3-11).
 *
 * <p>설정 카탈로그는 {@link PolicyConfigKey}가 정의한다. 이 서비스는 그 카탈로그에 <b>값을 붙여</b>
 * 응답하고, 저장 전에 카탈로그의 타입·범위 검증을 통과시키는 역할만 한다.
 *
 * <p>목록은 DB 행이 아니라 <b>카탈로그를 기준으로</b> 만든다 — 아직 시딩되지 않은 키도 기본값과 함께
 * 보여야 관리자가 설정의 존재를 알 수 있다. 반대로 카탈로그에서 제거된 키의 잔존 행은 노출하지 않는다.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class LeavePolicyConfigService {

    private final LeavePolicyConfigRepository leavePolicyConfigRepository;
    private final AdminAuditService adminAuditService;

    /** 설정 목록 (GET /api/admin/configs, SA) — 카탈로그 선언 순서 */
    @Transactional(readOnly = true)
    public List<LeavePolicyConfigResponse> getAll() {
        Map<String, LeavePolicyConfig> stored = leavePolicyConfigRepository.findAllByOrderByIdAsc().stream()
                .collect(Collectors.toMap(LeavePolicyConfig::getName, Function.identity(), (first, second) -> first));
        return Arrays.stream(PolicyConfigKey.values())
                .map(key -> LeavePolicyConfigResponse.of(key, stored.get(key.getKey())))
                .toList();
    }

    /**
     * 설정 값 변경 (PUT /api/admin/configs, SA).
     * 카탈로그에 없는 키는 404, 타입·범위를 벗어난 값은 400 — <b>저장 시점에</b> 막는다.
     * 저장 시점에 막지 않으면 값을 읽는 시점(사원의 연차 신청 등)에 문제가 드러난다.
     */
    @Transactional
    public LeavePolicyConfigResponse update(LeavePolicyConfigUpdateRequest request, Long actorId) {
        PolicyConfigKey key = PolicyConfigKey.from(request.name());
        String value = key.normalize(request.value());
        key.validate(value);
        validateReminderSettings(key, value);

        // 카탈로그에 있는 키인데 행이 없으면(신규 설정 추가 직후) 여기서 만든다
        LeavePolicyConfig config = leavePolicyConfigRepository.findByName(key.getKey())
                .orElseGet(() -> leavePolicyConfigRepository.save(
                        LeavePolicyConfig.create(key.getKey(), key.getDefaultValue())));
        // 아직 시딩 전이면 직전 값은 기본값이다 — 위에서 그 값으로 행을 만들었으므로 그대로 읽는다
        String before = config.getValue();
        config.updateValue(value);
        // 당겨쓰기 허용·상한처럼 전 사원의 신청 결과를 바꾸는 값이라 누가 언제 바꿨는지가 남아야 한다 (리뷰 S-3)
        adminAuditService.recordConfigChange(actorId, key.getKey(), before, value);
        log.info("[설정 변경] key={}, value={}, actorId={}", key.getKey(), value, actorId);
        return LeavePolicyConfigResponse.of(key, config);
    }

    /**
     * 자동 주기와 대상 목록 범위의 교차 검증.
     * 개별 카탈로그 값만 보는 {@link PolicyConfigKey#validate(String)}의 책임을 넘지 않도록
     * 두 값을 함께 저장하는 서비스에서 검증한다.
     */
    private void validateReminderSettings(PolicyConfigKey changedKey, String changedValue) {
        if (changedKey != PolicyConfigKey.REMINDER_AUTO_CYCLE
                && changedKey != PolicyConfigKey.REMINDER_LIST_DAYS) {
            return;
        }
        String cycleValue = changedKey == PolicyConfigKey.REMINDER_AUTO_CYCLE
                ? changedValue
                : storedValue(PolicyConfigKey.REMINDER_AUTO_CYCLE);
        String listDaysValue = changedKey == PolicyConfigKey.REMINDER_LIST_DAYS
                ? changedValue
                : storedValue(PolicyConfigKey.REMINDER_LIST_DAYS);

        ReminderCycle.fromConfig(cycleValue).ifPresent(cycle -> {
            int listDays;
            try {
                listDays = new BigDecimal(listDaysValue).intValueExact();
            } catch (ArithmeticException | NumberFormatException e) {
                throw new BusinessException(ErrorCode.INVALID_CONFIG_VALUE);
            }
            if (listDays < cycle.rangeDays()) {
                throw new BusinessException(ErrorCode.REMINDER_LIST_DAYS_TOO_SHORT);
            }
        });
    }

    /** 상대 설정이 아직 시딩되지 않았으면 카탈로그 기본값을 쓴다. */
    private String storedValue(PolicyConfigKey key) {
        return leavePolicyConfigRepository.findByName(key.getKey())
                .map(LeavePolicyConfig::getValue)
                .orElse(key.getDefaultValue());
    }
}
