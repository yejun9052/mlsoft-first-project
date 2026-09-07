package com.mlsoft.backend.domain.holiday.dto;

import com.mlsoft.backend.domain.holiday.client.HolidayApiClient;
import com.mlsoft.backend.global.exception.BusinessException;
import com.mlsoft.backend.global.exception.ErrorCode;

/** 공휴일 동기화 내부 결과 — 외부 조회 outcome과 적재 건수를 함께 전달한다. */
public record HolidaySyncResult(
        HolidayApiClient.Outcome outcome,
        int count
) {

    /**
     * 관리자 경로(수동 동기화·키 검증)에서 실패 outcome을 정해진 오류로 표면화한다.
     *
     * <p><b>outcome → ErrorCode 매핑의 단일 출처다.</b> 호출부마다 switch를 두면 outcome을
     * 늘렸을 때 한쪽만 고쳐져 같은 장애가 경로마다 다른 메시지로 보인다.
     * 자동 경로(캐시 미스·새해 스케줄러)는 이 메서드를 부르지 않고 조용히 degrade한다.
     */
    public static void requireOk(HolidayApiClient.Outcome outcome) {
        if (outcome == HolidayApiClient.Outcome.OK) {
            return;
        }
        // 목·대체 구현이 null을 주는 경우까지 장애로 본다
        if (outcome == null) {
            throw new BusinessException(ErrorCode.HOLIDAY_API_BAD_RESPONSE);
        }
        throw new BusinessException(switch (outcome) {
            case NO_KEY -> ErrorCode.HOLIDAY_API_KEY_NOT_CONFIGURED;
            case CALL_FAILED -> ErrorCode.HOLIDAY_API_CALL_FAILED;
            // OK는 위에서 걸러졌다 — switch 완전성 때문에 남긴다
            case OK, BAD_RESPONSE -> ErrorCode.HOLIDAY_API_BAD_RESPONSE;
        });
    }

    /** 이 결과가 실패면 같은 매핑으로 예외를 던진다. */
    public void requireOk() {
        requireOk(outcome);
    }
}
