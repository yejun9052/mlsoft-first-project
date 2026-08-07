package com.mlsoft.backend.domain.schedule.dto;

import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.time.LocalDate;
import java.util.List;

/**
 * 개인 일정 등록·수정 요청 (POST/PUT /api/schedules).
 * <p>
 * 등록자는 body가 아니라 {@code @AuthenticationPrincipal AuthUser}에서 잡는다 (docs/04).
 * 날짜 개수 상한은 연차와 같은 이유로 절대 가드를 둔다 — 비정상 페이로드가 DB 조회까지 가지 않게
 * (리뷰 I-3에서 연차에 넣은 것과 같은 패턴).
 */
public record ScheduleCreateRequest(

        @NotNull(message = "일정 종류를 선택해 주세요.")
        String scheduleType,

        @NotEmpty(message = "날짜를 하나 이상 선택해 주세요.")
        @Size(max = 366, message = "한 번에 등록할 수 있는 날짜 수를 초과했습니다.")
        List<LocalDate> dates,

        @Size(max = 500, message = "메모는 500자를 넘을 수 없습니다.")
        String memo
) {
}
