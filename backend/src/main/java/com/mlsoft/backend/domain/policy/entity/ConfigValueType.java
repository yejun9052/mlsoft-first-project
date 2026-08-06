package com.mlsoft.backend.domain.policy.entity;

/**
 * 설정 값 타입 (docs/02 3-11 leave_policy_config).
 *
 * <p>`leave_policy_config.value`는 DB에 문자열로 저장된다. 타입을 값과 함께 두지 않으면
 * 읽는 쪽이 각자 파싱하게 되고, 관리자가 저장한 잘못된 값이 **읽는 시점에** 터진다
 * (관리자가 `advance_max_days`에 "abc"를 넣으면 몇 시간 뒤 사원의 연차 신청이 500으로 실패).
 * 그래서 타입을 {@link PolicyConfigKey}에 선언하고 **저장 시점에** 검증한다.
 *
 * <p>화면 컨트롤 종류도 이 타입에서 파생된다 — BOOLEAN=토글, INTEGER/DECIMAL=숫자 입력,
 * ENUM=드롭다운. 프론트가 키 이름을 하드코딩하지 않아도 되게 하는 것이 목적이다.
 */
public enum ConfigValueType {

    /** true / false 두 값만 (대소문자 무시) */
    BOOLEAN,

    /** 정수 — min/max 범위 검증 */
    INTEGER,

    /** 소수 첫째 자리까지 (연차 일수 도메인은 DECIMAL(4,1)) — min/max 범위 검증 */
    DECIMAL,

    /** 미리 정의된 선택지 중 하나 (options) */
    ENUM
}
