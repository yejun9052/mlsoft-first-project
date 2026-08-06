package com.mlsoft.backend.domain.policy.entity;

/**
 * 설정이 실제로 동작하는지 여부.
 *
 * <p>관리자 화면에 노출된 설정 중 일부는 그 기능이 아직 구현되지 않았다 — 소진 안내 관련 2키는
 * 이메일 발송(`domain/email`에 entity·repository만 있음)이 없어 값을 바꿔도 아무 일도 일어나지 않는다.
 * 그런데 화면에는 정상 설정처럼 보였다 (docs/11 "⚠️ 반쪽" 참조).
 *
 * <p>설정을 늘리면 이 문제도 같이 커지므로, 동작 여부를 카탈로그에 명시해 화면이 구분해 보여준다.
 * 기능이 구현되면 {@code ACTIVE}로 바꾸는 것이 그 작업의 마지막 단계다.
 */
public enum PolicyConfigStatus {

    /** 지금 코드가 이 값을 읽어 동작한다 */
    ACTIVE,

    /** 값은 저장되지만 이를 읽는 기능이 아직 없다 — 화면에 "미동작"으로 표시 */
    PENDING_FEATURE
}
