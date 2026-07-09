package com.mlsoft.backend.domain.leave.controller;

import com.mlsoft.backend.domain.common.RequestStatus;
import com.mlsoft.backend.domain.leave.dto.ApprovalRequest;
import com.mlsoft.backend.domain.leave.dto.CancelRequest;
import com.mlsoft.backend.domain.leave.dto.LeaveCalendarResponse;
import com.mlsoft.backend.domain.leave.dto.LeaveCreateRequest;
import com.mlsoft.backend.domain.leave.dto.LeaveHistoryResponse;
import com.mlsoft.backend.domain.leave.dto.LeaveResponse;
import com.mlsoft.backend.domain.leave.dto.LeaveSummaryResponse;
import com.mlsoft.backend.domain.leave.service.LeaveService;
import com.mlsoft.backend.global.response.CommonResponse;
import com.mlsoft.backend.global.response.ResponseMessage;
import com.mlsoft.backend.security.AuthUser;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDate;
import java.util.List;

/**
 * 연차 API (docs/03 연차 섹션).
 * - 본인 식별은 Authentication(AuthUser)에서 추출 — 요청 body의 사용자 ID 신뢰 금지 (docs/04)
 * - 온보딩/퇴직/권한 신선도는 OnboardingCheckInterceptor가 /api/** 전역 가드
 * - 소유/승인자 식별 검증은 서비스 계층에서 (@PreAuthorize는 역할 게이트만)
 */
@RestController
@RequestMapping("/api/leaves")
@RequiredArgsConstructor
public class LeaveController {

    private final LeaveService leaveService;

    /** 연차 신청 — 선차감, 주말·과거·중복·잔여 검증 */
    @PostMapping
    public ResponseEntity<CommonResponse<LeaveResponse>> apply(
            @AuthenticationPrincipal AuthUser authUser,
            @Valid @RequestBody LeaveCreateRequest request
    ) {
        LeaveResponse response = leaveService.apply(authUser.id(), request);
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(CommonResponse.success(ResponseMessage.LEAVE_CREATED, response));
    }

    /** 내 신청 내역 (페이징, status 필터) */
    @GetMapping("/me")
    public ResponseEntity<CommonResponse<Page<LeaveResponse>>> getMyLeaves(
            @AuthenticationPrincipal AuthUser authUser,
            @RequestParam(required = false) RequestStatus status,
            Pageable pageable
    ) {
        Page<LeaveResponse> response = leaveService.getMyLeaves(authUser.id(), status, pageable);
        return ResponseEntity.ok(CommonResponse.success(ResponseMessage.LEAVE_FETCHED, response));
    }

    /** 잔여 현황 (총·사용·잔여·대기·당겨쓰기·다음 기산일) */
    @GetMapping("/me/summary")
    public ResponseEntity<CommonResponse<LeaveSummaryResponse>> getMySummary(
            @AuthenticationPrincipal AuthUser authUser
    ) {
        LeaveSummaryResponse response = leaveService.getMySummary(authUser.id());
        return ResponseEntity.ok(CommonResponse.success(ResponseMessage.LEAVE_FETCHED, response));
    }

    /** 캘린더용 승인 연차 (타인 사유 마스킹) */
    @GetMapping("/calendar")
    public ResponseEntity<CommonResponse<List<LeaveCalendarResponse>>> getCalendar(
            @AuthenticationPrincipal AuthUser authUser,
            @RequestParam int year,
            @RequestParam int month
    ) {
        List<LeaveCalendarResponse> response = leaveService.getCalendar(authUser.id(), year, month);
        return ResponseEntity.ok(CommonResponse.success(ResponseMessage.LEAVE_FETCHED, response));
    }

    /** 내가 승인자인 대기 목록 (취소 대기 포함) */
    @GetMapping("/pending")
    @PreAuthorize("hasAnyRole('TEAM_LEADER','SYSTEM_ADMIN')")
    public ResponseEntity<CommonResponse<Page<LeaveResponse>>> getPending(
            @AuthenticationPrincipal AuthUser authUser,
            Pageable pageable
    ) {
        Page<LeaveResponse> response = leaveService.getPending(authUser.id(), pageable);
        return ResponseEntity.ok(CommonResponse.success(ResponseMessage.LEAVE_FETCHED, response));
    }

    /** 내 팀 연차 현황 (기간 필터, 미지정 시 이번 달) */
    @GetMapping("/team")
    public ResponseEntity<CommonResponse<List<LeaveCalendarResponse>>> getTeam(
            @AuthenticationPrincipal AuthUser authUser,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to
    ) {
        List<LeaveCalendarResponse> response = leaveService.getTeam(authUser.id(), from, to);
        return ResponseEntity.ok(CommonResponse.success(ResponseMessage.LEAVE_FETCHED, response));
    }

    /** 전체 신청 목록 (페이징·status·keyword 필터) */
    @GetMapping
    @PreAuthorize("hasRole('SYSTEM_ADMIN')")
    public ResponseEntity<CommonResponse<Page<LeaveResponse>>> getAll(
            @RequestParam(required = false) RequestStatus status,
            @RequestParam(required = false) String keyword,
            Pageable pageable
    ) {
        Page<LeaveResponse> response = leaveService.getAllForAdmin(status, keyword, pageable);
        return ResponseEntity.ok(CommonResponse.success(ResponseMessage.LEAVE_FETCHED, response));
    }

    /** 승인/반려 — 조건부 갱신 (승인자만) */
    @PostMapping("/{id}/approval")
    @PreAuthorize("hasAnyRole('TEAM_LEADER','SYSTEM_ADMIN')")
    public ResponseEntity<CommonResponse<Void>> processApproval(
            @AuthenticationPrincipal AuthUser authUser,
            @PathVariable Long id,
            @Valid @RequestBody ApprovalRequest request
    ) {
        leaveService.processApproval(id, authUser.id(), request);
        String message = Boolean.TRUE.equals(request.approved())
                ? ResponseMessage.LEAVE_APPROVED : ResponseMessage.LEAVE_REJECTED;
        return ResponseEntity.ok(CommonResponse.success(message));
    }

    /** 취소 신청 — 미래=즉시 취소, 과거 포함=소급취소(승인 대기) (본인) */
    @PostMapping("/{id}/cancel")
    public ResponseEntity<CommonResponse<Void>> cancel(
            @AuthenticationPrincipal AuthUser authUser,
            @PathVariable Long id,
            @Valid @RequestBody CancelRequest request
    ) {
        RequestStatus result = leaveService.cancel(id, authUser.id(), request);
        String message = (result == RequestStatus.CANCEL_PENDING)
                ? ResponseMessage.LEAVE_CANCEL_REQUESTED : ResponseMessage.LEAVE_CANCELLED;
        return ResponseEntity.ok(CommonResponse.success(message));
    }

    /** 소급 취소 승인/반려 (승인자만) */
    @PostMapping("/{id}/cancel-approval")
    @PreAuthorize("hasAnyRole('TEAM_LEADER','SYSTEM_ADMIN')")
    public ResponseEntity<CommonResponse<Void>> processCancelApproval(
            @AuthenticationPrincipal AuthUser authUser,
            @PathVariable Long id,
            @Valid @RequestBody ApprovalRequest request
    ) {
        leaveService.processCancelApproval(id, authUser.id(), request);
        String message = Boolean.TRUE.equals(request.approved())
                ? ResponseMessage.LEAVE_CANCEL_APPROVED : ResponseMessage.LEAVE_CANCEL_REJECTED;
        return ResponseEntity.ok(CommonResponse.success(message));
    }

    /** 해당 건 처리 이력 (본인·승인자·SA) */
    @GetMapping("/{id}/histories")
    public ResponseEntity<CommonResponse<List<LeaveHistoryResponse>>> getHistories(
            @AuthenticationPrincipal AuthUser authUser,
            @PathVariable Long id
    ) {
        List<LeaveHistoryResponse> response = leaveService.getHistories(id, authUser.id());
        return ResponseEntity.ok(CommonResponse.success(ResponseMessage.LEAVE_FETCHED, response));
    }
}
