package com.mlsoft.backend.domain.user.entity;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

/**
 * 온보딩 진행 상태 (리뷰 S-1).
 *
 * <p>예전에는 {@code hire_date != null} 하나로 "온보딩 완료"를 판별했다. 그런데 입사일이
 * <b>자가 신고</b>라, 신입이 {@code 1990-01-01}을 넣으면 {@code MIN(15 + 35/2, 25)} = 25일이
 * 그 자리에서 부여됐다. 되돌리려면 관리자가 DB를 직접 고쳐야 했다 — 완료로 판정된 뒤에는
 * 재입력 경로가 없기 때문이다.
 *
 * <p>그래서 "입사일을 적었다"와 "그 입사일이 확정됐다"를 분리한다. 확정 전에는
 * {@code hire_date}가 채워져 있어도 연차가 0이고, 인터셉터가 {@code /api/auth/*} 밖을 막는다.
 * 스케줄러 3잡도 {@link #COMPLETED}만 대상으로 삼는다 — 미확정 입사일이 월차·리셋·생일 반차
 * 세 잡의 입력이 되면 안 된다.
 */
@Getter
@RequiredArgsConstructor
public enum OnboardingStatus {

    /** 아직 입사일·생일을 입력하지 않음 (가입 직후) */
    NOT_STARTED("미시작"),

    /**
     * 입력했으나 자동 승인 범위({@code onboarding_auto_approve_days})를 벗어나 관리자 승인 대기.
     * 입사일은 저장되지만 연차는 0이고, 승인 시점에 정책 연차가 산정된다.
     */
    PENDING_APPROVAL("승인 대기"),

    /** 확정 — 연차가 부여됐고 시스템을 정상 이용할 수 있다 */
    COMPLETED("완료");

    private final String label;
}
