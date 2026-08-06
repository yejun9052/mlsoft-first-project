package com.mlsoft.backend.domain.policy.service;

import com.mlsoft.backend.domain.policy.entity.LeavePolicyConfig;
import com.mlsoft.backend.domain.policy.entity.PolicyConfigKey;
import com.mlsoft.backend.domain.policy.repository.LeavePolicyConfigRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;

/**
 * 설정 값 읽기 — 타입별 접근자 (docs/02 3-11).
 *
 * <p>설정을 쓰는 쪽(관리자 CRUD)과 읽는 쪽(연차 신청 등 모든 도메인)을 분리한다.
 * 이전에는 {@code LeaveService}가 {@code LeavePolicyConfigRepository}를 직접 물고
 * {@code Boolean.parseBoolean}을 호출했는데, 그러면 키 상수와 파싱 방식이 읽는 서비스마다 흩어진다.
 *
 * <p><b>읽기는 절대 실패하지 않는다.</b> 값이 없거나 파싱할 수 없으면 카탈로그의 기본값으로
 * fallback하고 WARN을 남긴다. 이유:
 * <ul>
 *   <li>저장 시점 검증({@link PolicyConfigKey#validate})이 잘못된 값을 막지만, 그 검증이 생기기
 *       <b>전에 저장된 행</b>이나 DB를 직접 고쳐 넣은 값이 남아 있을 수 있다 — 그래서 읽을 때도
 *       같은 검증을 다시 통과시킨다(형식·범위 모두)</li>
 *   <li>설정 하나가 깨졌다고 사원의 연차 신청이 500으로 실패하면 피해자가 잘못된 쪽이 아니다 —
 *       관리자가 잘못 넣었는데 사원이 못 쓰게 된다</li>
 * </ul>
 * 즉 저장 시점 검증이 1차 방어선이고, 이 fallback이 2차 방어선이다.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class PolicyConfigReader {

    private final LeavePolicyConfigRepository leavePolicyConfigRepository;

    /**
     * BOOLEAN 설정 — "true"(대소문자 무시)만 true다.
     * {@code Boolean.parseBoolean}을 그대로 쓰지 않는 이유: 그 메서드는 "ture" 같은 오타를 예외 없이
     * false로 만든다. 그러면 관리자가 당겨쓰기를 켰다고 생각하는데 실제로는 꺼진 채로 돌아간다.
     */
    @Transactional(readOnly = true)
    public boolean getBoolean(PolicyConfigKey key) {
        return "true".equalsIgnoreCase(validValueOrDefault(key));
    }

    /** INTEGER 설정 — 검증을 통과한 값은 이미 정수라 소수부가 없다 */
    @Transactional(readOnly = true)
    public int getInt(PolicyConfigKey key) {
        return getDecimal(key).intValue();
    }

    /** DECIMAL 설정 — 연차 일수 계열은 전부 이쪽 (BigDecimal, docs/04) */
    @Transactional(readOnly = true)
    public BigDecimal getDecimal(PolicyConfigKey key) {
        return new BigDecimal(validValueOrDefault(key));
    }

    /** ENUM·문자열 설정 */
    @Transactional(readOnly = true)
    public String getString(PolicyConfigKey key) {
        return validValueOrDefault(key);
    }

    /**
     * 저장된 값을 카탈로그 기준으로 <b>다시 검증</b>한 뒤 준다. 하나라도 어긋나면 기본값 + WARN.
     *
     * <p>형식뿐 아니라 <b>허용 범위까지</b> 다시 본다. 파싱만 확인하면 저장 시점 검증이 없던 시절의
     * 값이 그대로 통과한다 — DB에 {@code advance_max_days="100.0"}이 남아 있으면 카탈로그 상한
     * 25.0을 무시하고 35일을 당겨쓴 신청이 승인돼, I-3 방어가 무력화된다.
     * 소수 자릿수도 같다 — {@code leave_max_dates_per_request="20.9"}를 20으로 조용히 깎는 대신
     * 기본값으로 되돌리고 로그를 남긴다.
     */
    private String validValueOrDefault(PolicyConfigKey key) {
        String stored = rawValue(key);
        try {
            String normalized = key.normalize(stored);
            key.validate(normalized);
            return normalized;
        } catch (RuntimeException e) {
            warnUnparsable(key, stored);
            return key.getDefaultValue();
        }
    }

    /** DB 행이 없으면(시딩 전·수동 삭제) 카탈로그 기본값 */
    private String rawValue(PolicyConfigKey key) {
        return leavePolicyConfigRepository.findByName(key.getKey())
                .map(LeavePolicyConfig::getValue)
                .orElse(key.getDefaultValue());
    }

    private void warnUnparsable(PolicyConfigKey key, String value) {
        log.warn("[설정] 값을 해석할 수 없어 기본값을 사용한다. key={}, 저장된 값='{}', 기본값={}",
                key.getKey(), value, key.getDefaultValue());
    }
}
