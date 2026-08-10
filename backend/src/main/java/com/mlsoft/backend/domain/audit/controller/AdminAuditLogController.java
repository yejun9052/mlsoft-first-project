package com.mlsoft.backend.domain.audit.controller;

import com.mlsoft.backend.domain.audit.dto.AdminActionOption;
import com.mlsoft.backend.domain.audit.dto.AdminAuditLogResponse;
import com.mlsoft.backend.domain.audit.entity.AdminAction;
import com.mlsoft.backend.domain.audit.service.AdminAuditService;
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
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * 관리자 조작 감사 로그 API (리뷰 S-3).
 *
 * <p>전 직원의 권한·연차 변경 내역을 노출하므로 <b>SYSTEM_ADMIN 전용</b>이다.
 * 쓰기 엔드포인트는 없다 — 기록은 조작이 일어나는 서비스 안에서만 만들어진다.
 */
@RestController
@RequestMapping("/api/admin/audit-logs")
@RequiredArgsConstructor
public class AdminAuditLogController {

    private final AdminAuditService adminAuditService;

    /** 감사 로그 목록 (페이징, 최신순) — action·대상 사원 선택 필터 */
    @GetMapping
    @PreAuthorize("hasRole('SYSTEM_ADMIN')")
    public ResponseEntity<CommonResponse<Page<AdminAuditLogResponse>>> getLogs(
            @RequestParam(required = false) AdminAction action,
            @RequestParam(required = false) Long targetUserId,
            @PageableDefault(sort = "createdAt", direction = Sort.Direction.DESC) Pageable pageable
    ) {
        Page<AdminAuditLogResponse> response = adminAuditService.getLogs(action, targetUserId, pageable);
        return ResponseEntity.ok(CommonResponse.success(ResponseMessage.ADMIN_AUDIT_LOG_FETCHED, response));
    }

    /** 필터용 액션 목록 — 라벨까지 내려주므로 프론트가 액션 이름을 하드코딩하지 않는다 */
    @GetMapping("/actions")
    @PreAuthorize("hasRole('SYSTEM_ADMIN')")
    public ResponseEntity<CommonResponse<List<AdminActionOption>>> getActions() {
        return ResponseEntity.ok(
                CommonResponse.success(ResponseMessage.ADMIN_AUDIT_LOG_FETCHED, adminAuditService.getActions()));
    }
}
