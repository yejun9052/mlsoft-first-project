package com.mlsoft.backend.domain.leave.controller;

import com.mlsoft.backend.domain.common.RequestAction;
import com.mlsoft.backend.domain.leave.dto.LeaveHistoryLogResponse;
import com.mlsoft.backend.domain.leave.service.LeaveHistoryService;
import com.mlsoft.backend.global.response.CommonResponse;
import com.mlsoft.backend.global.response.ResponseMessage;
import com.mlsoft.backend.security.AuthUser;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * 연차 처리 로그 API (docs/03 처리 이력).
 * 전사 로그는 SYSTEM_ADMIN, 팀 로그는 TEAM_LEADER — 팀 스코프는 서버가 요청자 부서로 결정한다.
 */
@RestController
@RequestMapping("/api/leave-histories")
@RequiredArgsConstructor
public class LeaveHistoryController {

    private final LeaveHistoryService leaveHistoryService;

    /** 전사 연차 처리 로그 (페이징, 최신순 기본 정렬, action 필터) */
    @GetMapping
    @PreAuthorize("hasRole('SYSTEM_ADMIN')")
    public ResponseEntity<CommonResponse<Page<LeaveHistoryLogResponse>>> getHistories(
            @RequestParam(required = false) RequestAction action,
            @PageableDefault(sort = "createdAt", direction = Sort.Direction.DESC) Pageable pageable
    ) {
        Page<LeaveHistoryLogResponse> response = leaveHistoryService.getHistories(action, pageable);
        return ResponseEntity.ok(CommonResponse.success(ResponseMessage.LEAVE_HISTORY_FETCHED, response));
    }

    /** 내 팀 연차 처리 로그 (페이징, 최신순 기본 정렬, action 필터) */
    @GetMapping("/my-team")
    @PreAuthorize("hasAnyRole('TEAM_LEADER','SYSTEM_ADMIN')")
    public ResponseEntity<CommonResponse<Page<LeaveHistoryLogResponse>>> getMyTeamHistories(
            @AuthenticationPrincipal AuthUser authUser,
            @RequestParam(required = false) RequestAction action,
            @PageableDefault(sort = "createdAt", direction = Sort.Direction.DESC) Pageable pageable
    ) {
        Page<LeaveHistoryLogResponse> response =
                leaveHistoryService.getMyTeamHistories(authUser.id(), action, pageable);
        return ResponseEntity.ok(CommonResponse.success(ResponseMessage.LEAVE_HISTORY_FETCHED, response));
    }
}
