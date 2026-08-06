package com.mlsoft.backend.domain.welfare.controller;

import com.mlsoft.backend.domain.common.RequestAction;
import com.mlsoft.backend.domain.welfare.dto.WelfareHistoryLogResponse;
import com.mlsoft.backend.domain.welfare.service.WelfareHistoryService;
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
 * 복리후생 처리 로그 API (docs/03 처리 이력).
 * 권한·스코프 규칙은 {@link com.mlsoft.backend.domain.leave.controller.LeaveHistoryController}와 동일.
 */
@RestController
@RequestMapping("/api/welfare-histories")
@RequiredArgsConstructor
public class WelfareHistoryController {

    private final WelfareHistoryService welfareHistoryService;

    /** 전사 복리후생 처리 로그 (페이징, 최신순 기본 정렬, action 필터) */
    @GetMapping
    @PreAuthorize("hasRole('SYSTEM_ADMIN')")
    public ResponseEntity<CommonResponse<Page<WelfareHistoryLogResponse>>> getHistories(
            @RequestParam(required = false) RequestAction action,
            @PageableDefault(sort = "createdAt", direction = Sort.Direction.DESC) Pageable pageable
    ) {
        Page<WelfareHistoryLogResponse> response = welfareHistoryService.getHistories(action, pageable);
        return ResponseEntity.ok(CommonResponse.success(ResponseMessage.WELFARE_HISTORY_FETCHED, response));
    }

    /** 내 팀 복리후생 처리 로그 (페이징, 최신순 기본 정렬, action 필터) */
    @GetMapping("/my-team")
    @PreAuthorize("hasAnyRole('TEAM_LEADER','SYSTEM_ADMIN')")
    public ResponseEntity<CommonResponse<Page<WelfareHistoryLogResponse>>> getMyTeamHistories(
            @AuthenticationPrincipal AuthUser authUser,
            @RequestParam(required = false) RequestAction action,
            @PageableDefault(sort = "createdAt", direction = Sort.Direction.DESC) Pageable pageable
    ) {
        Page<WelfareHistoryLogResponse> response =
                welfareHistoryService.getMyTeamHistories(authUser.id(), action, pageable);
        return ResponseEntity.ok(CommonResponse.success(ResponseMessage.WELFARE_HISTORY_FETCHED, response));
    }

    /**
     * 내가 처리한 복리후생 결재 로그 (페이징, 최신순, action 필터).
     * 결재 화면의 "승인·반려 완료" 탭이 연차 로그와 함께 병합해 쓴다 (LeaveHistoryController와 동일 규칙).
     */
    @GetMapping("/my-actions")
    public ResponseEntity<CommonResponse<Page<WelfareHistoryLogResponse>>> getMyActionHistories(
            @AuthenticationPrincipal AuthUser authUser,
            @RequestParam(required = false) RequestAction action,
            @PageableDefault(sort = "createdAt", direction = Sort.Direction.DESC) Pageable pageable
    ) {
        Page<WelfareHistoryLogResponse> response =
                welfareHistoryService.getMyActionHistories(authUser.id(), action, pageable);
        return ResponseEntity.ok(CommonResponse.success(ResponseMessage.WELFARE_HISTORY_FETCHED, response));
    }
}
