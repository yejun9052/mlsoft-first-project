package com.mlsoft.backend.domain.policy.entity;

import com.mlsoft.backend.global.exception.BusinessException;
import com.mlsoft.backend.global.exception.ErrorCode;
import lombok.Getter;

import java.math.BigDecimal;
import java.util.Arrays;
import java.util.List;
import java.util.Optional;

/**
 * 연차 시스템 설정 카탈로그 — 키·타입·기본값·허용 범위·화면 메타데이터의 <b>단일 정의처</b>
 * (docs/02 3-11 leave_policy_config, docs/03 시스템 설정).
 *
 * <p>이전에는 정의가 세 곳에 흩어져 있었다 — 키 문자열과 기본값은 {@code DataInitializer}, 읽는 쪽의
 * 키 상수는 각 서비스, 라벨·타입·설명은 프론트의 {@code CONFIG_META}. 그래서 설정을 하나 추가하면
 * 세 곳을 같이 고쳐야 했고, 프론트를 빼먹으면 {@code AdminPolicyPage}의 필터가 그 설정을
 * <b>조용히 숨겼다</b>(관리자 화면에 아예 안 보임).
 *
 * <p>여기에 모으면 설정 추가는 이 enum에 상수 한 줄을 넣는 것으로 끝난다 —
 * 시딩({@code DataInitializer})·검증(저장 시점)·화면 렌더(메타데이터 응답)가 모두 여기서 파생된다.
 *
 * <p><b>새 설정을 추가할 때</b>: 그 값을 읽어 동작하는 코드가 없으면 반드시
 * {@link PolicyConfigStatus#PENDING_FEATURE}로 두어라. 관리자 화면이 "미동작"으로 구분해 보여준다.
 */
@Getter
public enum PolicyConfigKey {

    // ── 당겨쓰기 (갭분석 A-3, docs/02 메모 11, 리뷰 I-3) ──────────────────────

    ADVANCE_LEAVE_ENABLED(
            "advance_leave_enabled", "false",
            "연차 당겨쓰기 허용",
            "잔여가 부족해도 신청을 접수하고, 부족분을 다음 기산일의 새 연차에서 차감한다.",
            PolicyConfigStatus.ACTIVE),

    ADVANCE_MAX_DAYS(
            "advance_max_days", ConfigValueType.DECIMAL, "5.0",
            BigDecimal.ZERO, new BigDecimal("25.0"), "일",
            "당겨쓰기 상한",
            "한 사원이 당겨쓸 수 있는 최대 일수. 초과하는 신청은 거부된다. "
                    + "상한이 없으면 다음 기산일에 연차가 음수가 되어 관리자가 직접 고치기 전까지 신청이 불가능해진다.",
            PolicyConfigStatus.ACTIVE),

    // ── 신청 입력 경계 (리뷰 I-3) ────────────────────────────────────────────

    LEAVE_MAX_DATES_PER_REQUEST(
            "leave_max_dates_per_request", ConfigValueType.INTEGER, "30",
            BigDecimal.ONE, new BigDecimal("366"), "일",
            "신청 1건당 최대 날짜 수",
            "연차 신청 한 건에 담을 수 있는 날짜 개수. 실수·악의로 대량 신청이 접수되는 것을 막는다.",
            PolicyConfigStatus.ACTIVE),

    NEXT_CYCLE_RESERVATION_ENABLED(
            "next_cycle_reservation_enabled", "true",
            "다음 회차 연차 예약 허용",
            "다음 기산일 이후 날짜의 연차 신청을 허용한다. 끄면 현재 회차 안에서만 신청할 수 있다.",
            PolicyConfigStatus.ACTIVE),

    // ── 기산일 리셋·월차 (docs/09 스케줄러) ──────────────────────────────────

    BONUS_CARRY_OVER_ENABLED(
            "bonus_carry_over_enabled", "false",
            "보너스 연차 이월",
            "기산일 리셋 때 남은 복리후생 가산분을 다음 연도로 이월한다. (docs/02 메모 10)",
            PolicyConfigStatus.ACTIVE),

    // 상한을 12가 아니라 11로 둔다 — 12회차 지급일은 정확히 1주년인데 그날은 기산일 리셋이 정책 연차로
    // 갈아 끼우므로 영원히 지급되지 않는다. 설정에 남겨 두면 "12로 올렸는데 아무 일도 안 일어나는" 값이 된다
    MONTHLY_LEAVE_MAX_DAYS(
            "monthly_leave_max_days", ConfigValueType.INTEGER, "11",
            BigDecimal.ZERO, new BigDecimal("11"), "일",
            "1년 미만 월차 적립 상한",
            "입사 1년이 안 된 사원에게 매월 1일씩 적립할 최대 일수. 근로기준법 기준은 11일이다. (갭분석 B-1)",
            PolicyConfigStatus.ACTIVE),

    // ── 온보딩 (리뷰 S-1) ────────────────────────────────────────────────────

    ONBOARDING_AUTO_APPROVE_DAYS(
            "onboarding_auto_approve_days", ConfigValueType.INTEGER, "90",
            BigDecimal.ZERO, new BigDecimal("3650"), "일",
            "온보딩 자동 승인 기간",
            "오늘로부터 이 기간 안의 입사일이면 온보딩이 바로 확정된다. 그보다 과거를 입력하면 "
                    + "관리자 승인 대기로 넘어가고 승인 전까지 연차가 부여되지 않는다. "
                    + "0으로 두면 모든 온보딩이 승인을 거친다.",
            PolicyConfigStatus.ACTIVE),

    ONBOARDING_REVISION_ENABLED(
            "onboarding_revision_enabled", "true",
            "승인 대기 중 입사일 수정 허용",
            "관리자 승인을 기다리는 사원이 신고한 입사일·생일을 1회에 한해 스스로 고칠 수 있게 한다. "
                    + "끄면 잘못 입력한 사원은 관리자 반려를 기다려야 한다.",
            PolicyConfigStatus.ACTIVE),

    // ── 소진 안내 메일 (docs/01 2-8) ─────────────────────────────────────────

    REMINDER_LIST_DAYS(
            "reminder_list_days", ConfigValueType.INTEGER, "30",
            BigDecimal.ZERO, new BigDecimal("365"), "일",
            "소진 안내 기준일",
            "기산일 N일 전부터 연차 소진 안내 대상 목록에 표시한다.",
            PolicyConfigStatus.ACTIVE),

    REMINDER_AUTO_CYCLE(
            "reminder_auto_cycle", "NONE",
            List.of("NONE", "D30", "D60", "D90", "QUARTER"),
            "자동 발송 주기",
            "기산일이 임박한 사원에게 안내 메일을 자동 발송하는 주기. NONE이면 발송하지 않는다.",
            PolicyConfigStatus.ACTIVE),

    // ── 퇴직자 데이터 파기 P1 (설계-초안 §2·§8) ────────────────────────────────

    RETIREE_PURGE_MODE(
            "retiree_purge_mode", "MANUAL",
            List.of("MANUAL", "AUTO"),
            "퇴직자 데이터 파기 모드",
            "퇴직자 데이터 파기 방식을 정한다. MANUAL은 관리자가 직접 실행하고 AUTO는 보존 기간이 지난 대상을 자동 파기한다.",
            PolicyConfigStatus.PENDING_FEATURE),

    RETIREE_PURGE_YEARS(
            "retiree_purge_years", ConfigValueType.INTEGER, "3",
            BigDecimal.valueOf(3), BigDecimal.TEN, "년",
            "퇴직자 데이터 보존 기간",
            "AUTO 모드에서 퇴직자 데이터를 파기하기 전 보존할 기간이다. 자동 모드에서만 쓰인다. 하한 3년은 근로기준법상 보존기간을 지키기 위한 값이므로 3년 미만으로 낮출 수 없다.",
            PolicyConfigStatus.PENDING_FEATURE,
            new VisibilityCondition("retiree_purge_mode", "AUTO"));

    /** DB `leave_policy_config.name`에 저장되는 키 */
    private final String key;
    private final ConfigValueType type;
    /** 시딩 기본값 — 값 파싱에 실패했을 때의 fallback으로도 쓰인다 */
    private final String defaultValue;
    /** INTEGER·DECIMAL 하한 (포함). 그 외 타입은 null */
    private final BigDecimal min;
    /** INTEGER·DECIMAL 상한 (포함). 그 외 타입은 null */
    private final BigDecimal max;
    /** 화면에 값과 함께 표시할 단위 (없으면 null) */
    private final String unit;
    /** ENUM 선택지. 그 외 타입은 빈 리스트 */
    private final List<String> options;
    private final String label;
    private final String description;
    private final PolicyConfigStatus status;
    /**
     * 조건을 만족할 때만 화면에 표시하는 선택적 의존 메타데이터. null이면 항상 표시한다.
     * 화면 노출 힌트일 뿐 저장 검증 조건이 아니다 — MANUAL일 때도 값을 저장·검증해
     * AUTO로 다시 전환할 때 설정값이 살아 있도록 한다.
     */
    private final VisibilityCondition visibleWhen;

    /** 조건부 노출 조건 — 의존 설정이 requiredValue일 때만 해당 설정을 표시한다. */
    public record VisibilityCondition(String dependsOnKey, String requiredValue) {
    }

    /** BOOLEAN 설정 */
    PolicyConfigKey(String key, String defaultValue, String label, String description, PolicyConfigStatus status) {
        this(key, ConfigValueType.BOOLEAN, defaultValue, null, null, null, List.of(), label, description, status, null);
    }

    /** ENUM 설정 */
    PolicyConfigKey(String key, String defaultValue, List<String> options,
                    String label, String description, PolicyConfigStatus status) {
        this(key, ConfigValueType.ENUM, defaultValue, null, null, null, options, label, description, status, null);
    }

    /** INTEGER·DECIMAL 설정 */
    PolicyConfigKey(String key, ConfigValueType type, String defaultValue,
                    BigDecimal min, BigDecimal max, String unit,
                    String label, String description, PolicyConfigStatus status) {
        this(key, type, defaultValue, min, max, unit, List.of(), label, description, status, null);
    }

    /** INTEGER·DECIMAL 설정 — 조건부 노출 메타데이터 포함 */
    PolicyConfigKey(String key, ConfigValueType type, String defaultValue,
                    BigDecimal min, BigDecimal max, String unit,
                    String label, String description, PolicyConfigStatus status,
                    VisibilityCondition visibleWhen) {
        this(key, type, defaultValue, min, max, unit, List.of(), label, description, status, visibleWhen);
    }

    PolicyConfigKey(String key, ConfigValueType type, String defaultValue,
                    BigDecimal min, BigDecimal max, String unit, List<String> options,
                    String label, String description, PolicyConfigStatus status,
                    VisibilityCondition visibleWhen) {
        this.key = key;
        this.type = type;
        this.defaultValue = defaultValue;
        this.min = min;
        this.max = max;
        this.unit = unit;
        this.options = options;
        this.label = label;
        this.description = description;
        this.status = status;
        this.visibleWhen = visibleWhen;
    }

    /** 키 문자열 → 카탈로그 항목. 카탈로그에 없는 키는 갱신 대상이 아니다(404) */
    public static PolicyConfigKey from(String key) {
        return findByKey(key).orElseThrow(() -> new BusinessException(ErrorCode.LEAVE_POLICY_CONFIG_NOT_FOUND));
    }

    /**
     * 키 문자열 → 카탈로그 항목 (없으면 empty).
     * 카탈로그에서 제거된 키의 DB 행이 남아 있어도 목록 조회가 실패하지 않도록 Optional로 준다.
     */
    public static Optional<PolicyConfigKey> findByKey(String key) {
        return Arrays.stream(values()).filter(candidate -> candidate.key.equals(key)).findFirst();
    }

    /**
     * 저장 전 값 검증 — 타입·범위·선택지를 모두 본다.
     *
     * <p>여기서 막지 못한 값은 나중에 <b>값을 읽는 쪽에서</b> 터진다. 그 시점의 피해자는 설정을
     * 잘못 넣은 관리자가 아니라 연차를 신청하려는 사원이다(500). 그래서 저장 시점 검증이 필수다.
     *
     * @throws BusinessException 형식이 틀리면 INVALID_CONFIG_VALUE, 범위를 벗어나면 CONFIG_VALUE_OUT_OF_RANGE
     */
    public void validate(String value) {
        switch (type) {
            case BOOLEAN -> {
                if (!"true".equalsIgnoreCase(value) && !"false".equalsIgnoreCase(value)) {
                    throw new BusinessException(ErrorCode.INVALID_CONFIG_VALUE);
                }
            }
            case ENUM -> {
                if (!options.contains(value)) {
                    throw new BusinessException(ErrorCode.INVALID_CONFIG_VALUE);
                }
            }
            case INTEGER -> validateRange(parseNumber(value, 0));
            case DECIMAL -> validateRange(parseNumber(value, 1));
        }
    }

    /**
     * 저장 형태로 정규화 — 공백 제거, BOOLEAN은 소문자로 통일.
     * 프론트가 {@code value === 'true'}로 비교하므로 "TRUE"가 저장되면 토글이 꺼진 것처럼 보인다.
     */
    public String normalize(String value) {
        String trimmed = value.trim();
        return (type == ConfigValueType.BOOLEAN) ? trimmed.toLowerCase() : trimmed;
    }

    /** 숫자 파싱 — 허용 소수 자릿수(maxScale)를 넘으면 형식 오류로 본다 */
    private BigDecimal parseNumber(String value, int maxScale) {
        BigDecimal parsed;
        try {
            parsed = new BigDecimal(value.trim());
        } catch (NumberFormatException e) {
            throw new BusinessException(ErrorCode.INVALID_CONFIG_VALUE);
        }
        // "5.0"은 정수 설정에도 허용한다 — 프론트 숫자 입력이 소수점을 붙여 보낼 수 있다
        if (parsed.stripTrailingZeros().scale() > maxScale) {
            throw new BusinessException(ErrorCode.INVALID_CONFIG_VALUE);
        }
        return parsed;
    }

    private void validateRange(BigDecimal parsed) {
        if ((min != null && parsed.compareTo(min) < 0) || (max != null && parsed.compareTo(max) > 0)) {
            throw new BusinessException(ErrorCode.CONFIG_VALUE_OUT_OF_RANGE);
        }
    }
}
