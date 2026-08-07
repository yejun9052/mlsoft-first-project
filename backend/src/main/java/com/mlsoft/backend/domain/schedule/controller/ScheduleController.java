package com.mlsoft.backend.domain.schedule.controller;

import com.mlsoft.backend.domain.schedule.dto.ScheduleCreateRequest;
import com.mlsoft.backend.domain.schedule.dto.ScheduleResponse;
import com.mlsoft.backend.domain.schedule.entity.ScheduleType;
import com.mlsoft.backend.domain.schedule.service.ScheduleService;
import com.mlsoft.backend.global.response.CommonResponse;
import com.mlsoft.backend.global.response.ResponseMessage;
import com.mlsoft.backend.security.AuthUser;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.Arrays;
import java.util.List;

/**
 * 개인 일정 API — 외근·출장·재택·교육 (연차 차감 없음, 결재 없음).
 * <p>
 * 역할 게이트를 두지 않는다. 본인 일정을 본인이 등록·삭제하는 것이고, 조회는 캘린더가
 * 이미 전사 공개이기 때문이다. 소유권 검증은 서비스 계층 (docs/04).
 */
@RestController
@RequestMapping("/api/schedules")
@RequiredArgsConstructor
public class ScheduleController {

    private final ScheduleService scheduleService;

    /** 일정 등록 — 승인 없이 즉시 확정 */
    @PostMapping
    public ResponseEntity<CommonResponse<ScheduleResponse>> create(
            @AuthenticationPrincipal AuthUser authUser,
            @Valid @RequestBody ScheduleCreateRequest request
    ) {
        ScheduleResponse response = scheduleService.create(authUser.id(), request);
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(CommonResponse.success(ResponseMessage.SCHEDULE_CREATED, response));
    }

    /** 일정 수정 — 본인만 */
    @PutMapping("/{id}")
    public ResponseEntity<CommonResponse<ScheduleResponse>> update(
            @AuthenticationPrincipal AuthUser authUser,
            @PathVariable Long id,
            @Valid @RequestBody ScheduleCreateRequest request
    ) {
        ScheduleResponse response = scheduleService.update(id, authUser.id(), request);
        return ResponseEntity.ok(CommonResponse.success(ResponseMessage.SCHEDULE_UPDATED, response));
    }

    /** 일정 삭제 — 본인만 */
    @DeleteMapping("/{id}")
    public ResponseEntity<CommonResponse<Void>> delete(
            @AuthenticationPrincipal AuthUser authUser,
            @PathVariable Long id
    ) {
        scheduleService.delete(id, authUser.id());
        return ResponseEntity.ok(CommonResponse.success(ResponseMessage.SCHEDULE_DELETED, null));
    }

    /**
     * 캘린더 — 전 직원 일정. keyword(이름 부분일치)·departmentId로 좁힐 수 있다.
     * 둘 다 선택이며, 없으면 해당 월 전체를 돌려준다.
     */
    @GetMapping("/calendar")
    public ResponseEntity<CommonResponse<List<ScheduleResponse>>> getCalendar(
            @AuthenticationPrincipal AuthUser authUser,
            @RequestParam int year,
            @RequestParam int month,
            @RequestParam(required = false) String keyword,
            @RequestParam(required = false) Long departmentId
    ) {
        List<ScheduleResponse> response =
                scheduleService.getCalendar(authUser.id(), year, month, keyword, departmentId);
        return ResponseEntity.ok(CommonResponse.success(ResponseMessage.SCHEDULE_FETCHED, response));
    }

    /** 내 일정 목록 */
    @GetMapping("/me")
    public ResponseEntity<CommonResponse<Page<ScheduleResponse>>> getMySchedules(
            @AuthenticationPrincipal AuthUser authUser,
            Pageable pageable
    ) {
        Page<ScheduleResponse> response = scheduleService.getMySchedules(authUser.id(), pageable);
        return ResponseEntity.ok(CommonResponse.success(ResponseMessage.SCHEDULE_FETCHED, response));
    }

    /**
     * 선택 가능한 일정 종류 — 값과 한글 라벨을 함께 내린다.
     * 프론트가 종류를 하드코딩하지 않게 하기 위한 것으로, 종류를 추가하면 화면이 자동으로 따라온다
     * (관리자 설정 카탈로그가 메타데이터를 함께 내리는 것과 같은 의도 — 리뷰 I-3).
     */
    @GetMapping("/types")
    public ResponseEntity<CommonResponse<List<ScheduleTypeOption>>> getTypes() {
        List<ScheduleTypeOption> options = Arrays.stream(ScheduleType.values())
                .map(type -> new ScheduleTypeOption(type.name(), type.getLabel()))
                .toList();
        return ResponseEntity.ok(CommonResponse.success(ResponseMessage.SCHEDULE_FETCHED, options));
    }

    /** 일정 종류 선택지 */
    public record ScheduleTypeOption(String value, String label) {
    }
}
