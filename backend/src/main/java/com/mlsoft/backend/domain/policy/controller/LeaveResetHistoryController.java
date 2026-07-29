package com.mlsoft.backend.domain.policy.controller;

import com.mlsoft.backend.domain.policy.dto.LeaveResetHistoryResponse;
import com.mlsoft.backend.domain.policy.service.LeaveResetHistoryService;
import com.mlsoft.backend.global.response.CommonResponse;
import com.mlsoft.backend.global.response.ResponseMessage;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 기산일 리셋·소멸 이력 관리자 API (docs/03 시스템 설정, docs/02 3-11(b)).
 * 전 직원의 리셋 이력을 노출하므로 SYSTEM_ADMIN 전용.
 */
@RestController
@RequestMapping("/api/admin/reset-histories")
@RequiredArgsConstructor
public class LeaveResetHistoryController {

    private final LeaveResetHistoryService leaveResetHistoryService;

    /** 리셋 이력 목록 (페이징, 리셋일 최신순 기본 정렬) */
    @GetMapping
    @PreAuthorize("hasRole('SYSTEM_ADMIN')")
    public ResponseEntity<CommonResponse<Page<LeaveResetHistoryResponse>>> getAll(
            @PageableDefault(sort = "resetDate", direction = Sort.Direction.DESC) Pageable pageable
    ) {
        Page<LeaveResetHistoryResponse> response = leaveResetHistoryService.getResetHistories(pageable);
        return ResponseEntity.ok(CommonResponse.success(ResponseMessage.LEAVE_RESET_HISTORY_FETCHED, response));
    }
}
