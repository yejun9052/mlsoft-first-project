package com.mlsoft.backend.domain.department.controller;

import com.mlsoft.backend.domain.department.dto.DepartmentCreateRequest;
import com.mlsoft.backend.domain.department.dto.DepartmentResponse;
import com.mlsoft.backend.domain.department.dto.DepartmentTreeResponse;
import com.mlsoft.backend.domain.department.dto.DepartmentUpdateRequest;
import com.mlsoft.backend.domain.department.service.DepartmentService;
import com.mlsoft.backend.global.response.CommonResponse;
import com.mlsoft.backend.global.response.ResponseMessage;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * 부서 API (docs/03 부서 섹션).
 * 조회는 전체 권한, 생성·수정·비활성화는 SYSTEM_ADMIN 전용.
 */
@RestController
@RequestMapping("/api/departments")
@RequiredArgsConstructor
public class DepartmentController {

    private final DepartmentService departmentService;

    /** 전체 목록 (플랫, 드롭다운용) */
    @GetMapping
    public ResponseEntity<CommonResponse<List<DepartmentResponse>>> getAll() {
        List<DepartmentResponse> response = departmentService.getAll();
        return ResponseEntity.ok(CommonResponse.success(ResponseMessage.DEPARTMENT_FETCHED, response));
    }

    /** 2단계 계층 트리 */
    @GetMapping("/tree")
    public ResponseEntity<CommonResponse<List<DepartmentTreeResponse>>> getTree() {
        List<DepartmentTreeResponse> response = departmentService.getTree();
        return ResponseEntity.ok(CommonResponse.success(ResponseMessage.DEPARTMENT_FETCHED, response));
    }

    /** 부서 생성 */
    @PostMapping
    @PreAuthorize("hasRole('SYSTEM_ADMIN')")
    public ResponseEntity<CommonResponse<DepartmentResponse>> create(
            @Valid @RequestBody DepartmentCreateRequest request
    ) {
        DepartmentResponse response = departmentService.create(request);
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(CommonResponse.success(ResponseMessage.DEPARTMENT_CREATED, response));
    }

    /** 부서 수정 (팀장 지정 포함) */
    @PutMapping("/{id}")
    @PreAuthorize("hasRole('SYSTEM_ADMIN')")
    public ResponseEntity<CommonResponse<DepartmentResponse>> update(
            @PathVariable Long id,
            @Valid @RequestBody DepartmentUpdateRequest request
    ) {
        DepartmentResponse response = departmentService.update(id, request);
        return ResponseEntity.ok(CommonResponse.success(ResponseMessage.DEPARTMENT_UPDATED, response));
    }

    /** 부서 비활성화 (소프트 삭제) */
    @DeleteMapping("/{id}")
    @PreAuthorize("hasRole('SYSTEM_ADMIN')")
    public ResponseEntity<CommonResponse<Void>> deactivate(@PathVariable Long id) {
        departmentService.deactivate(id);
        return ResponseEntity.ok(CommonResponse.success(ResponseMessage.DEPARTMENT_DEACTIVATED));
    }
}
