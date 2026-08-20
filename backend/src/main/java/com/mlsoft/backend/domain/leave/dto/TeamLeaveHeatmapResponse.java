package com.mlsoft.backend.domain.leave.dto;

import java.time.LocalDate;

/**
 * 팀 연차 사용 히트맵의 날짜별 인원 수.
 *
 * <p>히트맵에는 집계값만 필요하다. 이름·신청 ID·연차 사유를 응답에 넣지 않아
 * 화면 구현 실수로 개인정보가 노출될 가능성까지 차단한다.
 */
public record TeamLeaveHeatmapResponse(
        LocalDate date,
        long memberCount
) {
}
