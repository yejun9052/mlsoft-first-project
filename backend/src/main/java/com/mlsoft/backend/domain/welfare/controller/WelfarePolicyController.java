package com.mlsoft.backend.domain.welfare.controller;

import com.mlsoft.backend.domain.welfare.dto.WelfarePolicyRequest;
import com.mlsoft.backend.domain.welfare.dto.WelfarePolicyResponse;
import com.mlsoft.backend.domain.welfare.service.WelfarePolicyService;
import com.mlsoft.backend.global.response.CommonResponse;
import com.mlsoft.backend.global.response.ResponseMessage;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * 복리후생 정책 API (docs/03 복리후생 섹션).
 * 조회는 전체 권한, 추가·수정·비활성화는 SYSTEM_ADMIN 전용.
 */
@RestController
@RequestMapping("/api/welfare-policies")
@RequiredArgsConstructor
public class WelfarePolicyController {

    private final WelfarePolicyService welfarePolicyService;

    /** 정책 목록 (페이징, keyword·category 필터) */
    @GetMapping
    public ResponseEntity<CommonResponse<Page<WelfarePolicyResponse>>> getPolicies(
            @RequestParam(required = false) String keyword,
            @RequestParam(required = false) String category,
            Pageable pageable
    ) {
        Page<WelfarePolicyResponse> response = welfarePolicyService.getPolicies(keyword, category, pageable);
        return ResponseEntity.ok(CommonResponse.success(ResponseMessage.WELFARE_POLICY_FETCHED, response));
    }

    /** 카테고리 목록 (필터·신청 폼용) */
    @GetMapping("/categories")
    public ResponseEntity<CommonResponse<List<String>>> getCategories() {
        List<String> response = welfarePolicyService.getCategories();
        return ResponseEntity.ok(CommonResponse.success(ResponseMessage.WELFARE_POLICY_FETCHED, response));
    }

    /** 활성 정책 전체 (신청 폼에서 구분/대상 옵션 구성용) */
    @GetMapping("/all")
    public ResponseEntity<CommonResponse<List<WelfarePolicyResponse>>> getAllActive() {
        List<WelfarePolicyResponse> response = welfarePolicyService.getAllActive();
        return ResponseEntity.ok(CommonResponse.success(ResponseMessage.WELFARE_POLICY_FETCHED, response));
    }

    /** 정책 추가 — 구분·대상 조합 중복 검증 */
    @PostMapping
    @PreAuthorize("hasRole('SYSTEM_ADMIN')")
    public ResponseEntity<CommonResponse<WelfarePolicyResponse>> create(
            @Valid @RequestBody WelfarePolicyRequest request
    ) {
        WelfarePolicyResponse response = welfarePolicyService.create(request);
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(CommonResponse.success(ResponseMessage.WELFARE_POLICY_CREATED, response));
    }

    /** 정책 수정 */
    @PatchMapping("/{id}")
    @PreAuthorize("hasRole('SYSTEM_ADMIN')")
    public ResponseEntity<CommonResponse<WelfarePolicyResponse>> update(
            @PathVariable Long id,
            @Valid @RequestBody WelfarePolicyRequest request
    ) {
        WelfarePolicyResponse response = welfarePolicyService.update(id, request);
        return ResponseEntity.ok(CommonResponse.success(ResponseMessage.WELFARE_POLICY_UPDATED, response));
    }

    /** 정책 비활성화 (소프트 삭제) */
    @DeleteMapping("/{id}")
    @PreAuthorize("hasRole('SYSTEM_ADMIN')")
    public ResponseEntity<CommonResponse<Void>> deactivate(@PathVariable Long id) {
        welfarePolicyService.deactivate(id);
        return ResponseEntity.ok(CommonResponse.success(ResponseMessage.WELFARE_POLICY_DEACTIVATED));
    }
}
